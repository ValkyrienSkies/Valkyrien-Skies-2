package org.valkyrienskies.mod.common.fluid

import io.mockk.every
import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState
import org.joml.Matrix4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel

class WorldFluidCoverageTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `wet top center cannot hide an exposed edge or an interior hole`() {
        val bounds = AABBd(0.1, 0.1, 0.1, 2.9, 0.8, 2.9)
        assertTrue(WorldFluidCoverage.coversBounds(bounds) { _, _, _ -> 1.0 })
        assertFalse(WorldFluidCoverage.coversBounds(bounds) { x, _, z ->
            if (x == 0 && z == 0) -1.0 else 1.0
        })
        assertFalse(WorldFluidCoverage.coversBounds(bounds) { x, _, z ->
            if (x == 1 && z == 1) -1.0 else 1.0
        })
        assertFalse(WorldFluidCoverage.coversBounds(bounds) { x, _, z ->
            if (x == 0 && z == 0) 0.75 else 1.0
        })
    }

    @Test
    fun `rotated scaled partial cell uses the full transformed volume`() {
        val voxel = VsiFluidFloodedVoxel(-2, 1, 3, 17, 128, 0)
        val bounds = AABBd()
        val transform = Matrix4d().rotateZ(Math.PI / 4).scale(2.0)
        WorldFluidCoverage.transformedBounds(transform, voxel, bounds)
        assertTrue(bounds.maxY - bounds.minY > 2.0)
        assertTrue(WorldFluidCoverage.coversBounds(bounds) { _, _, _ -> 1.0 })
        assertFalse(WorldFluidCoverage.coversBounds(bounds) { x, _, _ ->
            if (x == kotlin.math.floor(bounds.minX).toInt()) -1.0 else 1.0
        })
    }

    @Test
    fun `exact negative block boundaries do not require the next cell`() {
        val visited = mutableSetOf<Triple<Int, Int, Int>>()
        assertTrue(WorldFluidCoverage.coversBounds(AABBd(-2.0, -1.0, -3.0, -1.0, 0.0, -2.0)) { x, y, z ->
            visited.add(Triple(x, y, z))
            1.0
        })
        assertEquals(setOf(Triple(-2, -1, -3)), visited)
    }

    @Test
    fun `oversized or invalid coverage draws conservatively without scanning`() {
        for (bounds in listOf(
            AABBd(0.0, 0.0, 0.0, 1000.0, 1000.0, 1000.0),
            AABBd(Double.NaN, 0.0, 0.0, 1.0, 1.0, 1.0),
            AABBd(0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, 1.0, 1.0),
            AABBd(Int.MAX_VALUE - 1.0, 0.0, 0.0, Int.MAX_VALUE.toDouble(), 1.0, 1.0),
        )) {
            assertFalse(WorldFluidCoverage.coversBounds(bounds) { _, _, _ ->
                fail("Unsafe bounds must not reach the world sampler")
            })
        }
    }

    @Test
    fun `ambient ocean cannot hide the surface below protected compartment air`() {
        val partial = VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0)
        val full = partial.copy(fillAmount = 255)
        val topology = maskTopology(Triple(0, 0, 0), Triple(0, 1, 0))
        assertTrue(WorldFluidCoverage.hasProtectedBoundary(partial, topology, maskFlooding(partial)))
        assertTrue(WorldFluidCoverage.hasProtectedBoundary(full, topology, maskFlooding(full)))
        assertTrue(WorldFluidCoverage.hasProtectedBoundary(full, null, maskFlooding(full)))
        val containedCell = maskTopology(Triple(0, 0, 0))
        assertFalse(WorldFluidCoverage.hasProtectedBoundary(full, containedCell, maskFlooding(full)))
    }

    @Test
    fun `production coverage requires compatible liquid and loaded cells`() {
        val level = mockk<ClientLevel>()
        val retained = mockk<FlowingFluid>()
        val worldType = mockk<FlowingFluid>()
        val state = mockk<FluidState>()
        every { level.hasChunkAt(any()) } returns true
        every { level.getFluidState(any<BlockPos>()) } returns state
        every { state.isEmpty } returns false
        every { state.type } returns worldType
        every { state.getHeight(level, any()) } returns 1f
        every { retained.isSame(worldType) } returns true
        val coverage = WorldFluidCoverage(level)
        val voxel = VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0)
        assertTrue(coverage.covers(Matrix4d(), voxel, retained))
        every { retained.isSame(worldType) } returns false
        assertFalse(coverage.covers(Matrix4d(), voxel, retained))
        every { retained.isSame(worldType) } returns true
        // A source's own height alone does not prove coverage near a sloping world-fluid edge.
        every { state.getHeight(level, any()) } answers {
            if (secondArg<BlockPos>().x == -1) 0.25f else 0.875f
        }
        assertFalse(coverage.covers(Matrix4d().scaling(0.75), voxel, retained))
        every { state.getHeight(level, any()) } returns 1f
        every { level.hasChunkAt(any()) } returns false
        assertFalse(coverage.covers(Matrix4d(), voxel, retained))
        assertFalse(coverage.covers(Matrix4d(), voxel, null))
    }
}
