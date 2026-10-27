package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SplitOwnershipTest {
    private val tree = (130..150).map { BlockPos(10, it, 10) }.toSet()
    private val platform = setOf(BlockPos(10, 128, 10), BlockPos(11, 128, 10))

    @Test
    fun smallerPlatformKeepsBearingAndOriginalShipId() {
        val anchor = SplitOwnership.Anchor(Vector3d(10.5, 127.5, 10.5), true)
        assertEquals(1, SplitOwnership.retainedComponent(listOf(tree, platform), listOf(anchor)))
    }

    @Test
    fun anchorWorksOnEitherSideAndInEitherComponentOrder() {
        for (position in listOf(Vector3d(10.5, 127.5, 10.5), Vector3d(9.5, 128.5, 10.5))) {
            assertEquals(0, SplitOwnership.retainedComponent(listOf(platform, tree), listOf(SplitOwnership.Anchor(position, false))))
        }
    }

    @Test
    fun freeSplitStillKeepsLargestPiece() {
        assertEquals(0, SplitOwnership.retainedComponent(listOf(tree, platform), emptyList()))
        assertEquals(1, SplitOwnership.retainedComponent(listOf(platform, tree), emptyList()))
    }

    @Test
    fun groundedBearingTakesPriorityOverOtherAttachments() {
        val anchors = listOf(
            SplitOwnership.Anchor(Vector3d(10.5, 127.5, 10.5), true),
            SplitOwnership.Anchor(Vector3d(10.5, 140.5, 10.5), false),
            SplitOwnership.Anchor(Vector3d(10.5, 141.5, 10.5), false)
        )
        assertEquals(1, SplitOwnership.retainedComponent(listOf(tree, platform), anchors))
    }
}
