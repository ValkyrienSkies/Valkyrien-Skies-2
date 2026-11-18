package org.valkyrienskies.mod.common.render.batched

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.client.renderer.ShaderInstance
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
import java.util.Optional

/**
 * The foreground occlusion case for ship motion vectors.
 *
 * The motion attachment is bound only around the ship draws, so something drawn after the ship and
 * in front of it -- a block entity, a particle, the player's hand -- changes the scene's colour and
 * depth but leaves the ship's vector in place. A consumer can only reject that stale vector if the
 * ship shaders say what they wrote: `.b` marks a ship pixel and `.a` is the depth it was drawn at,
 * and a pixel whose scene depth is now nearer than `.a` has been covered.
 *
 * For each opaque layer, three pixels: a ship pixel that is then covered, a ship pixel that is not, and no ship at all.
 */
@EnabledIfSystemProperty(named = "vs.test.fluidGL", matches = "true")
class ShipMotionOcclusionTest {
    private val width = 3

    /** Normalised device x of the centre of pixel [i]. */
    private fun ndcX(i: Int) = 2f * (i + 0.5f) / width - 1f

    @Test
    fun aShipPixelCoveredAfterTheShipPassReadsAsCovered() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        assertTrue(glfwInit())
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        val window = glfwCreateWindow(8, 8, "Ship motion occlusion", 0L, 0L)
        assertNotEquals(0L, window)
        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread()
            val pack = mockk<PackResources>(relaxed = true)
            // Straight off the classpath, not through ShipMaterials: material registration is
            // global and closes once shaders are loaded through it, and a material another test
            // registered has no business in this shader.
            val provider = ResourceProvider { id ->
                Optional.ofNullable(javaClass.getResource("/assets/${id.namespace}/${id.path}"))
                    .map { url -> Resource(pack) { url.openStream() } }
            }

            val storage = ShipTransformStorage()
            val vao = glGenVertexArrays()
            val white = glGenTextures()
            val colour = texture(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE)
            val depth = texture(GL_DEPTH_COMPONENT32F, GL_DEPTH_COMPONENT, GL_FLOAT)
            val motion = texture(GL_RGBA16F, GL_RGBA, GL_FLOAT)
            val framebuffer = glGenFramebuffers()
            val occluder = occluderProgram()
            try {
                glBindVertexArray(vao)
                glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colour, 0)
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0)
                assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER))
                glViewport(0, 0, width, 1)
                glEnable(GL_DEPTH_TEST)
                glDepthFunc(GL_LESS)
                glDepthMask(true)
                glDisable(GL_BLEND)
                glClearColor(0f, 0f, 1f, 1f)
                glClearDepth(1.0)

                // The ship moved a tenth of the screen's half-width to the right since last frame.
                storage.beginFrame()
                storage.append(Matrix4f(), Matrix4f(), Matrix4f().translation(-0.1f, 0f, 0f))
                storage.upload()
                storage.bind(4)
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, white)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, intArrayOf(-1))

                // Every opaque layer's fragment shader writes the marker and depth.
                for (layer in listOf("solid", "cutout", "cutout_mipped")) {
                    glActiveTexture(GL_TEXTURE0)
                    glBindTexture(GL_TEXTURE_2D, white)
                    glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                    ShipMotionVectors.setTexture(motion)
                    assertTrue(ShipMotionVectors.begin(), "Motion attachment declined on a one-attachment framebuffer")
                    ShaderInstance(provider, "rendertype_ship_batched_$layer",
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
                        // Ship at depth 0.6 on pixels 0 and 1; pixel 2 has no ship.
                        for (i in 0..1) {
                            glUniform3f(loc("ChunkOffset"), ndcX(i), 0f, SHIP_NDC_Z)
                            glDrawArrays(GL_POINTS, 0, 1)
                        }
                        glUseProgram(0)
                    }
                    ShipMotionVectors.end()
                    assertEquals(GL_NO_ERROR, glGetError())

                    // Something that is not a ship, drawn afterwards, nearer, over pixel 0. Its shader
                    // also writes location 1, so a motion attachment left bound would be overwritten.
                    glUseProgram(occluder)
                    glUniform3f(glGetUniformLocation(occluder, "p"), ndcX(0), 0f, OCCLUDER_NDC_Z)
                    glDrawArrays(GL_POINTS, 0, 1)
                    glUseProgram(0)
                    assertEquals(GL_NO_ERROR, glGetError())

                    val vectors = BufferUtils.createFloatBuffer(4 * width)
                    glBindTexture(GL_TEXTURE_2D, motion)
                    glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_FLOAT, vectors)
                    val scene = BufferUtils.createFloatBuffer(width)
                    glReadPixels(0, 0, width, 1, GL_DEPTH_COMPONENT, GL_FLOAT, scene)
                    fun m(pixel: Int, channel: Int) = vectors[pixel * 4 + channel]
                    val shipDepth = SHIP_NDC_Z * 0.5f + 0.5f
                    val occluderDepth = OCCLUDER_NDC_Z * 0.5f + 0.5f

                    // The vector itself, on both ship pixels.
                    for (i in 0..1) {
                        assertEquals(0.1f, m(i, 0), 1e-3f, "$layer: motion x at pixel $i")
                        assertEquals(0f, m(i, 1), 1e-3f, "$layer: motion y at pixel $i")
                        assertEquals(1f, m(i, 2), "$layer: ship marker at pixel $i")
                        assertEquals(shipDepth, m(i, 3), 1e-3f, "$layer: recorded depth at pixel $i")
                    }

                    // Pixel 0: the ship's vector survived the occluder, which is the case at hand, and
                    // the scene is now nearer than the depth the vector was written at -- covered.
                    assertEquals(occluderDepth, scene[0], 1e-5f)
                    assertTrue(scene[0] < m(0, 3) - COVERED, "$layer: covered ship pixel does not read as covered")

                    // Pixel 1: nothing in front, so the recorded depth is the scene's -- visible.
                    assertEquals(shipDepth, scene[1], 1e-5f)
                    assertTrue(scene[1] >= m(1, 3) - COVERED, "$layer: uncovered ship pixel reads as covered")

                    // Pixel 2: never a ship, still the zero it was cleared to.
                    for (c in 0..3) assertEquals(0f, m(2, c), "$layer: pixel 2 channel $c")
                    assertEquals(1f, scene[2], 1e-6f)
                }
            } finally {
                glBindFramebuffer(GL_FRAMEBUFFER, 0)
                ShipMotionVectors.setTexture(0)
                glDeleteProgram(occluder)
                glDeleteFramebuffers(framebuffer)
                glDeleteTextures(intArrayOf(colour, depth, motion, white))
                storage.delete()
                glDeleteVertexArrays(vao)
            }
        } finally {
            glfwDestroyWindow(window)
            GL.setCapabilities(null)
            glfwTerminate()
        }
    }

    private fun texture(internal: Int, format: Int, type: Int): Int {
        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexImage2D(GL_TEXTURE_2D, 0, internal, width, 1, 0, format, type, null as java.nio.ByteBuffer?)
        return id
    }

    private fun occluderProgram(): Int {
        fun compile(type: Int, source: String): Int {
            val shader = glCreateShader(type)
            glShaderSource(shader, source)
            glCompileShader(shader)
            assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader))
            return shader
        }
        val vertex = compile(GL_VERTEX_SHADER, """
            #version 330 core
            uniform vec3 p;
            void main() { gl_Position = vec4(p, 1.0); }
        """.trimIndent())
        val fragment = compile(GL_FRAGMENT_SHADER, """
            #version 330 core
            layout(location = 0) out vec4 colour;
            layout(location = 1) out vec4 motion;
            void main() { colour = vec4(1.0, 0.0, 0.0, 1.0); motion = vec4(9.0); }
        """.trimIndent())
        val program = glCreateProgram()
        glAttachShader(program, vertex)
        glAttachShader(program, fragment)
        glLinkProgram(program)
        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program))
        glDeleteShader(vertex)
        glDeleteShader(fragment)
        return program
    }

    private companion object {
        const val SHIP_NDC_Z = 0.2f
        const val OCCLUDER_NDC_Z = -0.6f

        /** Half-float depth is good to about 5e-4 here; anything nearer than this is in front. */
        const val COVERED = 0.002f
    }
}
