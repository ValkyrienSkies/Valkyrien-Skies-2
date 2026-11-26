package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import org.joml.Matrix4d
import org.joml.Vector4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece

class SplitPiecePlacementTest {
    private val bounds = AABBd(-1000.0, -1000.0, -1000.0, 1000.0, 1000.0, 1000.0)
    private val canonical = ShipPhysicalPiece(1L, "dim", bounds, Matrix4d(), listOf(Vector4d(-1.0, 0.0, 0.0, 5.0)))
    private val image = ShipPhysicalPiece(1L, "dim", bounds, Matrix4d().translation(100.0, 0.0, 0.0),
        listOf(Vector4d(1.0, 0.0, 0.0, -105.0)))
    private val pieces = listOf(canonical, image)

    @Test
    fun `blocks past the portal use the projected piece`() {
        assertSame(image, ShipAssembler.dominantPiece(pieces, listOf(BlockPos(6, 0, 0), BlockPos(8, 0, 0))))
    }

    @Test
    fun `blocks before the portal use the canonical piece`() {
        assertSame(canonical, ShipAssembler.dominantPiece(pieces, listOf(BlockPos(1, 0, 0), BlockPos(2, 0, 0))))
    }
}
