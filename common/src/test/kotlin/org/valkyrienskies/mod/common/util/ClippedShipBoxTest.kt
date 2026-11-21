package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.shapes.Shapes
import org.joml.Matrix4d
import org.joml.Vector4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece

class ClippedShipBoxTest {
    private val block = AABBd(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
    private val placed = AABBd(100.0, 0.0, 0.0, 101.0, 1.0, 1.0)
    private fun piece(vararg planes: Vector4d) = ShipPhysicalPiece(1, "test", placed,
        Matrix4d().translation(100.0, 0.0, 0.0), planes.toList())

    @Test fun `oblique cuts remove standing support and penetration in the empty corner`() {
        val cut = ClippedShipBox.clip(block, piece(Vector4d(1.0, 0.0, 1.0, -101.0),
            Vector4d(0.0, 0.0, -1.0, 0.9)))!!
        val query = ShipCollisionQuery()
        query.addClipped(cut.vertices, cut.axes)
        assertTrue(query.hasStandingSupport(100.70, 1.0, 0.6, 100.8, 2.8, 0.8))
        assertFalse(query.hasStandingSupport(100.1, 1.0, 0.2, 100.2, 2.8, 0.3))
        assertTrue(query.penetrationResponse(100.1, 0.5, 0.2, 100.2, 1.5, 0.3).lengthSquared() == 0.0)
        assertEquals(1.0, query.supportHeight(100.7, 0.9, 0.6, 100.8, 1.1, 0.8), 1e-8)
        assertTrue(query.supportHeight(100.1, 0.9, 0.2, 100.2, 1.1, 0.3).isNaN())
    }

    @Test fun `placing only obstructs a player inside the retained shape`() {
        val pieces = listOf(piece(Vector4d(1.0, 0.0, 0.0, -100.5)))
        val shape = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)
        fun obstructs(x: Double, y: Double) = ClippedShipBox.intersectsPlacement(pieces, shape,
            BlockPos.ZERO, AABBd(x, y, 0.2, x + 0.2, y + 1.8, 0.8))
        assertFalse(obstructs(100.1, 0.1))
        assertTrue(obstructs(100.6, 0.1))
        assertFalse(obstructs(100.6, 0.6))
    }

    @Test fun `touching a cut with zero retained volume cannot block an entity`() {
        assertNull(ClippedShipBox.clip(block, piece(Vector4d(1.0, 0.0, 0.0, -101.0))))
    }
}
