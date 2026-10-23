package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import java.time.Duration
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import org.joml.Matrix4d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.common.config.MassDatapackResolver

class ShipFluidVolumeTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private val world = mockk<VsiClientShipWorld>()
    private val ship = mockk<Ship>()
    private var transform = Matrix4d()
    private var topology = topologySnapshot()
    private var flooding = floodingSnapshot(VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0))
    private val water = mockk<FlowingFluid>()
    private val waterState = mockk<FluidState>()

    @BeforeEach fun setup() {
        every { ship.id } returns 7
        every { ship.shipToWorld } answers { transform }
        every { ship.worldToShip } answers { transform.invert(Matrix4d()) }
        every { world.getFluidGameplaySnapshot(7) } answers { gameplaySnapshot(topology, flooding) }
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17) } returns water
        every { water.defaultFluidState() } returns waterState
        every { waterState.`is`(FluidTags.WATER) } returns true
        every { waterState.`is`(FluidTags.LAVA) } returns false
    }
    @AfterEach fun cleanup() { ShipFluidInteraction.clear(world); unmockkAll() }
    private fun query(bounds: AABB) = ShipFluidInteraction.prepareVolume(world, listOf(ship), bounds)

    @Test fun `entity straddling a dry domain keeps exterior world immersion`() {
        flooding = floodingSnapshot()
        val q = query(AABB(0.2, 0.1, 0.2, 1.2, 0.9, 0.8))
        assertTrue(q.worldHeight(BlockPos.ZERO, 1.0f).isNaN())
        assertEquals(1.0f, q.worldHeight(BlockPos(1, 0, 0), 1.0f))
        assertEquals(0.0, q.retainedDepth(FluidTags.WATER))
    }

    @Test fun `entity queries capture one pair and ignore orphaned diagnostic components`() {
        every { world.getFluidTopologySnapshot(any()) } throws AssertionError("Independent topology read")
        every { world.getFluidFloodingSnapshot(any()) } throws AssertionError("Independent liquid read")
        assertTrue(query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)).retainedDepth(FluidTags.WATER) > 0.0)
        verify(exactly = 1) { world.getFluidGameplaySnapshot(7) }
        every { world.getFluidGameplaySnapshot(7) } returns null
        val missing = query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9))
        assertFalse(missing.hasDomains())
        assertEquals(0.0, missing.retainedDepth(FluidTags.WATER))
        assertEquals(1.0f, missing.worldHeight(BlockPos.ZERO, 1.0f))
    }

    @Test fun `partial cell domain masks only its portion of a world block`() {
        flooding = floodingSnapshot()
        transform.scale(0.5)
        val q = query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9))
        assertEquals(1.0f, q.worldHeight(BlockPos.ZERO, 1.0f), 1e-6f)
        val enclosed = query(AABB(0.1, 0.1, 0.1, 0.4, 0.4, 0.4))
        assertTrue(enclosed.worldHeight(BlockPos.ZERO, 1.0f).isNaN())
    }

    @Test fun `retained depth and world masking use distinct ownership and preserve fluid types`() {
        val q = query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9))
        assertEquals(128.0 / 255 - 0.1, q.retainedDepth(FluidTags.WATER), 1e-12)
        assertEquals(0.0, q.retainedDepth(FluidTags.LAVA))
        assertTrue(q.worldHeight(BlockPos.ZERO, 1.0f).isNaN())
        val found = mutableMapOf<FluidState, Double>()
        q.forEachRetained { state, depth -> found[state] = depth }
        assertEquals(mapOf(waterState to (128.0 / 255 - 0.1)), found)
        assertTrue(q.controlsCell(7, BlockPos.ZERO))
        assertFalse(q.controlsCell(7, BlockPos(1, 0, 0)))
    }

    @Test fun `tilted cell rejects enclosing box corners`() {
        transform.rotateZ(Math.PI / 4)
        val q = query(AABB(0.6, 0.1, 0.2, 0.65, 0.15, 0.8))
        assertEquals(0.0, q.retainedDepth(FluidTags.WATER))
        assertEquals(1.0f, q.worldHeight(BlockPos.ZERO, 1.0f), 1e-6f)
    }

    @Test fun `tiny scale and huge local coordinates do not scan empty inverse transformed volume`() {
        transform.scale(1e-9)
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val q = query(AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0))
            assertTrue(q.retainedDepth(FluidTags.WATER) > 1.0)
            assertTrue(q.hasDomains())
        }
        topology = topology.copy(streamSequence = 4, voxels = listOf(VsiFluidTopologyVoxel(Int.MAX_VALUE, 0, 0, 1)))
        flooding = flooding.copy(streamSequence = 5, voxels = listOf(VsiFluidFloodedVoxel(Int.MAX_VALUE, 0, 0, 17, 255, 0)))
        transform.identity().translation(-Int.MAX_VALUE.toDouble(), 0.0, 0.0)
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            assertEquals(0.8, query(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)).retainedDepth(FluidTags.WATER), 1e-12)
        }
    }

    @Test fun `cached runs coalesce a complete domain without filling its holes`() {
        flooding = floodingSnapshot()
        val cells = (0..3).flatMap { x -> (0..3).flatMap { y -> (0..3).map { z -> VsiFluidTopologyVoxel(x, y, z, 1) } } }
        topology = topology.copy(voxels = cells, totalDomainVoxelCount = cells.size)
        val body = ShipFluidInteraction.bodyFor(world, 7)!!
        val field = body.javaClass.getDeclaredField("domainBoxes").apply { isAccessible = true }
        assertEquals(1, (field.get(body) as List<*>).size)
        flooding = flooding.copy(streamSequence = 5)
        assertSame(field.get(body), field.get(ShipFluidInteraction.bodyFor(world, 7)), "Fill updates must reuse domain runs")
        assertTrue(query(AABB(0.1, 0.1, 0.1, 3.9, 3.9, 3.9)).worldHeight(BlockPos(1, 1, 1), 1.0f).isNaN())
        topology = topology.copy(streamSequence = 4, totalDomainVoxelCount = cells.size - 1,
            voxels = cells.filterNot { it.positionX == 1 && it.positionY == 1 && it.positionZ == 1 })
        assertEquals(1.0f, query(AABB(0.1, 0.1, 0.1, 3.9, 3.9, 3.9)).worldHeight(BlockPos(1, 1, 1), 1.0f))
    }

    @Test fun `ordinary small queries use bounded hash probes and huge queries enumerate sparse cells`() {
        val cells = (0 until 5000).map { VsiFluidFloodedVoxel(it, 0, 0, 17, 255, 0) }
        val body = spyk(ShipFluidInteraction.CachedBody(topology, flooding.copy(voxels = cells)))
        val found = mutableListOf<VsiFluidFloodedVoxel>()
        body.forEachFlooded(AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)) { found.add(it) }
        assertEquals(listOf(cells.first()), found)
        verify(exactly = 1) { body.getFloodedVoxel(any(), any(), any()) }
        clearMocks(body, answers = false)
        found.clear()
        body.forEachFlooded(AABB(-1e20, -1e20, -1e20, 1e20, 1e20, 1e20)) { found.add(it) }
        assertEquals(5000, found.size)
        verify(exactly = 0) { body.getFloodedVoxel(any(), any(), any()) }
    }
}
