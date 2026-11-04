package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import org.joml.Vector3d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.physics.VsiFluidOutflow
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.core.internal.physics.VsiFluidParcelMomentum
import org.valkyrienskies.core.internal.physics.VsiFluidParcelPose
import org.valkyrienskies.core.internal.physics.VsiFluidParcelReceiver
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.common.*
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.networking.PacketFluidParcel
import org.valkyrienskies.mod.common.networking.PacketFluidParcelBatch
import java.math.BigDecimal

class WorldFluidParcelMergingTest {
    companion object {
        private const val DIM = "minecraft:overworld"
        private const val WATER = "minecraft:water"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>(relaxed = true)
    private val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
    private val blocks = mutableMapOf<BlockPos, BlockState>()
    private var sources = FractionalFluidSources()
    private var loaded = true
    private var ground = true
    private var oldCollection = 0
    private val levels get() = mapOf(DIM to level)

    @BeforeEach fun setup() {
        oldCollection = VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds
        VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds = 2
        mockkObject(MassDatapackResolver, BlockStateInfo)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { (server as IShipObjectWorldServerProvider).fractionalFluidSources } answers { sources }
        every { (server as IShipObjectWorldServerProvider).shipObjectWorld } returns null
        every { level.server } returns server
        every { level.dimensionId } returns DIM
        every { level.dimension() } returns net.minecraft.world.level.Level.OVERWORLD
        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.hasChunkAt(any()) } answers { loaded }
        every { level.players() } returns emptyList()
        every { level.getBlockState(any()) } answers {
            val pos = firstArg<BlockPos>()
            blocks[pos] ?: if (ground && pos.y <= 0) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState()
        }
        every { level.setBlock(any(), any(), any()) } answers {
            val pos = firstArg<BlockPos>().immutable()
            val state = secondArg<BlockState>()
            blocks[pos] = state
            sources.reconcileFluid(DIM, pos.x, pos.y, pos.z, FractionalFluidSources.fluidName(state))
            true
        }
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } just Runs
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.WATER
    }

    @AfterEach fun cleanup() {
        VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds = oldCollection
        unmockkAll()
    }

    private fun add(manager: WorldFluidOutflowManager, units: String = "10", x: Double = .5, y: Double = 100.5,
        z: Double = .5, vx: Double = 0.0, fluid: String = WATER, dimension: String = DIM) {
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueueExact(dimension, fluid,
            Vector3d(x, y, z), Vector3d(vx, 0.0, 0.0), BigDecimal(units)))
    }

    private fun entries(manager: WorldFluidOutflowManager): List<CompoundTag> =
        manager.save().getList("parcels", Tag.TAG_COMPOUND.toInt()).map { it as CompoundTag }

    private fun units(entry: CompoundTag): BigDecimal = if (entry.contains("volume_units"))
        BigDecimal(entry.getString("volume_units")) else BigDecimal(entry.getString("volume")) * BigDecimal(255)

    private fun total(manager: WorldFluidOutflowManager): BigDecimal = entries(manager).fold(BigDecimal.ZERO) { sum, entry -> sum + units(entry) }
    private fun exact(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test fun `nearby airborne parcels preserve exact ownership center and linear momentum`() {
        val manager = WorldFluidOutflowManager()
        val empty = manager.parcelStorageBytes()
        add(manager, "10", x = .4, vx = 1.0)
        val single = manager.parcelStorageBytes()
        add(manager, "20", x = .6, vx = 2.0, fluid = "minecraft:flowing_water")
        manager.tick(levels)
        assertEquals(1, manager.activeParcelCount())
        val survivor = entries(manager).single()
        exact("30", units(survivor))
        assertEquals((.45 * 10 + .7 * 20) / 30, survivor.getDouble("x"), 1e-12)
        assertEquals(5.0 / 3, survivor.getDouble("vx"), 1e-12)
        assertEquals(-.4905, survivor.getDouble("vy"), 1e-12)
        assertEquals(single, manager.parcelStorageBytes())
        val restored = WorldFluidOutflowManager().also { it.load(manager.save()) }
        assertEquals(manager.save(), restored.save())
        blocks[BlockPos.containing(survivor.getDouble("x"), survivor.getDouble("y"), survivor.getDouble("z"))] = Blocks.WATER.defaultBlockState()
        restored.tick(levels)
        assertEquals(empty, restored.parcelStorageBytes())
    }

    @Test fun `nearby fractional amounts add exactly without rounding or a separate remainder`() {
        val manager = WorldFluidOutflowManager()
        add(manager, "0.0000000000000000000000000000000000007")
        add(manager, "1.0000000000000000000000000000000000003")
        manager.tick(levels)
        exact("1.000000000000000000000000000000000001", total(manager))
        assertEquals(1, manager.activeParcelCount())
        assertEquals(manager.save(), WorldFluidOutflowManager().also { it.load(manager.save()) }.save())
    }

    @Test fun `different fluids dimensions separated and opposing streams stay distinct`() {
        for (scenario in 0..4) {
            val manager = WorldFluidOutflowManager()
            add(manager)
            when (scenario) {
                0 -> add(manager, fluid = "minecraft:lava")
                1 -> add(manager, dimension = "minecraft:the_nether")
                2 -> add(manager, x = 1.1)
                3 -> add(manager, vx = 4.0)
                4 -> add(manager, units = "60")
            }
            manager.tick(levels + ("minecraft:the_nether" to level))
            assertEquals(2, manager.activeParcelCount(), "scenario=$scenario")
            exact(if (scenario == 4) "70" else "20", total(manager))
        }
    }

    @Test fun `nearby streams cannot merge through a terrain corner`() {
        blocks[BlockPos(1, 100, 0)] = Blocks.STONE.defaultBlockState()
        val manager = WorldFluidOutflowManager()
        add(manager, x = .9, z = .9)
        add(manager, x = 1.1, z = 1.1)
        manager.tick(levels)
        assertEquals(2, manager.activeParcelCount())
        exact("20", total(manager))
    }

    @Test fun `nearby ship receivers conservatively prevent merging`() {
        val world = mockk<VsiServerShipWorld>(relaxed = true)
        every { (server as IShipObjectWorldServerProvider).shipObjectWorld } returns world
        every { world.supportsFluidParcelInterception } returns false
        mockkObject(WorldFluidParcelReceivers)
        every { WorldFluidParcelReceivers.collect(any(), any(), any(), any(), any(), any()) } returns listOf(mockk())
        val manager = WorldFluidOutflowManager()
        add(manager)
        add(manager)
        manager.tick(levels)
        assertEquals(2, manager.activeParcelCount())
        exact("20", total(manager))
    }

    @Test fun `angular momentum owners retain their original payload and do not coalesce`() {
        val manager = WorldFluidOutflowManager()
        val momentum = VsiFluidParcelMomentum(1000.0, .02, .01, -.03, .1, .1, .1, .0, .0, .0)
        repeat(2) {
            manager.enqueue(level, VsiFluidOutflow(1, 1, 1, DIM, 7, .5, 100.5, .5,
                0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 17, 10, momentum = momentum))
        }
        manager.tick(levels)
        assertEquals(2, manager.activeParcelCount())
        assertTrue(entries(manager).all { it.getCompound("momentum").getDouble("lx") == .02 })
    }

    @Test fun `pending ship interception owners cannot merge before their receipts arrive`() {
        val world = mockk<VsiServerShipWorld>(relaxed = true)
        every { (server as IShipObjectWorldServerProvider).shipObjectWorld } returns world
        every { world.supportsFluidParcelInterception } returns true
        every { world.submitFluidParcelInterception(any()) } returns true
        every { MassDatapackResolver.getLiquidStateId(any()) } returns 17
        mockkObject(WorldFluidParcelReceivers)
        val pose = VsiFluidParcelPose(0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        every { WorldFluidParcelReceivers.collect(any(), any(), any(), any(), any(), any()) } returns
            listOf(VsiFluidParcelReceiver(19, 1, pose, pose))
        val manager = WorldFluidOutflowManager()
        add(manager)
        add(manager)
        repeat(3) { manager.tick(levels) }
        exact("20", total(manager))
        assertEquals(2, manager.activeParcelCount())
        val pending = entries(manager).map { it.getCompound("interception").getUUID("id") }
        assertEquals(2, pending.toSet().size)
        verify(exactly = 2) { world.submitFluidParcelInterception(any()) }
        assertEquals(manager.save(), WorldFluidOutflowManager().also { it.load(manager.save()) }.save())
    }

    @Test fun `dense spray coalesces into bounded parcels without increasing water`() {
        val manager = WorldFluidOutflowManager(256, 256)
        repeat(256) { add(manager, "1", x = .4 + it % 3 * .03) }
        repeat(4) { manager.tick(levels) }
        exact("256", total(manager))
        assertTrue(manager.activeParcelCount() in 5..16)
        assertTrue(entries(manager).all { units(it) <= BigDecimal("63.75") })
    }

    @Test fun `landed droplets accumulate across ticks and reload into an exact finite puddle`() {
        var manager = WorldFluidOutflowManager()
        add(manager, "20", x = .1, y = 1.5, z = .1)
        repeat(4) { manager.tick(levels) }
        assertTrue(blocks.isEmpty())
        assertEquals(8, manager.save().getInt("version"))
        val checkpoint = manager.save()
        manager = WorldFluidOutflowManager().also { it.load(checkpoint) }
        assertEquals(checkpoint, manager.save())
        add(manager, "20", x = .9, y = 1.5, z = .9)
        manager.tick(levels)
        exact("40", total(manager))
        assertEquals(1, manager.activeParcelCount())
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        val pos = BlockPos(0, 1, 0)
        assertEquals(1, blocks.getValue(pos).fluidState.amount)
        exact("40", requireNotNull(sources.amount(DIM, 0, 1, 0, WATER)))
        sources = FractionalFluidSources().also { it.load(sources.save()) }
        exact("40", requireNotNull(sources.amount(DIM, 0, 1, 0, WATER)))
    }

    @Test fun `continuous one unit spray conserves water through flight collection and puddle reload`() {
        var manager = WorldFluidOutflowManager()
        repeat(100) { tick ->
            add(manager, "1", x = .45 + tick % 2 * .1, y = 4.5)
            manager.tick(levels)
            if (tick == 49) {
                manager = WorldFluidOutflowManager().also { it.load(manager.save()) }
                sources = FractionalFluidSources().also { it.load(sources.save()) }
            }
            val landed = sources.amount(DIM, 0, 1, 0, WATER) ?: BigDecimal.ZERO
            exact((tick + 1).toString(), total(manager) + landed)
        }
        assertTrue(requireNotNull(sources.amount(DIM, 0, 1, 0, WATER)) >= BigDecimal("31.875"))
        repeat(30) { manager.tick(levels) }
        assertEquals(0, manager.activeParcelCount())
        exact("100", requireNotNull(sources.amount(DIM, 0, 1, 0, WATER)))
    }

    @Test fun `collection expiry survives repeated reload and pauses in unloaded chunks`() {
        VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds = 1
        var manager = WorldFluidOutflowManager()
        val empty = manager.parcelStorageBytes()
        add(manager, "1", y = 1.5)
        repeat(10) { manager.tick(levels) }
        val age = entries(manager).single().getLong("collection_age")
        assertEquals(9L, age)
        loaded = false
        repeat(50) { manager.tick(levels) }
        assertEquals(age, entries(manager).single().getLong("collection_age"))
        loaded = true
        repeat(10) {
            manager = WorldFluidOutflowManager().also { it.load(manager.save()) }
            manager.tick(levels)
            assertEquals(1, manager.activeParcelCount())
        }
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        assertEquals(empty, manager.parcelStorageBytes())
        assertTrue(blocks.isEmpty())
    }

    @Test fun `landed accumulation works when the work budget services owners on different ticks`() {
        val manager = WorldFluidOutflowManager(8, 1)
        add(manager, "20", y = 1.5)
        manager.tick(levels)
        add(manager, "20", x = .6, y = 1.5)
        repeat(3) { manager.tick(levels) }
        assertEquals(0, manager.activeParcelCount())
        exact("40", requireNotNull(sources.amount(DIM, 0, 1, 0, WATER)))
        assertEquals(1, blocks.getValue(BlockPos(0, 1, 0)).fluidState.amount)
    }

    @Test fun `merging into a lagging collector preserves the original expiry tick`() {
        VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds = 1
        val manager = WorldFluidOutflowManager(8, 1)
        add(manager, "1", y = 1.5)
        repeat(18) { manager.tick(levels) }
        add(manager, "1", y = 1.5)
        repeat(2) { manager.tick(levels) }
        assertEquals(1, manager.activeParcelCount())
        exact("2", total(manager))
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `new arrivals do not reset the oldest collection lifetime`() {
        VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds = 1
        val manager = WorldFluidOutflowManager()
        add(manager, "1", y = 1.5)
        repeat(19) { manager.tick(levels) }
        add(manager, "1", y = 1.5)
        manager.tick(levels)
        exact("2", total(manager))
        assertEquals(19L, entries(manager).single().getLong("collection_age"))
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `support removal resumes flight and clears the collection age`() {
        val manager = WorldFluidOutflowManager()
        add(manager, "1", y = 1.5)
        manager.tick(levels)
        val height = entries(manager).single().getDouble("y")
        ground = false
        repeat(4) { manager.tick(levels) }
        val parcel = entries(manager).single()
        assertFalse(parcel.contains("collection_age"))
        assertTrue(parcel.getDouble("y") < height)
        exact("1", units(parcel))
    }

    @Test fun `zero collection lifetime discards undersized landings without rounding up`() {
        VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds = 0
        val manager = WorldFluidOutflowManager()
        add(manager, "31", y = 1.5)
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        assertTrue(blocks.isEmpty())
        assertEquals(0, sources.size)
    }

    @Test fun `pressure servicing expires collection at twenty hertz and frees admission`() {
        VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds = 1
        var now = 0L
        val manager = WorldFluidOutflowManager(1, 1) { now }
        add(manager, "1", y = 1.5)
        manager.servicePressure(levels)
        repeat(20) { now += 50_000_000; manager.servicePressure(levels) }
        assertEquals(1, manager.activeParcelCount())
        now += 50_000_000
        manager.servicePressure(levels)
        assertEquals(0, manager.activeParcelCount())
        add(manager, "1", y = 1.5)
    }

    @Test fun `invalid collection checkpoints leave previous owners and index intact`() {
        val manager = WorldFluidOutflowManager()
        add(manager)
        val before = manager.save()
        val invalid = before.copy()
        invalid.putInt("version", 8)
        invalid.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putLong("collection_age", Long.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { manager.load(invalid) }
        assertEquals(before, manager.save())
        add(manager)
        manager.tick(levels)
        exact("20", total(manager))
        assertEquals(1, manager.activeParcelCount())
    }

    private fun visuals(onSend: (PacketFluidParcel) -> Unit) {
        val core = mockk<VsiCore>()
        val network = mockk<org.valkyrienskies.core.impl.networking.simple.SimplePacketNetworking>()
        val player = mockk<net.minecraft.server.level.ServerPlayer>()
        val wrapper = mockk<org.valkyrienskies.mod.common.util.MinecraftPlayer>()
        every { vsCore } returns core
        every { core.simplePacketNetworking } returns network
        every { player.playerWrapper } returns wrapper
        every { player.getUUID() } returns java.util.UUID(0, 1)
        every { player.distanceToSqr(any<Double>(), any<Double>(), any<Double>()) } returns 0.0
        every { level.players() } returns listOf(player)
        every { network.sendToClient(any(), wrapper) } answers {
            when (val packet = firstArg<org.valkyrienskies.core.impl.networking.simple.SimplePacket>()) {
                is PacketFluidParcel -> onSend(packet)
                is PacketFluidParcelBatch -> packet.packets().forEach(onSend)
                else -> error("Unexpected packet: $packet")
            }
        }
    }

    @Test fun `survivor update and donor retirement remain retryable after either packet fails`() {
        for (failRetirement in listOf(false, true)) {
            val packets = mutableListOf<PacketFluidParcel>()
            var fail = false
            visuals { packet ->
                if (fail && (if (failRetirement) packet.state == null else (packet.state?.volume ?: 0.0) > .05))
                    throw IllegalStateException("delivery")
                packets.add(packet)
            }
            val manager = WorldFluidOutflowManager()
            add(manager, x = .1, vx = 1.0)
            add(manager, x = .8, vx = -1.0)
            manager.tick(levels)
            assertEquals(2, packets.size)
            val originalIds = packets.map { it.id }.toSet()
            fail = true
            assertThrows(IllegalStateException::class.java) { repeat(3) { manager.tick(levels) } }
            exact("20", total(manager))
            assertEquals(1, entries(manager).size)
            val checkpoint = manager.save()
            exact("20", total(WorldFluidOutflowManager().also { it.load(checkpoint) }))
            fail = false
            repeat(2) { manager.tick(levels) }
            assertEquals(1, manager.activeParcelCount())
            assertEquals(originalIds, packets.map { it.id }.toSet())
            val latest = packets.groupBy { it.id }.values.map { it.maxBy { packet -> packet.revision } }
            assertEquals(1, latest.count { it.state == null })
            assertEquals(20.0 / 255, latest.single { it.state != null }.state!!.volume, 1e-16)
        }
    }

    @Test fun `collecting owner refreshes its visual and retires only at expiry`() {
        VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds = 1
        val packets = mutableListOf<PacketFluidParcel>()
        visuals { packets.add(it) }
        val manager = WorldFluidOutflowManager()
        add(manager, "1", y = 1.5)
        repeat(20) { manager.tick(levels) }
        assertEquals(20, packets.size)
        assertTrue(packets.all { it.state != null && it.state!!.volume > 0.0 })
        manager.tick(levels)
        assertNull(packets.last().state)
        assertEquals(1, packets.map { it.id }.toSet().size)
        assertTrue(packets.zipWithNext().all { (a, b) -> b.revision > a.revision })
    }
}
