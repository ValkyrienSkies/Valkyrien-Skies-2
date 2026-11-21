package org.valkyrienskies.mod.common

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Vector4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece

class ShipEffectLocationsTest {
    private val entrance = ShipPhysicalPiece(1, "world", AABBd(-2.0, -2.0, -2.0, 2.0, 2.0, 2.0),
        Matrix4d(), listOf(Vector4d(0.0, 0.0, 1.0, -0.5)))
    private val exit = ShipPhysicalPiece(1, "world", AABBd(98.0, -2.0, -2.0, 102.0, 2.0, 2.0),
        Matrix4d().translation(100.0, 0.0, 0.0).rotateY(Math.PI / 2), listOf(Vector4d(-1.0, 0.0, 0.0, 100.5)))
    private val pieces = listOf(entrance, exit)

    @Test fun `block event covers retained material even when its origin is past the cut`() {
        val positions = ShipEffectLocations.block(pieces, BlockPos.ZERO, 0.5, 0.5, 0.8)
        assertEquals(2, positions.size)
        assertEquals(0.8, positions[0].z, 1e-8)
        assertTrue(positions[1].x in 100.0..100.5)
        assertEquals(1, ShipEffectLocations.block(pieces, BlockPos(0, 0, -1), 0.5, 0.5, -0.5).size)
        assertEquals(1, ShipEffectLocations.block(pieces, BlockPos(0, 0, 1), 0.5, 0.5, 1.5).size)
    }

    @Test fun `a point on the seam follows its listener while a point beyond it has one image`() {
        val nearExit = Vec3(101.0, 0.0, 0.0)
        assertSame(exit, ShipEffectLocations.point(pieces, 0.5, 0.5, 0.5, nearExit))
        assertSame(entrance, ShipEffectLocations.point(pieces, 0.5, 0.5, 0.5, Vec3.ZERO))
        assertSame(entrance, ShipEffectLocations.point(pieces, 0.5, 0.5, 0.8, nearExit))
        assertTrue(ShipEffectLocations.nearestBlock(pieces, 0.5, 0.5, 0.8, nearExit)!!.x in 100.0..100.5)
    }
}
