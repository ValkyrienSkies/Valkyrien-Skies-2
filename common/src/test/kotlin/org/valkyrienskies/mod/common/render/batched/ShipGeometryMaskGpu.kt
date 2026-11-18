package org.valkyrienskies.mod.common.render.batched

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import io.mockk.every
import io.mockk.mockk
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import org.joml.*
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33C.*
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.api.render.*
import org.valkyrienskies.mod.client.render.ShipMaterialStack
import org.valkyrienskies.mod.client.render.ShipMaterialTable
import org.valkyrienskies.mod.client.render.ShipMaskTerrain
import org.valkyrienskies.mod.common.render.light.ShipShadowMap
import org.valkyrienskies.mod.common.render.light.VsDynamicLight

internal object ShipGeometryMaskGpu {
    private const val claim = 28_000_000

    fun check(shader: ShaderInstance, effect: ShipMaterial, tint: ShipMaterial) {
        val scene = glGenFramebuffers()
        val depth = glGenRenderbuffers()
        val color = glGenRenderbuffers()
        val atlas = glGenTextures()
        val depthTexture = glGenTextures()
        val previousMaterialTexture = glGenTextures()
        val shadowTexture = glGenTextures()
        val comparisonTexture = glGenTextures()
        val comparisonSampler = glGenSamplers()
        val outer = ShipMaskTarget.acquire()
        val inner = ShipMaskTarget.acquire()
        val opaque = arrayOfNulls<VertexBuffer>(ShipSectionMesh.CHUNK_LAYERS.size)
        opaque[ShipSectionMesh.layerIndex(RenderType.solid())] = strip(0, 0.375f, 0.375f)
        opaque[ShipSectionMesh.layerIndex(RenderType.cutoutMipped())] = strip(1, 0.375f, 0.625f)
        opaque[ShipSectionMesh.layerIndex(RenderType.cutout())] = strip(2, 0.125f, 0.375f)
        val section = ShipSectionMesh(claim, 0, 0)
        section.setBuffer(ShipSectionMesh.layerIndex(RenderType.translucent()), strip(3, 0.875f, 0.875f))
        val sections = Long2ObjectOpenHashMap<ShipSectionMesh>().apply { put(0L, section) }
        val mesh = ShipMesh(opaque, sections, claim, 0, 0)
        try {
            glBindFramebuffer(GL_FRAMEBUFFER, scene)
            glBindRenderbuffer(GL_RENDERBUFFER, depth)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, 8, 4)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depth)
            glBindRenderbuffer(GL_RENDERBUFFER, color)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, 8, 4)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, color)
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER))
            glViewport(0, 0, 8, 4)
            glDisable(GL_SCISSOR_TEST)
            glDepthMask(true)
            glClearDepth(0.5)
            glClearColor(0f, 0f, 1f, 1f)
            glClearStencil(3)
            glClear(GL_DEPTH_BUFFER_BIT or GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
            glEnable(GL_SCISSOR_TEST)
            glScissor(0, 0, 1, 4)
            glClearDepth(0.25)
            glClear(GL_DEPTH_BUFFER_BIT)
            glDisable(GL_SCISSOR_TEST)
            glEnable(GL_STENCIL_TEST)
            glStencilFunc(GL_EQUAL, 3, 0xff)
            glStencilOp(GL_ZERO, GL_ZERO, GL_ZERO)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, atlas)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 4, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                intArrayOf(0x00ffffff, 0x40ffffff, -1, 0x80ffffff.toInt()))
            RenderSystem.setShaderTexture(0, atlas)
            RenderSystem.setShaderTexture(2, atlas)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            val ship = mockk<ClientShip> {
                every { renderTransform.position } returns Vector3d()
                every { renderTransform.positionInModel } returns Vector3d(claim.toDouble(), 0.0, 0.0)
                every { renderTransform.shipToWorld } returns Matrix4d().translation(-claim.toDouble(), 0.0, 0.0)
                every { renderTransform.rotation } returns Quaterniond()
                every { renderTransform.scaling } returns Vector3d(1.0)
                every { chunkClaim.xStart } returns claim / 16
                every { chunkClaim.zStart } returns 0
            }
            val projection = Matrix4f().ortho(0f, 8f, 0f, 4f, -1f, 1f)
            fun mask(target: ShipMaskTarget, selection: ShipMaterialStack?, cameraX: Double = 0.0): ShipGeometryMask {
                val terrain = ShipMaskTerrain(PoseStack(), projection, cameraX, 0.0, 0.0)
                val viewport = ShipMaskTarget.Rect.read(GL_VIEWPORT)
                val bounds = ShipMaskTarget.bounds(terrain, AABBd(0.0, 0.0, 0.0, 8.0, 4.0, 0.0), viewport)
                return requireNotNull(target.render(viewport, bounds) { ShipMaskDraw.draw(shader, terrain, ship, mesh, selection) })
            }
            glActiveTexture(GL_TEXTURE0 + ShipShadowMap.TEXTURE_UNIT)
            glBindTexture(GL_TEXTURE_2D_ARRAY, shadowTexture)
            glActiveTexture(GL_TEXTURE0 + ShipShadowMap.COMPARISON_TEXTURE_UNIT)
            glBindTexture(GL_TEXTURE_2D_ARRAY, comparisonTexture)
            glBindSampler(ShipShadowMap.COMPARISON_TEXTURE_UNIT, comparisonSampler)
            glActiveTexture(GL_TEXTURE0)
            val first = mask(outer, null)
            glActiveTexture(GL_TEXTURE0 + ShipShadowMap.TEXTURE_UNIT)
            assertEquals(shadowTexture, glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY))
            glActiveTexture(GL_TEXTURE0 + ShipShadowMap.COMPARISON_TEXTURE_UNIT)
            assertEquals(comparisonTexture, glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY))
            assertEquals(comparisonSampler, glGetInteger(GL_SAMPLER_BINDING))
            glActiveTexture(GL_TEXTURE0)
            val original = pixels(first)
            assertEquals(listOf(0, 255, 255, 255, 255, 255, 255, 255), original.take(8))
            assertEquals(listOf(0, 0, 0, 0, 255, 255, 255, 255), original.subList(8, 16))
            assertEquals(listOf(0, 0, 0, 0, 255, 255, 255, 255), original.subList(16, 24))
            assertEquals(listOf(0, 128, 128, 128, 128, 128, 128, 128), original.takeLast(8))

            val dissolve = ShipMaterialTable().append(listOf(
                ShipMaterialSelection(effect, x = 0.5f, y = 0.5f),
                ShipMaterialSelection(tint, ShipMaterialPass.BLENDED, z = 1f, w = 0.5f)
            ))
            glActiveTexture(GL_TEXTURE5)
            glBindTexture(GL_TEXTURE_BUFFER, previousMaterialTexture)
            glActiveTexture(GL_TEXTURE0)
            val second = mask(inner, dissolve, 1.0)
            glActiveTexture(GL_TEXTURE5)
            assertEquals(previousMaterialTexture, glGetInteger(GL_TEXTURE_BINDING_BUFFER))
            glActiveTexture(GL_TEXTURE0)
            assertNotEquals(first.textureId, second.textureId)
            assertEquals(listOf(0, 16, 16, 0, 0, 16, 16, 0), pixels(second).take(8))
            assertEquals(original, pixels(first))
            glEnable(GL_SCISSOR_TEST)
            glScissor(5, 0, 1, 4)
            assertEquals(listOf(0, 0, 0, 0, 0, 255, 0, 0), pixels(mask(inner, null)).take(8))
            assertEquals(ShipMaskTarget.Rect(5, 0, 1, 4), ShipMaskTarget.Rect.read(GL_SCISSOR_BOX))
            assertEquals(scene, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING))
            assertTrue(glGetBoolean(GL_DEPTH_WRITEMASK))
            val scenePixel = BufferUtils.createByteBuffer(4)
            glReadPixels(3, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, scenePixel)
            assertEquals(255, scenePixel[2].toInt() and 255)
            glReadPixels(3, 0, 1, 1, GL_STENCIL_INDEX, GL_UNSIGNED_BYTE, scenePixel)
            assertEquals(3, scenePixel[0].toInt())
            val depths = FloatArray(2)
            glReadPixels(0, 0, 2, 1, GL_DEPTH_COMPONENT, GL_FLOAT, depths)
            assertArrayEquals(floatArrayOf(0.25f, 0.5f), depths, 0.00001f)
            glDisable(GL_STENCIL_TEST)
            glDisable(GL_SCISSOR_TEST)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, 0)
            val viewport = ShipMaskTarget.Rect.read(GL_VIEWPORT)
            assertNull(inner.render(viewport, viewport) { error("Mask drew without scene depth") })
            glBindTexture(GL_TEXTURE_2D, depthTexture)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, 8, 4, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0)
            glClearDepth(0.25)
            glClear(GL_DEPTH_BUFFER_BIT)
            assertTrue(pixels(mask(inner, null)).all { it == 0 })
            glClearDepth(1.0)
            glClear(GL_DEPTH_BUFFER_BIT)
            assertTrue(pixels(mask(inner, null)).take(8).all { it == 255 })
            assertEquals(GL_NO_ERROR, glGetError())
        } finally {
            outer.release()
            inner.release()
            ShipMaskTarget.clearPool()
            mesh.close()
            VsDynamicLight.deleteStorages()
            glDisable(GL_STENCIL_TEST)
            glDisable(GL_SCISSOR_TEST)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glDeleteFramebuffers(scene)
            glDeleteRenderbuffers(depth)
            glDeleteRenderbuffers(color)
            glDeleteTextures(atlas)
            glDeleteTextures(depthTexture)
            glDeleteTextures(previousMaterialTexture)
            glDeleteTextures(shadowTexture)
            glDeleteTextures(comparisonTexture)
            glDeleteSamplers(comparisonSampler)
        }
    }

    private fun strip(y: Int, leftUv: Float, rightUv: Float): VertexBuffer {
        val builder = BufferBuilder(256)
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)
        fun vertex(x: Int, dy: Int, uv: Float) {
            builder.vertex(x.toDouble(), (y + dy).toDouble(), 0.0).color(255, 255, 255, 255)
                .uv(uv, 0.5f).uv2(240, 240).normal(0f, 0f, 1f).endVertex()
        }
        for ((start, uv) in listOf(0 to leftUv, 4 to rightUv)) {
            vertex(start, 0, uv)
            vertex(start + 4, 0, uv)
            vertex(start + 4, 1, uv)
            vertex(start, 1, uv)
        }
        return VertexBuffer(VertexBuffer.Usage.STATIC).apply { bind(); upload(builder.end()) }
    }

    private fun pixels(mask: ShipGeometryMask): List<Int> {
        val previous = glGetInteger(GL_TEXTURE_BINDING_2D)
        val data = BufferUtils.createByteBuffer(mask.textureWidth * mask.textureHeight)
        glBindTexture(GL_TEXTURE_2D, mask.textureId)
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_BYTE, data)
        glBindTexture(GL_TEXTURE_2D, previous)
        return List(data.capacity()) { data[it].toInt() and 255 }
    }
}
