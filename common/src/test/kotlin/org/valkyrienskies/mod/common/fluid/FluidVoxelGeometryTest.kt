package org.valkyrienskies.mod.common.fluid

import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedRegion
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel

class FluidVoxelGeometryTest {
    private fun bounds(x: Int, y: Int, z: Int, xx: Int, yy: Int, zz: Int) =
        x or (y shl 5) or (z shl 10) or (xx shl 15) or (yy shl 20) or (zz shl 25)

    @Test
    fun `slab water occupies only the void above the slab at its actual height`() {
        val shape = bounds(0, 8, 0, 16, 16, 16)
        val domain = VsiFluidTopologyVoxel(4, 2, -7, 1, listOf(shape.toLong() or (1L shl 30)))
        val water = VsiFluidFloodedVoxel(4, 2, -7, 1, 64, 0, listOf(VsiFluidFloodedRegion(shape, 1, 0.75)))
        assertFalse(FluidVoxelGeometry.contains(domain, 0.5, 0.4, 0.5))
        assertTrue(FluidVoxelGeometry.contains(domain, 0.5, 0.5, 0.5))
        assertEquals(0, FluidVoxelGeometry.fluidAt(water, 0.5, 0.4, 0.5))
        assertEquals(1, FluidVoxelGeometry.fluidAt(water, 0.5, 0.6, 0.5))
        assertEquals(0, FluidVoxelGeometry.fluidAt(water, 0.5, 0.75, 0.5))
        val domains = mutableListOf<AABB>()
        val liquids = mutableListOf<AABB>()
        FluidVoxelGeometry.forEachDomain(domain, domains::add)
        FluidVoxelGeometry.forEachFlooded(water) { _, box -> liquids.add(box) }
        assertEquals(listOf(AABB(4.0, 2.5, -7.0, 5.0, 3.0, -6.0)), domains)
        assertEquals(listOf(AABB(4.0, 2.5, -7.0, 5.0, 2.75, -6.0)), liquids)
    }

    @Test
    fun `a thin wall separates dry and wet components and different fluids`() {
        val left = bounds(0, 0, 0, 7, 16, 16)
        val right = bounds(9, 0, 0, 16, 16, 16)
        val water = VsiFluidFloodedVoxel(0, 0, 0, 2, 255, 0, listOf(
            VsiFluidFloodedRegion(left, 1, 0.25), VsiFluidFloodedRegion(right, 2, 1.0)))
        assertEquals(1, FluidVoxelGeometry.fluidAt(water, 0.2, 0.1, 0.5))
        assertEquals(0, FluidVoxelGeometry.fluidAt(water, 0.2, 0.3, 0.5))
        assertEquals(0, FluidVoxelGeometry.fluidAt(water, 0.5, 0.1, 0.5))
        assertEquals(2, FluidVoxelGeometry.fluidAt(water, 0.8, 0.9, 0.5))
        val volumes = mutableMapOf<Int, Double>()
        FluidVoxelGeometry.forEachFlooded(water) { id, box -> volumes[id] = box.xsize * box.ysize * box.zsize }
        assertEquals(7.0 / 64, volumes[1])
        assertEquals(7.0 / 16, volumes[2])
    }
}
