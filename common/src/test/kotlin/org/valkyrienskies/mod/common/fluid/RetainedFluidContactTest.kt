package org.valkyrienskies.mod.common.fluid

import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion
import java.util.function.IntPredicate
import kotlin.math.sqrt

class RetainedFluidContactTest {
    private val water = IntPredicate { it == 7 }
    private val cell = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
    private val bounds = (16 shl 15) or (16 shl 20) or (16 shl 25)
    private fun voxel(amount: Double, other: Double = 0.0) = VsiFluidFloodedVoxel(1000, 20, -4, 7,
        (amount + other).toInt(), 0, surfaceRegions = listOf(VsiFluidSurfaceRegion(bounds,
            0.0, 1.0, 0.0, 0.0, (amount + other) / 255,
            buildList {
                if (amount > 0) add(VsiFluidSurfaceConstituent(7, amount))
                if (other > 0) add(VsiFluidSurfaceConstituent(8, other))
            })))

    @Test fun `water destruction uses the exact one eighth amount boundary`() {
        for (amount in listOf(Math.nextDown(31.875), 31.875, Math.nextUp(31.875))) {
            assertEquals(amount >= 31.875,
                RetainedFluidContact.canDestroy(voxel(amount), listOf(cell), water), "amount=$amount")
        }
    }

    @Test fun `pressure storage and another constituent cannot satisfy water threshold`() {
        val mixed = voxel(31.0, 100.0).copy(pressureAmount = 60000)
        assertFalse(RetainedFluidContact.canDestroy(mixed, listOf(cell), water))
        assertEquals(31.0 / 255, RetainedFluidContact.volume(mixed, water))
        assertTrue(RetainedFluidContact.canDestroy(voxel(31.875, 100.0), listOf(cell), water))
    }

    @Test fun `sufficient water must touch the target shape`() {
        val shallow = voxel(31.875)
        assertFalse(RetainedFluidContact.canDestroy(shallow,
            listOf(AABB(0.25, 0.5, 0.25, 0.75, 0.75, 0.75)), water))
        assertTrue(RetainedFluidContact.canDestroy(shallow,
            listOf(AABB(0.25, 0.0, 0.25, 0.75, 0.0625, 0.75)), water))
    }

    @Test fun `small cavity capacity does not normalize a small amount into a full block`() {
        val smallBounds = (4 shl 15) or (4 shl 20) or (4 shl 25)
        val small = VsiFluidFloodedVoxel(0, 0, 0, 7, 4, 0, surfaceRegions = listOf(
            VsiFluidSurfaceRegion(smallBounds, 0.0, 1.0, 0.0, 0.0, .25,
                listOf(VsiFluidSurfaceConstituent(7, 255.0 / 64)))))
        assertFalse(RetainedFluidContact.canDestroy(small, listOf(cell), water))
    }

    @Test fun `tilted material planes determine actual contact`() {
        val initial = voxel(63.75)
        val tilted = initial.copy(surfaceRegions = listOf(VsiFluidSurfaceRegion(bounds,
            1 / sqrt(2.0), 1 / sqrt(2.0), 0.0, 0.0, 0.5,
            listOf(VsiFluidSurfaceConstituent(7, 63.75)))))
        assertTrue(RetainedFluidContact.canDestroy(tilted, listOf(AABB(0.0, 0.0, 0.0, .25, .25, 1.0)), water))
        assertFalse(RetainedFluidContact.canDestroy(tilted, listOf(AABB(.75, .75, 0.0, 1.0, 1.0, 1.0)), water))
    }

    @Test fun `contact with a cavity face counts but an edge or separated solid does not`() {
        val halfBounds = (8 shl 15) or (16 shl 20) or (16 shl 25)
        val wet = VsiFluidFloodedVoxel(0, 0, 0, 7, 128, 0, surfaceRegions = listOf(
            VsiFluidSurfaceRegion(halfBounds, 0.0, 1.0, 0.0, 0.0, 1.0,
                listOf(VsiFluidSurfaceConstituent(7, 127.5)))))
        assertTrue(RetainedFluidContact.canDestroy(wet, listOf(AABB(.5, 0.0, 0.0, 1.0, 1.0, 1.0)), water))
        assertFalse(RetainedFluidContact.canDestroy(wet, listOf(AABB(.5, 1.0, 0.0, 1.0, 2.0, 1.0)), water))
        assertFalse(RetainedFluidContact.canDestroy(wet, listOf(AABB(.6, 0.0, 0.0, 1.0, 1.0, 1.0)), water))
    }

    @Test fun `opposite layers touch only if their filled regions meet`() {
        val lava = IntPredicate { it == 8 }
        val waterLayer = VsiFluidSurfaceRegion(bounds, 0.0, 1.0, 0.0, .25, .5,
            listOf(VsiFluidSurfaceConstituent(7, 63.75)))
        val lavaLayer = VsiFluidSurfaceRegion(bounds, 0.0, 1.0, 0.0, 0.0, .25,
            listOf(VsiFluidSurfaceConstituent(8, 63.75)))
        val touching = voxel(63.75).copy(surfaceRegions = listOf(waterLayer, lavaLayer))
        val separated = touching.copy(surfaceRegions = listOf(waterLayer.copy(lowerProjection = .3), lavaLayer))
        assertTrue(RetainedFluidContact.materialsTouch(touching, water, touching, lava))
        assertFalse(RetainedFluidContact.materialsTouch(separated, water, separated, lava))
    }

    @Test fun `legacy fills exclude pressure and retain block-local coordinates`() {
        assertFalse(RetainedFluidContact.canDestroy(VsiFluidFloodedVoxel(1000, 20, -4, 7, 31, 60000), listOf(cell), water))
        assertTrue(RetainedFluidContact.canDestroy(VsiFluidFloodedVoxel(1000, 20, -4, 7, 32, 0), listOf(cell), water))
        assertFalse(RetainedFluidContact.canDestroy(VsiFluidFloodedVoxel(1000, 20, -4, 8, 255, 0), listOf(cell), water))
    }

    @Test fun `empty outline shapes still allow fire replacement at the threshold`() {
        assertTrue(RetainedFluidContact.canDestroy(voxel(31.875), emptyList(), water))
        assertFalse(RetainedFluidContact.canDestroy(voxel(31.0), emptyList(), water))
    }
}
