package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.pathfinding.NearbyShip
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame

class ShipFluidStateQueriesTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private val level = mockk<Level>()
    private val world = mockk<VsiClientShipWorld>()
    private val ship = mockk<LoadedShip>()
    private val local = BlockPos(1000, 3, -2000)
    private val toWorld = Matrix4d().translation(-1000.0, -3.0, 2000.0)
    private val bounds = AABBd(-10.0, -10.0, -10.0, 10.0, 10.0, 10.0)
    private var flood = floodingSnapshot(VsiFluidFloodedVoxel(local.x, local.y, local.z, 17, 128, 0))
    private var topology = topologySnapshot().copy(voxels = listOf(VsiFluidTopologyVoxel(local.x, local.y, local.z, 1)))

    @BeforeEach fun setup() {
        every { ship.id } returns 7
        every { ship.chunkClaim.contains(any<Int>(), any<Int>()) } returns true
        every { world.getFluidGameplaySnapshot(7) } answers { gameplaySnapshot(topology, flood) }
        every { level.getBlockState(any()) } returns Blocks.AIR.defaultBlockState()
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17, true) } returns Fluids.WATER
        every { MassDatapackResolver.getFlowingFluid(18, true) } returns Fluids.LAVA
    }
    @AfterEach fun cleanup() { ShipFluidInteraction.clear(world); unmockkAll() }
    private fun nearby() = NearbyShip(ship, bounds, toWorld.invert(Matrix4d()))
    private fun query(frame: PathfindingFrame? = null, ships: Array<NearbyShip> = arrayOf(nearby())) =
        ShipFluidStateQueries.Query(level, world, frame, ships)
    private fun amount(fill: Int) {
        flood = flood.copy(streamSequence = flood.streamSequence + 1,
            totalFloodedVoxelCount = if (fill > 0) 1 else 0,
            voxels = if (fill > 0) listOf(VsiFluidFloodedVoxel(local.x, local.y, local.z, 17, fill, 0)) else emptyList())
    }

    @Test fun `every positive fill is visible to local cell queries without inventing full sources`() {
        val frame = PathfindingFrame.InShip(ship, 7, toWorld)
        for (fill in 1..255) {
            amount(fill)
            val state = query(frame).resolve(local)!!.fluid()
            assertTrue(state.type.isSame(Fluids.WATER), "fill=$fill")
            assertEquals(fill == 255, state.isSource, "fill=$fill")
            assertEquals((fill * 8 + 254) / 255, state.amount)
        }
    }

    @Test fun `world and ship frame reads agree in translated and rotated cells`() {
        for (angle in listOf(0.0, 0.47, -1.2)) {
            toWorld.identity().rotateZ(angle).translate(-1000.0, -3.0, 2000.0)
            val point = toWorld.transformPosition(local.x + .5, local.y + .5, local.z + .5, Vector3d())
            val q = query(PathfindingFrame.InShip(ship, 7, toWorld))
            assertEquals(q.resolve(local)!!.fluid(), q.world(point.x, point.y, point.z)!!.fluid())
        }
    }

    @Test fun `dry domain overrides ambient ocean and produces a consistent air block`() {
        amount(0)
        every { level.getBlockState(BlockPos.ZERO) } returns Blocks.WATER.defaultBlockState()
        val result = query().resolve(BlockPos.ZERO)!!
        assertTrue(result.fluid().isEmpty)
        assertTrue(result.blockState(Blocks.WATER.defaultBlockState()).isAir)
        assertTrue(result.blockState(Blocks.LAVA.defaultBlockState()).isAir)
    }

    @Test fun `retained water is a nonair path block even when real terrain is air`() {
        val result = query().resolve(BlockPos.ZERO)!!
        val block = result.blockState(Blocks.AIR.defaultBlockState())
        assertFalse(block.isAir)
        assertEquals(result.fluid(), block.fluidState)
        assertEquals(Blocks.FIRE.defaultBlockState(), result.blockState(Blocks.FIRE.defaultBlockState()))
        assertEquals(Blocks.STONE.defaultBlockState(), result.blockState(Blocks.STONE.defaultBlockState()))
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
    }

    @Test fun `solid hull and newly solid terrain override stale retained cells`() {
        every { level.getBlockState(local) } returns Blocks.STONE.defaultBlockState()
        val result = query().resolve(BlockPos.ZERO)!!
        assertTrue(result.fluid().isEmpty)
        assertEquals(Blocks.STONE.defaultBlockState(), result.blockState(Blocks.AIR.defaultBlockState()))
        assertTrue(query(PathfindingFrame.InShip(ship, 7, toWorld)).resolve(local)!!.fluid().isEmpty)
    }

    @Test fun `projected ship hazards survive both wet and dry virtual fluid views`() {
        for (fill in listOf(128, 0)) {
            amount(fill)
            for (hazard in listOf(Blocks.SWEET_BERRY_BUSH, Blocks.FIRE, Blocks.LILY_PAD)) {
                val state = hazard.defaultBlockState()
                every { level.getBlockState(local) } returns state
                val result = query().resolve(BlockPos.ZERO)!!
                assertEquals(fill == 0, result.fluid().isEmpty)
                assertEquals(state, result.blockState(Blocks.AIR.defaultBlockState()))
            }
        }
    }

    @Test fun `waterlogged solid and ordinary world solid keep their real block state`() {
        every { level.getBlockState(BlockPos.ZERO) } returns Blocks.STONE.defaultBlockState()
        assertNull(query().resolve(BlockPos.ZERO))
        val logged = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        assertEquals(logged, ShipFluidStateQueries.Cell(Fluids.EMPTY.defaultFluidState(), null).blockState(logged))
    }

    @Test fun `missing disabled and unknown snapshots fall back to real fluid`() {
        every { world.getFluidGameplaySnapshot(7) } returns null
        assertNull(query().resolve(BlockPos.ZERO))
        every { level.getBlockState(local) } returns Blocks.WATER.defaultBlockState()
        assertTrue(query().resolve(BlockPos.ZERO)!!.fluid().type.isSame(Fluids.WATER))
        every { world.getFluidGameplaySnapshot(7) } answers { gameplaySnapshot(topology, flood) }
        every { MassDatapackResolver.getFlowingFluid(17, true) } returns null
        assertTrue(query().resolve(BlockPos.ZERO)!!.fluid().type.isSame(Fluids.WATER))
        every { level.getBlockState(local) } returns Blocks.AIR.defaultBlockState()
        assertNull(query().resolve(BlockPos.ZERO))
        topology = topology.copy(enabled = false, valid = false, totalDomainVoxelCount = 0, voxels = emptyList())
        amount(0)
        assertNull(query().resolve(BlockPos.ZERO))
    }

    @Test fun `one path pins complete pairs and transforms while the next path sees updates`() {
        val transform = Matrix4d(toWorld)
        val q = query(PathfindingFrame.InShip(ship, 7, transform))
        val original = q.resolve(local)!!.fluid()
        amount(0)
        transform.translate(30.0, 0.0, 0.0)
        toWorld.translate(40.0, 0.0, 0.0)
        bounds.translate(50.0, 0.0, 0.0)
        assertEquals(original, q.resolve(local)!!.fluid())
        assertEquals(original, q.resolve(BlockPos(local.x, local.y, local.z))!!.fluid())
        assertEquals(original, q.world(.5, .5, .5)!!.fluid())
        assertTrue(query(PathfindingFrame.InShip(ship, 7, toWorld)).resolve(local)!!.fluid().isEmpty)
        verify(exactly = 2) { world.getFluidGameplaySnapshot(7) }
        verify(exactly = 0) { world.getFluidTopologySnapshot(any()) }
        verify(exactly = 0) { world.getFluidFloodingSnapshot(any()) }
    }

    @Test fun `outside a controlled domain keeps real ship water and unmasked world water`() {
        val other = BlockPos(1, 0, 0)
        assertNull(query().resolve(other))
        every { level.getBlockState(other) } returns Blocks.WATER.defaultBlockState()
        assertNull(query().resolve(other), "Ordinary ambient fluid remains the caller's fallback")
        every { level.getBlockState(other) } returns Blocks.AIR.defaultBlockState()
        every { level.getBlockState(local.offset(1, 0, 0)) } returns Blocks.WATER.defaultBlockState()
        assertTrue(query().resolve(other)!!.fluid().type.isSame(Fluids.WATER))
    }

    @Test fun `overlapping dry and wet ships prefer water independently of iteration order`() {
        val other = mockk<LoadedShip>()
        every { other.id } returns 8
        every { world.getFluidGameplaySnapshot(8) } answers {
            gameplaySnapshot(topology.copy(bodyId = 8), flood.copy(bodyId = 8))
        }
        every { world.getFluidGameplaySnapshot(7) } answers { gameplaySnapshot(topology, floodingSnapshot()) }
        val wet = NearbyShip(other, AABBd(bounds), toWorld.invert(Matrix4d()))
        for (ships in listOf(arrayOf(nearby(), wet), arrayOf(wet, nearby()))) {
            assertTrue(query(ships = ships).resolve(BlockPos.ZERO)!!.fluid().type.isSame(Fluids.WATER))
        }
    }

    @Test fun `in-ship off-domain projection uses the frozen path transform`() {
        val frame = PathfindingFrame.InShip(ship, 7, Matrix4d(toWorld))
        val other = mockk<LoadedShip>()
        every { other.id } returns 8
        every { world.getFluidGameplaySnapshot(8) } answers {
            gameplaySnapshot(topology.copy(bodyId = 8), flood.copy(bodyId = 8))
        }
        val neighbor = NearbyShip(other, bounds, Matrix4d().translation(998.0, 3.0, -2000.0))
        val q = query(frame, arrayOf(nearby(), neighbor))
        every { ship.chunkClaim.contains(any<Int>(), any<Int>()) } returns false
        amount(0)
        (frame.shipToWorldSnapshot as Matrix4d).translate(50.0, 0.0, 0.0)
        assertTrue(q.resolve(local.offset(2, 0, 0))!!.fluid().type.isSame(Fluids.WATER))
        assertNull(q.resolve(local.offset(4, 0, 0)))
    }
}
