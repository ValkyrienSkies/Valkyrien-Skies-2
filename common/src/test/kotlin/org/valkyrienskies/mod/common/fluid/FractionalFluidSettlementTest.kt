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
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.internal.physics.VsiFluidOutflow
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.dimensionId
import java.math.BigDecimal

class FractionalFluidSettlementTest {
    companion object {
        private const val DIMENSION = "minecraft:overworld"
        private const val WATER = "minecraft:water"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>()
    private val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
    private val pos = BlockPos(0, 1, 0)
    private val blocks = mutableMapOf<BlockPos, BlockState>()
    private var sources = FractionalFluidSources()
    private var manager = WorldFluidOutflowManager(8, 8)
    private val levels get() = mapOf(DIMENSION to level)
    private var writes = 0

    @BeforeEach fun setup() {
        mockkObject(MassDatapackResolver, BlockStateInfo)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.WATER
        every { level.server } returns server
        every { level.dimensionId } returns DIMENSION
        every { (server as IShipObjectWorldServerProvider).fractionalFluidSources } answers { sources }
        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.hasChunkAt(any()) } returns true
        every { level.players() } returns emptyList()
        every { level.getBlockState(any()) } answers {
            blocks[firstArg<BlockPos>()] ?: if (firstArg<BlockPos>().y <= 0) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState()
        }
        ordinaryWrites()
        every { level.scheduleTick(any<BlockPos>(), any<net.minecraft.world.level.material.Fluid>(), any<Int>()) } just Runs
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } just Runs
    }

    @AfterEach fun cleanup() { unmockkAll() }

    private fun ordinaryWrites() {
        every { level.setBlock(any(), any(), any()) } answers {
            writes++
            blocks[firstArg<BlockPos>().immutable()] = secondArg()
            true
        }
    }

    private fun enqueue(volume: Double, x: Double = 0.5, y: Double = 1.5, vx: Double = 0.0) {
        val event = VsiFluidOutflow(1, 1, 1, DIMENSION, 7, x, y, 0.5,
            0.0, -1.0, 0.0, vx, 0.0, 0.0, 17, 255).copy(worldVolume = volume)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, event))
    }

    private fun amount(position: BlockPos = pos) = sources.amount(DIMENSION, position.x, position.y, position.z, WATER)
    private fun assertAmount(expected: String, position: BlockPos = pos) =
        assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(amount(position))))
    private fun entries() = manager.save().getList("parcels", Tag.TAG_COMPOUND.toInt())
    private fun units(entry: CompoundTag, key: String = "volume"): BigDecimal =
        if (entry.contains(key + "_units")) entry.getString(key + "_units").toBigDecimal()
        else entry.getString(key).toBigDecimal() * BigDecimal(255)
    private fun pending() = entries().fold(BigDecimal.ZERO) { total, entry -> total + units(entry as CompoundTag) }
    private fun reload(parcelSave: CompoundTag = manager.save(), sourceSave: CompoundTag = sources.save()) {
        sources = FractionalFluidSources().also { it.load(sourceSave) }
        manager = WorldFluidOutflowManager(8, 8).also { it.load(parcelSave) }
    }

    @Test fun `same visible flowing level accumulates exact amounts across reload`() {
        enqueue(0.01)
        manager.tick(levels)
        val state = blocks[pos]
        assertAmount("2.55")
        assertEquals(1, writes)
        reload()
        enqueue(0.01)
        manager.tick(levels)
        assertEquals(state, blocks[pos])
        assertEquals(1, writes)
        assertAmount("5.10")
        assertEquals(0, manager.activeParcelCount())
        verify { BlockStateInfo.onSourceAmountChanged(level, pos, requireNotNull(state), 5) }
    }

    @Test fun `refilling a partial source keeps the exact nondecimal world-volume remainder`() {
        blocks[pos] = Blocks.WATER.defaultBlockState()
        sources.setAmount(DIMENSION, 0, 1, 0, WATER, BigDecimal(29))
        enqueue(1.0)
        manager.tick(levels)
        assertAmount("255")
        assertEquals(0, writes)
        assertEquals(0, pending().compareTo(BigDecimal(29)))
        assertTrue(entries().getCompound(0).contains("volume_units"))
        verify { BlockStateInfo.onSourceAmountChanged(level, pos, Blocks.WATER.defaultBlockState(), 255) }
        verify(exactly = 1) { level.scheduleTick(pos, Fluids.WATER, any<Int>()) }
        reload()
        repeat(8) { manager.tick(levels) }
        assertEquals(0, manager.activeParcelCount())
        val stored = sources.save().getList("sources", Tag.TAG_COMPOUND.toInt())
        assertEquals(2, stored.size)
        assertEquals(listOf(BigDecimal(29), BigDecimal(255)), stored.map {
            (it as CompoundTag).getString("units").toBigDecimal()
        }.sorted())
    }

    @Test fun `failed and ambiguous world writes retain one owner through saved pending journals`() {
        for (applied in listOf(false, true)) {
            blocks.clear(); sources = FractionalFluidSources(); manager = WorldFluidOutflowManager(8, 8); writes = 0
            enqueue(0.1)
            var checkpoint: Pair<CompoundTag, CompoundTag>? = null
            every { level.setBlock(any(), any(), any()) } answers {
                if (applied) { blocks[firstArg<BlockPos>().immutable()] = secondArg(); writes++ }
                checkpoint = manager.save() to sources.save()
                throw IllegalStateException("interrupted write")
            }
            assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
            assertAmount("0")
            assertEquals(0, pending().compareTo(BigDecimal("25.5")))
            val saved = requireNotNull(checkpoint)
            reload(saved.first, saved.second)
            ordinaryWrites()
            manager.tick(levels)
            assertEquals(1, writes)
            assertAmount("25.5")
            assertEquals(0, manager.activeParcelCount())
            reload()
            manager.tick(levels)
            assertAmount("25.5")
        }
    }

    @Test fun `publication interruption after a same-state credit cannot replay the deposit`() {
        blocks[pos] = Blocks.WATER.defaultBlockState()
        sources.setAmount(DIMENSION, 0, 1, 0, WATER, BigDecimal("127.25"))
        enqueue(0.1)
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } throws IllegalStateException("publication")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        assertAmount("152.75")
        assertEquals(0, pending().signum())
        assertTrue(entries().getCompound(0).getCompound("placement").getBoolean("scheduled_only"))
        reload()
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } just Runs
        manager.tick(levels)
        assertAmount("152.75")
        assertEquals(0, manager.activeParcelCount())
        assertEquals(0, writes)
    }

    @Test fun `capacity refusal occurs before mutation and can resume with storage available`() {
        sources = FractionalFluidSources(0)
        enqueue(0.1)
        assertThrows(FractionalFluidSources.StorageCapacityException::class.java) { manager.tick(levels) }
        assertEquals(0, writes)
        assertEquals(0, sources.size)
        assertEquals(0, pending().compareTo(BigDecimal("25.5")))
        reload()
        manager.tick(levels)
        assertAmount("25.5")
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `confirmed refusal restores carrier storage and a later accepted write succeeds`() {
        enqueue(0.1)
        every { level.setBlock(any(), any(), any()) } returns false
        manager.tick(levels)
        assertEquals(0, sources.size)
        assertEquals(0L, sources.retainedBytes)
        assertEquals(0, pending().compareTo(BigDecimal("25.5")))
        ordinaryWrites()
        manager.tick(levels)
        assertAmount("25.5")
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `waterlogged partial source refills without replacing its solid`() {
        val state = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        blocks[pos] = state
        sources.setAmount(DIMENSION, 0, 1, 0, WATER, BigDecimal("127.25"))
        enqueue(0.1, y = 2.1)
        manager.tick(levels)
        assertEquals(state, blocks[pos])
        assertAmount("152.75")
        assertEquals(0, writes)
        assertEquals(0, manager.activeParcelCount())
        reload()
        assertAmount("152.75")
    }

    @Test fun `moving parcel credits a partial source at its first crossed cell`() {
        blocks[BlockPos(1, 1, 0)] = Blocks.WATER.defaultBlockState()
        sources.setAmount(DIMENSION, 1, 1, 0, WATER, BigDecimal(29))
        enqueue(0.1, vx = 100.0)
        manager.tick(levels)
        assertAmount("54.5", BlockPos(1, 1, 0))
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `malformed quantity intent cannot replace current parcel ownership`() {
        enqueue(0.1)
        every { level.setBlock(any(), any(), any()) } throws IllegalStateException("hold journal")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val valid = manager.save()
        for ((key, value) in listOf("units_before" to "1", "units_after" to "256", "units_after" to "-1")) {
            val invalid = valid.copy()
            invalid.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).getCompound("placement").putString(key, value)
            assertThrows(IllegalArgumentException::class.java) { manager.load(invalid) }
            assertEquals(valid, manager.save())
        }
    }

    @Test fun `full refill waits for carrier capacity without releasing finite ownership`() {
        sources.setAmount(DIMENSION, 0, 1, 0, WATER, BigDecimal.ONE)
        val bytes = sources.retainedBytes
        val saved = sources.save()
        sources = FractionalFluidSources(bytes).also { it.load(saved) }
        blocks[pos] = Blocks.WATER.defaultBlockState()
        enqueue(1.0)
        assertThrows(FractionalFluidSources.StorageCapacityException::class.java) { manager.tick(levels) }
        assertAmount("1")
        assertEquals(0, pending().compareTo(BigDecimal(255)))
        sources = FractionalFluidSources(bytes + 8).also { it.load(sources.save()) }
        manager.tick(levels)
        assertEquals(bytes + 8, sources.retainedBytes)
        assertEquals(1, sources.size)
        assertAmount("255")
        assertEquals(0, pending().compareTo(BigDecimal.ONE))
        assertEquals(0, writes)
    }

    @Test fun `a conflicting edit during placement cannot consume the parcel credit`() {
        enqueue(0.1)
        every { level.setBlock(any(), any(), any()) } answers {
            blocks[pos] = Blocks.STONE.defaultBlockState()
            sources.reconcileFluid(DIMENSION, 0, 1, 0, null)
            true
        }
        manager.tick(levels)
        assertEquals(0, pending().compareTo(BigDecimal("25.5")))
        assertEquals(0, sources.size)
        reload()
        manager.tick(levels)
        assertEquals(0, pending().compareTo(BigDecimal("25.5")))
        blocks.remove(pos)
        ordinaryWrites()
        manager.tick(levels)
        assertAmount("25.5")
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `a successful write with a conflicting same-fluid callback retains its pending credit`() {
        enqueue(1.0)
        every { level.setBlock(any(), any(), any()) } answers {
            blocks[pos] = Fluids.WATER.getFlowing(2, false).createLegacyBlock()
            true
        }
        manager.tick(levels)
        assertAmount("0")
        assertEquals(0, pending().compareTo(BigDecimal(255)))
        reload()
        manager.tick(levels)
        assertEquals(0, pending().compareTo(BigDecimal(255)))
        blocks.remove(pos)
        ordinaryWrites()
        manager.tick(levels)
        assertAmount("255")
        assertTrue(requireNotNull(blocks[pos]).fluidState.isSource)
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `quantity journal storage is admitted before any carrier or world change`() {
        enqueue(0.1)
        val retained = manager.parcelStorageBytes()
        manager.maxParcelStorageBytes = retained
        assertThrows(WorldFluidOutflowManager.StorageCapacityException::class.java) { manager.tick(levels) }
        assertEquals(retained, manager.parcelStorageBytes())
        assertEquals(0, sources.size)
        assertEquals(0, writes)
        assertEquals(0, pending().compareTo(BigDecimal("25.5")))
        manager.maxParcelStorageBytes = retained + 32768
        every { level.setBlock(any(), any(), any()) } throws IllegalStateException("hold admitted journal")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val charged = manager.parcelStorageBytes()
        assertTrue(charged > retained)
        reload()
        assertEquals(charged, manager.parcelStorageBytes())
        ordinaryWrites()
        manager.tick(levels)
        assertAmount("25.5")
        assertEquals(WorldFluidOutflowManager().parcelStorageBytes(), manager.parcelStorageBytes())
    }
}
