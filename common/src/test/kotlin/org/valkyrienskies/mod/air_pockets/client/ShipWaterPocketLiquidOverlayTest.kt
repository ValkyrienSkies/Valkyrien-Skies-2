package org.valkyrienskies.mod.air_pockets.client

import java.util.BitSet
import kotlin.math.abs
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ShipWaterPocketLiquidOverlayTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `transparent block next to submerged exterior is selected`() {
        val open = bitSetOf(0)
        val interior = BitSet()
        val waterReachable = bitSetOf(0)
        val overlaySolids = bitSetOf(1)

        val selected = ShipWaterPocketLiquidOverlay.isOutsideSubmergedFluid(open, interior, waterReachable, 0) &&
            ShipWaterPocketLiquidOverlay.touchesOverlayBoundary(open, interior, waterReachable, overlaySolids, null, 0, 2, 1, 1)

        assertTrue(selected)
    }

    @Test
    fun `open submerged hole is selected`() {
        val open = bitSetOf(0, 1)
        val interior = bitSetOf(1)
        val waterReachable = bitSetOf(0)

        val selected = ShipWaterPocketLiquidOverlay.isOutsideSubmergedFluid(open, interior, waterReachable, 0) &&
            ShipWaterPocketLiquidOverlay.touchesOverlayBoundary(open, interior, waterReachable, null, null, 0, 2, 1, 1)

        assertTrue(selected)
    }

    @Test
    fun `interior only cells are not selected`() {
        val open = bitSetOf(0)
        val interior = bitSetOf(0)
        val waterReachable = bitSetOf(0)

        assertFalse(ShipWaterPocketLiquidOverlay.isOutsideSubmergedFluid(open, interior, waterReachable, 0))
    }

    @Test
    fun `non submerged outside cells are not selected`() {
        val open = bitSetOf(0, 1)
        val interior = bitSetOf(1)
        val waterReachable = BitSet()

        val selected = ShipWaterPocketLiquidOverlay.isOutsideSubmergedFluid(open, interior, waterReachable, 0) &&
            ShipWaterPocketLiquidOverlay.touchesOverlayBoundary(open, interior, waterReachable, null, null, 0, 2, 1, 1)

        assertFalse(selected)
    }

    @Test
    fun `door voxel marked open still counts as full cell overlay boundary`() {
        val open = bitSetOf(0, 1)
        val interior = BitSet()
        val waterReachable = bitSetOf(0)
        val overlaySolids = bitSetOf(1)
        val fullCellOverlaySolids = bitSetOf(1)

        val selected = ShipWaterPocketLiquidOverlay.isOutsideSubmergedFluid(open, interior, waterReachable, 0) &&
            ShipWaterPocketLiquidOverlay.touchesOverlayBoundary(
                open,
                interior,
                waterReachable,
                overlaySolids,
                fullCellOverlaySolids,
                0,
                2,
                1,
                1
            )

        assertTrue(selected)
        assertTrue(ShipWaterPocketLiquidOverlay.isFullCellOverlaySolid(fullCellOverlaySolids, 1))
    }

    @Test
    fun `door voxel is not treated as an outside emission source`() {
        val fullCellOverlaySolids = bitSetOf(1)

        assertTrue(ShipWaterPocketLiquidOverlay.isFullCellOverlaySolid(fullCellOverlaySolids, 1))
        assertFalse(ShipWaterPocketLiquidOverlay.isFullCellOverlaySolid(fullCellOverlaySolids, 0))
    }

    @Test
    fun `clipping trims polygon to fluid height`() {
        val inX = floatArrayOf(0f, 0f, 0f, 0f)
        val inY = floatArrayOf(0f, 1f, 1f, 0f)
        val inZ = floatArrayOf(0f, 0f, 1f, 1f)
        val inU = floatArrayOf(0f, 1f, 1f, 0f)
        val inV = floatArrayOf(0f, 0f, 1f, 1f)
        val outX = FloatArray(6)
        val outY = FloatArray(6)
        val outZ = FloatArray(6)
        val outU = FloatArray(6)
        val outV = FloatArray(6)

        val outCount = ShipWaterPocketLiquidOverlay.clipToSurfaceY(
            inX,
            inY,
            inZ,
            inU,
            inV,
            4,
            outX,
            outY,
            outZ,
            outU,
            outV,
            0.0,
            1.0,
            0.0,
            0.0,
            0.5
        )

        assertEquals(4, outCount)
        val maxY = outY.take(outCount).max()
        assertTrue(abs(maxY - 0.5f) < 1.0e-4f)
    }

    @Test
    fun `fluid fallback order prefers sampled then snapshot then water`() {
        assertSame(Fluids.LAVA, ShipWaterPocketLiquidOverlay.chooseOverlayFluid(Fluids.LAVA, Fluids.WATER))
        assertSame(Fluids.WATER, ShipWaterPocketLiquidOverlay.chooseOverlayFluid(null, Fluids.WATER))
        assertSame(Fluids.WATER, ShipWaterPocketLiquidOverlay.chooseOverlayFluid(null, null))
    }

    @Test
    fun `fluid sprite selection prefers still before overlay and flow`() {
        assertEquals(0, ShipWaterPocketFluidVisualHelper.preferredSpriteIndex(true, true, true))
        assertEquals(2, ShipWaterPocketFluidVisualHelper.preferredSpriteIndex(false, true, true))
        assertEquals(1, ShipWaterPocketFluidVisualHelper.preferredSpriteIndex(false, true, false))
        assertEquals(-1, ShipWaterPocketFluidVisualHelper.preferredSpriteIndex(false, false, false))
    }

    @Test
    fun `solid render layer can still qualify as transparent overlay block`() {
        val candidate = ShipWaterPocketLiquidOverlay.isOverlaySolidCandidate(false, false, true, 0)
        assertTrue(candidate)
    }

    @Test
    fun `opaque solid block is not treated as transparent overlay block`() {
        val candidate = ShipWaterPocketLiquidOverlay.isOverlaySolidCandidate(false, true, false, 15)
        assertFalse(candidate)
    }

    @Test
    fun `overlay uv scale enlarges the sampled texture`() {
        assertEquals(1.0f, ShipWaterPocketLiquidOverlay.scaleOverlayUv(1.0f))
        assertEquals(0.5f, ShipWaterPocketLiquidOverlay.scaleOverlayUv(0.5f))
    }

    @Test
    fun `recessed thin barrier exposes only the wet face`() {
        val recessedDoorLikeBox = listOf(AABB(0.75, 0.0, 0.0, 1.0, 1.0, 1.0))

        val wetFaceCount = ShipWaterPocketLiquidOverlay.countAccessibleSolidInterfacesForBoxes(
            recessedDoorLikeBox,
            0, // SHAPE_FACE_NEG_X
            0, // SHAPE_FACE_NEG_X
        )
        val dryFaceCount = ShipWaterPocketLiquidOverlay.countAccessibleSolidInterfacesForBoxes(
            recessedDoorLikeBox,
            0, // SHAPE_FACE_NEG_X
            1, // SHAPE_FACE_POS_X
        )

        assertNotEquals(0, wetFaceCount)
        assertEquals(0, dryFaceCount)
    }

    @Test
    fun `doors use full cell solid overlays`() {
        assertTrue(ShipWaterPocketLiquidOverlay.shouldUseFullCellSolidOverlay(Blocks.OAK_DOOR.defaultBlockState()))
        assertFalse(ShipWaterPocketLiquidOverlay.shouldUseFullCellSolidOverlay(Blocks.GLASS.defaultBlockState()))
        assertTrue(ShipWaterPocketLiquidOverlay.shouldRenderOverlaySolidInterfaces(Blocks.OAK_DOOR.defaultBlockState()))
        assertTrue(ShipWaterPocketLiquidOverlay.shouldRenderOverlaySolidInterfaces(Blocks.GLASS.defaultBlockState()))
    }

    private fun bitSetOf(vararg indices: Int): BitSet =
        BitSet().apply {
            indices.forEach(::set)
        }
}
