package org.valkyrienskies.mod.common.render.batched

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceProvider
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.valkyrienskies.mod.api.render.ShipMaterialSelection
import org.valkyrienskies.mod.client.render.ShipMaterials
import java.util.Optional

@EnabledIfSystemProperty(named = "vs.test.fluidGL", matches = "true")
class ShipMaterialShaderTest {
    @Test
    fun batchedMaterialsDiscardBlendAndReload() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        assertTrue(glfwInit())
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        val window = glfwCreateWindow(8, 8, "Ship materials", 0L, 0L)
        assertNotEquals(0L, window)
        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread()
            val effect = ShipMaterials.register(ResourceLocation("test:dissolve"))
            var body = "if (color.r < 0.01 || fract(shipPos.x * 0.25) < params.x) discard; color.gb = vec2(0); color.a *= params.y;"
            val pack = mockk<PackResources>(relaxed = true)
            val provider = ResourceProvider { id ->
                if (id == ResourceLocation("test:shaders/include/ship_material/dissolve.glsl")) {
                    Optional.of(Resource(pack) { body.byteInputStream() })
                } else {
                    Optional.ofNullable(javaClass.getResource("/assets/${id.namespace}/${id.path}"))
                        .map { url -> Resource(pack) { url.openStream() } }
                }
            }
            val storage = ShipTransformStorage()
            val vao = glGenVertexArrays()
            val white = glGenTextures()
            try {
                glBindVertexArray(vao)
                storage.beginFrame()
                // The transform texture stores current model-view, camera-relative, and
                // previous model-view matrices. The material test has no motion, so previous
                // equals current.
                val modelView = Matrix4f()
                storage.append(modelView, Matrix4f(), Matrix4f(modelView))
                storage.upload()
                storage.bind(4)
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, white)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, intArrayOf(-1))
                glViewport(0, 0, 1, 1)
                glEnable(GL_DEPTH_TEST)
                glClearColor(0f, 0f, 1f, 1f)
                for (layer in listOf("solid", "cutout", "cutout_mipped", "translucent")) {
                    ShaderInstance(ShipMaterials.shaderResources(provider), "rendertype_ship_batched_$layer",
                        DefaultVertexFormat.BLOCK).use { shader ->
                        val program = shader.id
                        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program))
                        glUseProgram(program)
                        fun loc(name: String) = glGetUniformLocation(program, name)
                        glUniform1i(loc("ShipTransforms"), 4)
                        glUniform1i(loc("u_VsLightSections"), 5)
                        glUniform1i(loc("u_VsLightLut"), 5)
                        glUniform1i(loc("u_VsShipEmitters"), 4)
                        glUniform1i(loc("u_VsShipShadowMap"), 14)
                        glUniform1i(loc("u_VsShipShadowCompare"), 15)
                        glUniformMatrix4fv(loc("ProjMat"), false, Matrix4f().get(FloatArray(16)))
                        glUniform1f(loc("FogStart"), 10f)
                        glUniform1f(loc("FogEnd"), 20f)
                        glUniform4f(loc("ColorModulator"), 1f, 1f, 1f, 1f)
                        glVertexAttrib4f(glGetAttribLocation(program, "Color"), 1f, 1f, 1f, 0f)
                        glVertexAttribI2i(glGetAttribLocation(program, "UV2"), 240, 240)
                        val uniforms = requireNotNull(ShipMaterialUniforms.forShader(shader))
                        val selection = ShipMaterialSelection(effect, x = 0.5f, y = 1f)
                        glDepthMask(true)
                        glDisable(GL_BLEND)
                        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                        uniforms.select(selection)
                        uniforms.origin(1, 0, 0)
                        glDrawArrays(GL_POINTS, 0, 1)
                        assertPixel(0, 0, 255, 1f)
                        uniforms.origin(3, 0, 0)
                        glDrawArrays(GL_POINTS, 0, 1)
                        assertPixel(255, 0, 0, 0.5f)
                        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                        uniforms.select(selection.copy(y = 0.5f))
                        glDepthMask(false)
                        glEnable(GL_BLEND)
                        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                        glDrawArrays(GL_POINTS, 0, 1)
                        assertPixel(128, 0, 127, 1f)
                        glDepthMask(true)
                        glDisable(GL_BLEND)
                        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                        glUniform4f(loc("ColorModulator"), 1f, 1f, 1f, 0.25f)
                        uniforms.setDeferredLayer(RenderType.cutoutMipped())
                        glDrawArrays(GL_POINTS, 0, 1)
                        assertPixel(0, 0, 255, 1f)
                        uniforms.select(null)
                        glUniform4f(loc("ColorModulator"), 1f, 1f, 1f, 1f)
                        glDrawArrays(GL_POINTS, 0, 1)
                        assertPixel(255, 255, 255, 0.5f)
                        glUseProgram(0)
                    }
                }
                ShaderInstance(ShipMaterials.shaderResources(provider), "rendertype_ship_batched_mask",
                    DefaultVertexFormat.BLOCK).use { ShipGeometryMaskGpu.check(it, effect) }
                ShaderInstance(provider, "rendertype_ship_batched_mask", DefaultVertexFormat.BLOCK).use {
                    assertEquals(-1, glGetUniformLocation(it.id, "vs_MaterialId"))
                    assertTrue(glGetUniformLocation(it.id, "vs_MaskCutout") >= 0)
                }
                body = "color.rgb = params.xyz;"
                ShaderInstance(ShipMaterials.shaderResources(provider), "rendertype_ship_batched_solid",
                    DefaultVertexFormat.BLOCK).use { shader ->
                    assertEquals(-1, glGetUniformLocation(shader.id, "vs_MaterialOrigin"))
                    assertTrue(glGetUniformLocation(shader.id, "vs_MaterialParams") >= 0)
                }
            } finally {
                storage.delete()
                glDeleteTextures(white)
                glDeleteVertexArrays(vao)
            }
        } finally {
            glfwDestroyWindow(window)
            GL.setCapabilities(null)
            glfwTerminate()
        }
    }

    private fun assertPixel(r: Int, g: Int, b: Int, expectedDepth: Float) {
        assertEquals(GL_NO_ERROR, glGetError())
        val rgba = BufferUtils.createByteBuffer(4)
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, rgba)
        for ((i, expected) in listOf(r, g, b).withIndex()) {
            assertEquals(expected.toDouble(), (rgba[i].toInt() and 255).toDouble(), 1.0)
        }
        val depth = FloatArray(1)
        glReadPixels(0, 0, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, depth)
        assertEquals(expectedDepth, depth[0], 0.00001f)
    }
}
