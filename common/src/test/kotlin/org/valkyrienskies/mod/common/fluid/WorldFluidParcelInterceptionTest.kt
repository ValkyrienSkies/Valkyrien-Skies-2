package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.nbt.Tag
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.internal.physics.*
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import java.util.UUID

class WorldFluidParcelInterceptionTest {
    companion object {
        private const val DIMENSION = "minecraft:overworld"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>()
    private val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
    private val world = mockk<VsiServerShipWorld>(relaxed = true)
    private val ship = mockk<LoadedServerShip>()
    private val ships = mutableListOf(ship)
    private val requests = LinkedHashMap<UUID, VsiFluidParcelInterception>()
    private val receipts = HashMap<UUID, VsiFluidParcelInterceptionResult>()
    private val levels get() = mapOf(DIMENSION to level)

    @BeforeEach fun setup() {
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.WATER
        every { MassDatapackResolver.getLiquidStateId(any()) } returns 17
        every { level.server } returns server
        every { (server as IShipObjectWorldServerProvider).shipObjectWorld } returns world
        every { (server as IShipObjectWorldServerProvider).fractionalFluidSources } returns null
        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.hasChunkAt(any()) } returns true
        every { level.getBlockState(any()) } returns Blocks.AIR.defaultBlockState()
        every { level.players() } returns emptyList()
        every { world.supportsFluidParcelInterception } returns true
        every { world.loadedShips.iterator() } answers { ships.iterator() }
        every { ship.id } returns 19
        every { ship.chunkClaimDimension } returns DIMENSION
        every { ship.worldAABB } returns AABBd(-3.0, 90.0, -3.0, 3.0, 105.0, 3.0)
        val transform = mockk<ShipTransform>()
        every { transform.positionInWorld } returns Vector3d(0.0, 100.0, 0.0)
        every { transform.positionInShip } returns Vector3d(.5, .5, .5)
        every { transform.shipToWorldRotation } returns Quaterniond()
        every { transform.shipToWorldScaling } returns Vector3d(1.0)
        every { ship.transform } returns transform
        every { ship.prevTickTransform } returns transform
        every { world.getFluidTopologySnapshot(19) } returns
            VsiFluidTopologySnapshot(19, DIMENSION, 1, 1, 4, true, true, 0, emptyList())
        every { world.submitFluidParcelInterception(any()) } answers {
            val request = firstArg<VsiFluidParcelInterception>()
            requests[request.id] = request
            true
        }
        every { world.consumeFluidParcelInterception(any(), any()) } answers {
            val id = firstArg<UUID>()
            val receipt = receipts[id]
            if (receipt == null) false else {
                secondArg<(VsiFluidParcelInterceptionResult) -> Unit>().invoke(receipt)
                receipts.remove(id)
                true
            }
        }
    }

    @AfterEach fun cleanup() { ShipFluidCandidates.clear(world); unmockkAll() }

    private fun manager(velocity: Double = -5.0, nanoTime: () -> Long = System::nanoTime): WorldFluidOutflowManager =
        WorldFluidOutflowManager(8, 8, nanoTime).also {
        assertEquals(VsiFluidOutflowResult.APPLIED, it.enqueue(level, VsiFluidOutflow(1, 1, 1, DIMENSION, 7,
            .5, 100.5, .5, 0.0, -1.0, 0.0, 0.0, velocity, 0.0, 17, 510)))
    }

    private fun entry(manager: WorldFluidOutflowManager) = manager.save().getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)

    @Test fun `partial catch survives reload and debits the world parcel exactly once`() {
        var manager = manager()
        manager.tick(levels)
        assertEquals(1, requests.size)
        val request = requests.values.single()
        assertEquals(510.0, request.fillAmount)
        assertEquals(2.0, entry(manager).getString("volume").toDouble())
        manager = WorldFluidOutflowManager(8, 8).also { it.load(manager.save()) }
        receipts[request.id] = VsiFluidParcelInterceptionResult(255.0, .5, 19)
        ships.clear()
        manager.tick(levels)
        assertEquals(1.0, entry(manager).getString("volume").toDouble())
        assertFalse(entry(manager).contains("interception"))
        manager.tick(levels)
        assertEquals(1.0, entry(manager).getString("volume").toDouble())
        assertEquals(1, requests.size)
    }

    @Test fun `a completed debit remains applied when later world access fails and reload retries motion`() {
        var manager = manager()
        manager.tick(levels)
        val id = requests.keys.single()
        receipts[id] = VsiFluidParcelInterceptionResult(127.5, .25, 19)
        every { level.hasChunkAt(any()) } throws IllegalStateException("World temporarily unavailable")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val saved = manager.save()
        assertTrue(entry(manager).getCompound("interception").getBoolean("applied"))
        assertEquals(1.5, entry(manager).getString("volume").toDouble())
        every { level.hasChunkAt(any()) } returns true
        ships.clear()
        manager = WorldFluidOutflowManager(8, 8).also { it.load(saved) }
        manager.tick(levels)
        assertEquals(1.5, entry(manager).getString("volume").toDouble())
        assertEquals(1, requests.size)
    }

    @Test fun `terrain before a receiver limits the native sweep and complete catches remove the parcel`() {
        val manager = manager(-20.0)
        every { level.getBlockState(any()) } answers {
            if (firstArg<BlockPos>().y < 100) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState()
        }
        manager.tick(levels)
        assertEquals(1, requests.size)
        val request = requests.values.single()
        assertTrue(request.maximumFraction < 1.0)
        receipts[request.id] = VsiFluidParcelInterceptionResult(510.0, .25, 19)
        manager.tick(levels)
        assertTrue(manager.save().getList("parcels", Tag.TAG_COMPOUND.toInt()).isEmpty())
    }

    @Test fun `pressure motion does not replay the ships previous game tick movement`() {
        val current = ship.transform
        val previous = mockk<ShipTransform>()
        every { previous.positionInWorld } returns Vector3d(-20.0, 100.0, 0.0)
        every { previous.positionInShip } returns current.positionInShip
        every { previous.shipToWorldScaling } returns current.shipToWorldScaling
        every { previous.shipToWorldRotation } returns current.shipToWorldRotation
        every { ship.prevTickTransform } returns previous
        var now = 0L
        val manager = manager(nanoTime = { now })
        manager.tick(levels)
        val first = requests.values.single()
        assertEquals(-20.0, first.receivers.single().before.positionX)
        assertEquals(0.0, first.receivers.single().after.positionX)
        receipts[first.id] = VsiFluidParcelInterceptionResult(0.0, 1.0, null)
        manager.servicePressure(levels)
        now += 50_000_000L
        manager.servicePressure(levels)
        val next = requests.values.last()
        assertNotEquals(first.id, next.id)
        assertEquals(next.receivers.single().after, next.receivers.single().before)
        assertEquals(2.0, entry(manager).getString("volume").toDouble())
    }

    @Test fun `compound domains outside the main hull and truncated publications remain candidates`() {
        fun candidates() = WorldFluidParcelReceivers.collect(world, DIMENSION,
            Vector3d(30.5, 100.5, .5), Vector3d(30.5, 100.0, .5), true) { }
        assertTrue(candidates()!!.isEmpty())
        val topology = world.getFluidTopologySnapshot(19)!!
        every { world.getFluidTopologySnapshot(19) } returns topology.copy(streamSequence = 2,
            totalDomainVoxelCount = 1, voxels = listOf(VsiFluidTopologyVoxel(30, 0, 0, 2)))
        assertEquals(19L, candidates()!!.single().bodyId)
        every { world.getFluidTopologySnapshot(19) } returns topology.copy(streamSequence = 3,
            totalDomainVoxelCount = 1, voxels = emptyList())
        assertEquals(19L, candidates()!!.single().bodyId)
        ships.clear()
        assertTrue(candidates()!!.isEmpty())
    }
}
