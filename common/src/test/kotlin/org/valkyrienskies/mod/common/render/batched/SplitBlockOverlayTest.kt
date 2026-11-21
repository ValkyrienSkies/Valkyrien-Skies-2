package org.valkyrienskies.mod.common.render.batched

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import net.minecraft.world.level.Level
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import org.joml.Matrix4d
import org.joml.Vector4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.internal.ships.VsiQueryableShipData
import org.valkyrienskies.mod.common.allShips
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.squaredDistanceToPhysicalBlock
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece

class SplitBlockOverlayTest {
    private val cell = BlockPos(-28669952, 128, 12290048)
    private val camera = Vec3(2.0, 2.0, 2.0)
    private val pieces = listOf(0.0, 100.0).map { mouth ->
        val matrix = Matrix4d().translation(mouth, 0.0, 0.0).translate(-cell.x.toDouble(), -cell.y.toDouble(), -cell.z.toDouble())
        val plane = if (mouth == 0.0) Vector4d(-1.0, 0.0, 0.0, .5) else Vector4d(1.0, 0.0, 0.0, -100.5)
        ShipPhysicalPiece(1, "test", AABBd(mouth, 0.0, 0.0, mouth + 1, 1.0, 1.0), matrix, listOf(plane))
    }

    @Test fun `mining reach follows retained material and rejects the empty image`() {
        val level = mockk<Level>()
        val ship = mockk<Ship>()
        val ships = mockk<VsiQueryableShipData<Ship>>()
        val exit = Vec3(100.5, .5, .5)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt") {
            every { level.allShips } returns ships
            every { level.getShipManagingPos(any<BlockPos>()) } returns ship
            every { level.toWorldCoordinates(exit) } returns exit
            every { ship.id } returns 1L
            every { ships.getSplitPieces(1L) } returns pieces
            assertEquals(0.0, level.squaredDistanceToPhysicalBlock(cell, exit))
            assertEquals(101.0 * 101.0, level.squaredDistanceToPhysicalBlock(cell.west(), exit))
        }
    }

    @Test fun `selection edges and mining quads end at the same seam at both mouths`() {
        val buffer = BufferBuilder(1024)
        fun checkPositions(format: VertexFormat) {
            val batch = buffer.end()
            try {
                val vertices = batch.vertexBuffer()
                val xs = (0 until batch.drawState().vertexCount()).map { vertices.getFloat(it * format.vertexSize) + camera.x }
                assertEquals(setOf(0.0, .5, 100.5, 101.0), xs.toSet())
            } finally {
                batch.release()
            }
        }
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)
        SplitShipRenderer.drawOutline(pieces, Shapes.block(), cell, PoseStack(), buffer, camera.x, camera.y, camera.z)
        checkPositions(DefaultVertexFormat.POSITION_COLOR_NORMAL)
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)
        SplitShipRenderer.drawDamage(pieces, cell, PoseStack(), camera, buffer) { _, face ->
            for ((x, y) in listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0)) {
                face.vertex(x, y, 1.0).color(255, 255, 255, 255).uv(0f, 0f).overlayCoords(0)
                    .uv2(0).normal(0f, 0f, 1f).endVertex()
            }
        }
        checkPositions(DefaultVertexFormat.BLOCK)
    }
}
