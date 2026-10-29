package org.valkyrienskies.mod.common.fluid

import com.mojang.blaze3d.vertex.VertexConsumer
import io.mockk.mockk
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel

class FloodedVoxelRendererTest {
    @Test
    fun `partial stacked cells retain both horizontal faces across the air gap`() {
        val quads = renderCells(128, 128).chunked(4)
        assertEquals(12, quads.size)
        assertEquals(4, quads.count { kotlin.math.abs(it.first().normal.y) > 0.99f })
        assertTrue(quads.any { it.first().normal.y > 0.99f && it.first().position.y < 1.0 })
        assertTrue(quads.any { it.first().normal.y < -0.99f && it.first().position.y > 1.0 })
    }

    @Test
    fun `full lower cell culls the contacting faces regardless of upper fill`() {
        for (upperFill in listOf(1, 128, 255)) {
            val quads = renderCells(255, upperFill).chunked(4)
            assertEquals(10, quads.size)
            assertEquals(2, quads.count { kotlin.math.abs(it.first().normal.y) > 0.99f })
        }
    }

    @Test
    fun `different liquids retain their boundary faces`() {
        assertEquals(12, renderCells(255, 255, upperFluidId = 18).size / 4)
    }

    @Test
    fun `one unit of fill still emits nondegenerate faces`() {
        val quads = renderCells(1, 1).chunked(4)
        assertEquals(12, quads.size)
        for (quad in quads) {
            val edgeA = Vector3d(quad[1].position).sub(quad[0].position)
            val edgeB = Vector3d(quad[2].position).sub(quad[0].position)
            assertTrue(edgeA.cross(edgeB).lengthSquared() > 0.0)
        }
    }

    @Test
    fun `normals are unit world vectors perpendicular to rotated scaled faces`() {
        val transform = Matrix4f().translation(100f, -20f, 50f)
            .rotateZ(0.6f).rotateX(0.3f).scale(2f, 3f, 4f)
        val vertices = renderCells(128, 128, transform = transform)
        for (quad in vertices.chunked(4)) {
            val edgeA = Vector3d(quad[1].position).sub(quad[0].position)
            val edgeB = Vector3d(quad[2].position).sub(quad[0].position)
            val expected = edgeA.cross(edgeB).normalize()
            for (vertex in quad) {
                assertEquals(1.0, vertex.normal.length().toDouble(), 1e-6)
                assertEquals(expected.x, vertex.normal.x.toDouble(), 1e-4)
                assertEquals(expected.y, vertex.normal.y.toDouble(), 1e-4)
                assertEquals(expected.z, vertex.normal.z.toDouble(), 1e-4)
            }
        }
    }

    private fun renderCells(
        lowerFill: Int,
        upperFill: Int,
        upperFluidId: Int = 17,
        transform: Matrix4f = Matrix4f(),
    ): List<Vertex> {
        val lower = VsiFluidFloodedVoxel(0, 0, 0, 17, lowerFill, 0)
        val upper = VsiFluidFloodedVoxel(0, 1, 0, upperFluidId, upperFill, 0)
        val cached = FloodedFluidClientCache.CachedSnapshot(floodingSnapshot(lower, upper))
        val sprite = mockk<TextureAtlasSprite>(relaxed = true)
        val recorder = Recorder()
        val normalMatrix = Matrix3f().set(transform).invert().transpose()
        for (voxel in listOf(lower, upper)) {
            FloodedVoxelRenderer.renderExposedFaces(
                transform, normalMatrix, recorder, cached, voxel, Vector3d(),
                1f, 1f, 1f, 0, false, sprite, sprite,
            )
        }
        return recorder.vertices
    }

    private data class Vertex(val position: Vector3d, val normal: Vector3f)

    private class Recorder : VertexConsumer {
        val vertices = mutableListOf<Vertex>()
        private var position = Vector3d()
        private var normal = Vector3f()
        override fun vertex(x: Double, y: Double, z: Double): VertexConsumer = apply {
            position = Vector3d(x, y, z)
        }
        override fun normal(x: Float, y: Float, z: Float): VertexConsumer = apply {
            normal = Vector3f(x, y, z)
        }
        override fun color(r: Int, g: Int, b: Int, a: Int): VertexConsumer = this
        override fun uv(u: Float, v: Float): VertexConsumer = this
        override fun overlayCoords(u: Int, v: Int): VertexConsumer = this
        override fun uv2(u: Int, v: Int): VertexConsumer = this
        override fun endVertex() { vertices.add(Vertex(position, normal)) }
        override fun defaultColor(r: Int, g: Int, b: Int, a: Int) {}
        override fun unsetDefaultColor() {}
    }
}
