package org.valkyrienskies.mod.common.render.batched

import com.mojang.blaze3d.vertex.*
import io.mockk.*
import kotlin.math.abs
import net.minecraft.SharedConstants
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.ChestRenderer
import net.minecraft.client.resources.model.Material
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece

class SplitChestRenderTest {
    @Test fun `open chest retains its lid and surface area across both mouths`() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val context = mockk<BlockEntityRendererProvider.Context>()
        every { context.bakeLayer(ModelLayers.CHEST) } returns ChestRenderer.createSingleBodyLayer().bakeRoot()
        every { context.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT) } returns ChestRenderer.createDoubleBodyLeftLayer().bakeRoot()
        every { context.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT) } returns ChestRenderer.createDoubleBodyRightLayer().bakeRoot()
        val renderer = ChestRenderer<ChestBlockEntity>(context)
        val cell = BlockPos(-28669952, 128, 12290048)
        val state = Blocks.CHEST.defaultBlockState()
        val chest = ChestBlockEntity(cell, state)
        val type = Sheets.chestSheet()
        val material = mockk<Material>()
        every { material.buffer(any(), any()) } answers { firstArg<MultiBufferSource>().getBuffer(type) }

        fun vertices(piece: ShipPhysicalPiece? = null, camera: Vec3 = Vec3.ZERO): List<Vector3d> {
            val builder = BufferBuilder(1024)
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY)
            val output = MultiBufferSource { builder }
            fun draw(pose: PoseStack, buffers: MultiBufferSource) {
                renderer.render(chest, 0.5f, pose, buffers, 0x00f000d0, 0x000a0003)
            }
            if (piece == null) draw(PoseStack(), output)
            else ShipPieceBuffers.draw(listOf(piece), cell, PoseStack(), camera, output, ::draw)
            val batch = builder.end()
            return try {
                val bytes = batch.vertexBuffer()
                List(batch.drawState().vertexCount()) { i ->
                    val offset = i * DefaultVertexFormat.NEW_ENTITY.vertexSize
                    assertEquals(0x000a0003, bytes.getInt(offset + 24))
                    assertEquals(0x00f000d0, bytes.getInt(offset + 28))
                    Vector3d(bytes.getFloat(offset).toDouble(), bytes.getFloat(offset + 4).toDouble(),
                        bytes.getFloat(offset + 8).toDouble())
                }
            } finally { batch.release() }
        }
        fun area(points: List<Vector3d>) = points.chunked(4).sumOf { (a, b, c, d) ->
            val diagonal = c.sub(a, Vector3d())
            (b.sub(a, Vector3d()).cross(diagonal).length() + diagonal.cross(d.sub(a, Vector3d())).length()) * 0.5
        }
        mockkStatic(Sheets::class) {
            every { Sheets.chooseMaterial(chest, any(), any()) } returns material
            val closed = vertices()
            chest.triggerEvent(1, 1)
            val level = mockk<Level>()
            repeat(12) { ChestBlockEntity.lidAnimateTick(level, cell, state, chest) }
            val open = vertices()
            assertTrue(open.maxOf { it.y } > closed.maxOf { it.y } + 0.25)
            val halves = listOf(0.0, 100.0).map { x ->
                val transform = Matrix4d().translation(x, 0.0, 0.0).translate(-cell.x.toDouble(), -cell.y.toDouble(), -cell.z.toDouble())
                val plane = if (x == 0.0) Vector4d(-1.0, 0.0, 0.0, 0.5) else Vector4d(1.0, 0.0, 0.0, -100.5)
                val piece = ShipPhysicalPiece(1, "test", AABBd(x, 0.0, 0.0, x + 1, 1.0, 1.0), transform, listOf(plane))
                val half = vertices(piece, Vec3(x + 0.25, 0.0, 0.0)).onEach { it.x += 0.25 }
                assertEquals(open.maxOf { it.y }, half.maxOf { it.y }, 1e-5)
                assertTrue(half.all { if (x == 0.0) it.x <= 0.50001 else it.x >= 0.49999 })
                assertTrue(half.any { abs(it.x - 0.5) < 1e-5 })
                half
            }
            assertEquals(area(open), halves.sumOf(::area), 1e-5)
        }
    }
}
