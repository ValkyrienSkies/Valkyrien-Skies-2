package org.valkyrienskies.mod.common.fluid

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL21.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.GL_TEXTURE_BUFFER
import org.lwjgl.opengl.GL31.GL_TEXTURE_BINDING_BUFFER
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidGameplaySnapshot
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.config.VSGameConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import org.joml.Vector3d
import org.joml.primitives.AABBd
import java.util.function.Function

/** Run with -PfluidGlTests=true on a machine with an OpenGL 3.2 driver. No visible window. */
@EnabledIfSystemProperty(named = "vs.test.fluidGL", matches = "true")
class FluidOcclusionShaderTest {
    @Test
    fun `production shader compiles and uploaded masks preserve dry air on the GPU`() {
        assertTrue(glfwInit(), "GLFW initialization failed")
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        val window = glfwCreateWindow(16, 16, "Fluid mask test", 0, 0)
        assertNotEquals(0L, window, "Could not create hidden GL context")
        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            println("Fluid shader GPU: ${glGetString(GL_RENDERER)}, ${glGetString(GL_VERSION)}")
            val vertexSource = resource("core/rendertype_translucent.vsh")
            val fragmentSource = resource("core/rendertype_translucent.fsh")
            val production = program(expandImports(vertexSource), expandImports(fragmentSource))
            // All nine slots must survive linking, including the new per-mask metadata.
            for (slot in 0..8) {
                assertTrue(glGetUniformLocation(production, "VsFluidOcclusionPartialCount$slot") >= 0)
            }
            verifyRendererUploadAndCleanup(production)
            glDeleteProgram(production)
            verifyOverflowRendering(fragmentSource)

            // Execute the exact production decoder functions with controlled fragment positions.
            val constants = fragmentSource.substring(
                fragmentSource.indexOf("const int VS_FLUID_MASK_WIDTH_SHIFT"),
                fragmentSource.indexOf("bool vs_fluidUvWithin"),
            )
            val decoder = fragmentSource.substring(
                fragmentSource.indexOf("uint vs_fetchFluidMaskWord"), fragmentSource.indexOf("void main()"),
            )
            val harness = program("""
                #version 150
                void main() {
                    vec2 p = gl_VertexID == 0 ? vec2(-1.0, -1.0) :
                        (gl_VertexID == 1 ? vec2(3.0, -1.0) : vec2(-1.0, 3.0));
                    gl_Position = vec4(p, 0.0, 1.0);
                }
            """.trimIndent(), """
                #version 150
                uniform sampler2D Mask;
                uniform vec3 TestPoint;
                uniform vec3 Grid;
                uniform vec3 CameraLocal;
                uniform mat4 WorldToShip;
                uniform int PartialCount;
                out vec4 Color;
                #define valkyrienair_CamRelPos TestPoint
                $constants
                $decoder
                void main() {
                    bool dry = vs_shouldCullFluid(Mask, Grid, CameraLocal, WorldToShip, PartialCount);
                    Color = dry ? vec4(1.0, 0.0, 0.0, 1.0) : vec4(0.0, 0.0, 0.0, 1.0);
                }
            """.trimIndent())
            val texture = glGenTextures()
            val vao = glGenVertexArrays()
            try {
                glUseProgram(harness)
                glBindVertexArray(vao)
                glBindTexture(GL_TEXTURE_2D, texture)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
                glUniform1i(glGetUniformLocation(harness, "Mask"), 0)
                glUniform1i(glGetUniformLocation(harness, "VsFluidOverflowMasks"), FluidOcclusionOverflow.MASK_UNIT)
                glUniform1i(glGetUniformLocation(harness, "VsFluidOverflowDescriptors"), FluidOcclusionOverflow.DESCRIPTOR_UNIT)
                glUniform1i(glGetUniformLocation(harness, "VsFluidOverflowCount"), 0)
                glUniform3f(glGetUniformLocation(harness, "CameraLocal"), 0f, 0f, 0f)
                setMatrix(harness, Matrix4f())
                glViewport(0, 0, 1, 1)

                val topology = maskTopology(*(0..256).map { Triple(it, 0, 0) }.toTypedArray())
                val flooding = maskFlooding(*(1..255).map {
                    VsiFluidFloodedVoxel(it, 0, 0, 17, it, 0)
                }.toTypedArray())
                val mixed = FluidOcclusionMaskData.build(topology, flooding)!!
                upload(harness, mixed)
                assertPixel(harness, 0.5f, 0.5f, 0.5f, true)
                assertPixel(harness, 255.5f, 0.999f, 0.5f, false)
                for (fill in 1..254) {
                    val height = fill / 255f
                    assertPixel(harness, fill + 0.5f, height - 0.001f, 0.5f, false)
                    assertPixel(harness, fill + 0.5f, height + 0.001f, 0.5f, true)
                }
                assertPixel(harness, -0.1f, 0.5f, 0.5f, false)
                assertPixel(harness, 257f, 0.5f, 0.5f, false)

                // Reuse the same texture/program for a dry-only update, then restore mixed data.
                val binary = FluidOcclusionMaskData.build(topology, null)!!
                upload(harness, binary)
                assertPixel(harness, 255.5f, 0.5f, 0.5f, true)
                upload(harness, mixed)
                assertPixel(harness, 255.5f, 0.5f, 0.5f, false)

                // Rotation maps world X to local Y. The height test must operate in local space.
                setMatrix(harness, Matrix4f().rotationZ((Math.PI / 2).toFloat()))
                assertPixel(harness, 0.25f, -128.5f, 0.5f, false)
                assertPixel(harness, 0.75f, -128.5f, 0.5f, true)
                setMatrix(harness, Matrix4f())

                upload(harness, FluidOcclusionMaskDataTest.largeSparseMask())
                assertPixel(harness, 0.5f, 0.5f, 128.5f, true)
                assertPixel(harness, 255.5f, 255.5f, 255.5f, false)
                assertPixel(harness, 255.5f, 255.999f, 255.5f, true)
                assertPixel(harness, 254.5f, 255.5f, 255.5f, false)
                assertEquals(GL_NO_ERROR, glGetError())
            } finally {
                glDeleteTextures(texture)
                glDeleteVertexArrays(vao)
                glDeleteProgram(harness)
            }
        } finally {
            GL.setCapabilities(null)
            glfwMakeContextCurrent(0)
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }

    private fun upload(program: Int, data: FluidOcclusionMaskData) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, FluidOcclusionMaskData.TEXTURE_WIDTH,
            data.textureHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, data.bytes)
        glUniform3f(glGetUniformLocation(program, "Grid"), data.sizeX.toFloat(), data.sizeY.toFloat(), data.sizeZ.toFloat())
        glUniform1i(glGetUniformLocation(program, "PartialCount"), data.partialCount)
    }

    private fun verifyRendererUploadAndCleanup(program: Int) {
        if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread()
        val type = FluidOcclusionRenderer::class.java
        val masksField = type.getDeclaredField("SHIP_MASKS").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val masks = masksField.get(null) as Long2ObjectOpenHashMap<Any>
        val maskType = Class.forName("org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer\$ShipMask")
        val mask = maskType.getDeclaredConstructor(Long::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(7L)
        val update = maskType.declaredMethods.first { it.name == "update" }.apply { isAccessible = true }
        val textureField = maskType.getDeclaredField("textureId").apply { isAccessible = true }
        val handles = type.getDeclaredMethod("handles", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(null, program)
        val upload = type.declaredMethods.first { it.name == "uploadShipSlot" }.apply { isAccessible = true }
        val ship = mockk<ClientShip>(relaxed = true)
        every { ship.renderTransform.worldToShip } returns Matrix4d()
        val topology = maskTopology(Triple(0, 0, 0), Triple(1, 0, 0))
        val partial = maskFlooding(VsiFluidFloodedVoxel(1, 0, 0, 17, 128, 0))
        masks.put(7L, mask)
        glUseProgram(program)
        try {
            assertEquals(true, update.invoke(mask, topology, partial))
            upload.invoke(null, handles, 3, ship, mask, 0.0, 0.0, 0.0)
            assertEquals(1, glGetUniformi(program, glGetUniformLocation(program, "VsFluidOcclusionPartialCount3")))
            assertEquals(5, glGetUniformi(program, glGetUniformLocation(program, "VsFluidOcclusionMask3")))
            assertEquals(textureField.getInt(mask), glGetInteger(GL_TEXTURE_BINDING_2D))
            val bytes = BufferUtils.createByteBuffer(FluidOcclusionMaskData.TEXTURE_WIDTH * 4)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes)
            val expected = FluidOcclusionMaskData.build(topology, partial)!!.bytes
            for (index in 0 until expected.capacity()) assertEquals(expected.get(index), bytes.get(index))

            // A sentinel GPU edit detects a redundant upload without mocking the GL driver.
            bytes.put(0, (bytes.get(0).toInt() xor 127).toByte())
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, FluidOcclusionMaskData.TEXTURE_WIDTH, 1,
                GL_RGBA, GL_UNSIGNED_BYTE, bytes)
            val pressureOnly = FloodedFluidClientCache.CachedSnapshot(
                floodingSnapshot(VsiFluidFloodedVoxel(1, 0, 0, 19, 128, 100)).copy(streamSequence = 5))
            assertEquals(true, update.invoke(mask, topology, pressureOnly))
            val readback = BufferUtils.createByteBuffer(bytes.capacity())
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, readback)
            assertEquals(bytes, readback, "equal mask content must not upload again")

            // A lost texture must still recreate and upload the unchanged CPU mask.
            glDeleteTextures(textureField.getInt(mask))
            assertEquals(true, update.invoke(mask, topology, pressureOnly))
            GlStateManager._bindTexture(textureField.getInt(mask))
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, readback)
            assertEquals(expected, readback, "resource loss must restore the CPU mask")

            val dry = FloodedFluidClientCache.CachedSnapshot(floodingSnapshot().copy(streamSequence = 6))
            assertEquals(true, update.invoke(mask, topology, dry))
            upload.invoke(null, handles, 3, ship, mask, 0.0, 0.0, 0.0)
            assertEquals(0, glGetUniformi(program, glGetUniformLocation(program, "VsFluidOcclusionPartialCount3")))
            val texture = textureField.getInt(mask)
            assertTrue(glIsTexture(texture))
            FluidOcclusionRenderer.onProgramClosed(program)
            assertFalse(glIsTexture(texture))
            assertTrue(masks.isEmpty())
        } finally {
            FluidOcclusionRenderer.clear()
            GlStateManager._activeTexture(GL_TEXTURE0)
            GlStateManager._bindTexture(0)
        }
    }

    private fun setMatrix(program: Int, matrix: Matrix4f) {
        val buffer = BufferUtils.createFloatBuffer(16)
        matrix.get(buffer)
        glUniformMatrix4fv(glGetUniformLocation(program, "WorldToShip"), false, buffer)
    }

    private fun verifyOverflowRendering(fragment: String) {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val vertex = """
            #version 150
            uniform vec3 TestPoint;
            out float vertexDistance;
            out vec4 vertexColor;
            out vec2 texCoord0;
            out vec4 normal;
            out vec3 valkyrienair_CamRelPos;
            out vec2 v_VsLightCoordRaw;
            out vec3 v_VsWorldNormal;
            void main() {
                vec2 p = gl_VertexID == 0 ? vec2(-1.0, -1.0) :
                    (gl_VertexID == 1 ? vec2(3.0, -1.0) : vec2(-1.0, 3.0));
                gl_Position = vec4(p, 0.0, 1.0);
                vertexDistance = 0.0; vertexColor = vec4(1.0); texCoord0 = vec2(0.5);
                normal = vec4(0.0, 1.0, 0.0, 0.0); v_VsWorldNormal = normal.xyz;
                valkyrienair_CamRelPos = TestPoint; v_VsLightCoordRaw = vec2(0.5);
            }
        """.trimIndent()
        val shader = program(vertex, expandImports(fragment))
        val level = mockk<ClientLevel>()
        val world = mockk<VsiClientShipWorld>(relaxed = true)
        val minecraft = mockk<Minecraft>()
        val sprite = mockk<TextureAtlasSprite>()
        val ships = mutableListOf<ClientShip>()
        val snapshots = mutableMapOf<Long, VsiFluidGameplaySnapshot>()
        val poses = mutableMapOf<Long, Matrix4d>()
        val queries = mutableMapOf<Long, Vector3d>()
        val oldEnabled = VSGameConfig.CLIENT.renderFluidOcclusion
        val white = glGenTextures()
        val vao = glGenVertexArrays()
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        mockkStatic(Minecraft::class)
        every { level.shipObjectWorld } returns world
        every { Minecraft.getInstance() } returns minecraft
        every { minecraft.getTextureAtlas(any()) } returns Function { sprite }
        every { sprite.u0 } returns 0f; every { sprite.v0 } returns 0f
        every { sprite.u1 } returns 1f; every { sprite.v1 } returns 1f
        every { world.loadedShips.iterator() } answers { ships.iterator() }
        every { world.getFluidGameplaySnapshot(any()) } answers { snapshots[firstArg()] }
        fun add(id: Long, transform: Matrix4d, fill: Int = 0) {
            val ship = mockk<ClientShip>(relaxed = true)
            every { ship.id } returns id
            every { ship.renderTransform.shipToWorld } answers { poses.getValue(id) }
            every { ship.renderTransform.worldToShip } answers { Matrix4d(poses.getValue(id)).invert() }
            every { ship.renderAABB } answers {
                val p = poses.getValue(id).transformPosition(0.5, 0.5, 0.5, Vector3d())
                AABBd(p.x - 1, p.y - 1, p.z - 1, p.x + 1, p.y + 1, p.z + 1)
            }
            poses[id] = transform
            queries[id] = transform.transformPosition(0.5, 0.75, 0.5, Vector3d())
            ships += ship
            snapshots[id] = gameplaySnapshot(topologySnapshot().copy(bodyId = id), floodingSnapshot(
                *if (fill > 0) arrayOf(VsiFluidFloodedVoxel(0, 0, 0, 17, fill, 0)) else emptyArray()
            ).copy(bodyId = id))
        }
        fun bindProgram(p: Int) {
            glUseProgram(p)
            glUniform1i(glGetUniformLocation(p, "Sampler0"), 0)
            glUniform1i(glGetUniformLocation(p, "Sampler2"), 1)
            glUniform1i(glGetUniformLocation(p, "u_VsShipEmitters"), 12)
            glUniform1i(glGetUniformLocation(p, "u_VsShipGlowEnabled"), 0)
            glUniform4f(glGetUniformLocation(p, "ColorModulator"), 1f, 1f, 1f, 1f)
            glUniform1f(glGetUniformLocation(p, "FogStart"), 1f)
            glUniform1f(glGetUniformLocation(p, "FogEnd"), 2f)
            FluidOcclusionRenderer.setupForProgram(p, level, 0.0, 0.0, 0.0)
        }
        fun pixel(p: Int, point: Vector3d, dry: Boolean) {
            glUniform3f(glGetUniformLocation(p, "TestPoint"), point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
            glClearColor(0f, 0f, 1f, 1f); glClear(GL_COLOR_BUFFER_BIT)
            glDrawArrays(GL_TRIANGLES, 0, 3)
            val bytes = BufferUtils.createByteBuffer(4)
            glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, bytes)
            assertEquals(if (dry) 0 else 255, bytes[0].toInt() and 255, "full production shader at $point")
            assertEquals(GL_NO_ERROR, glGetError())
        }
        try {
            VSGameConfig.CLIENT.renderFluidOcclusion = true
            FluidOcclusionRenderer.clear()
            FloodedFluidClientCache.clear(); FluidTopologyClientCache.clear()
            glBindVertexArray(vao); glViewport(0, 0, 1, 1)
            GlStateManager._activeTexture(GL_TEXTURE0)
            GlStateManager._bindTexture(white)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
            val whitePixel = BufferUtils.createByteBuffer(4).put(byteArrayOf(-1, -1, -1, -1)).flip()
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, whitePixel)
            // Nine closer hulls are behind the sampled scene. They must not consume all masking capacity.
            for (id in 1L..9L) add(id, Matrix4d().translation(-id.toDouble() - 2, 0.0, 0.0))
            for (id in 10L..40L) add(id, Matrix4d().translation(id * 3.0, 0.0, 0.0), if (id == 40L) 128 else 0)
            bindProgram(shader)
            assertEquals(31, glGetUniformi(shader, glGetUniformLocation(shader, "VsFluidOverflowCount")))
            for (point in queries.values) pixel(shader, point, true)
            pixel(shader, Vector3d(120.5, 0.25, 0.5), false)
            pixel(shader, Vector3d(9000.0, 0.75, 0.5), false)

            // Optional independent before/after reproduction using the exact archived production shader.
            System.getenv("FLUID_MASK_BASELINE_SHADER")?.let { source ->
                val baseline = program(vertex, expandImports(java.io.File(source).readText()))
                try {
                    bindProgram(baseline)
                    pixel(baseline, queries.getValue(40), false)
                    println("Archived nine-slot shader reproduced visible world water in hull40")
                    if (System.getenv("FLUID_MASK_BENCHMARK") == "true") {
                        val overflowShips = ships.filter { it.id > 9 }
                        ships.removeAll(overflowShips.toSet())
                        try {
                            benchmarkDirectShaders(baseline, shader, ::bindProgram,
                                listOf(queries.getValue(1), queries.getValue(9), Vector3d(500.0, 0.75, 0.5)))
                        } finally { ships.addAll(overflowShips) }
                    }
                } finally {
                    FluidOcclusionRenderer.onProgramClosed(baseline); glDeleteProgram(baseline)
                }
                bindProgram(shader)
                pixel(shader, queries.getValue(40), true)
            }

            // Pose-only changes must update descriptors without depending on new fluid sequences.
            poses[40] = Matrix4d().translation(160.0, 3.0, 7.0).rotateXYZ(0.2, 0.6, 0.4).scale(0.5, 2.0, 1.5)
            bindProgram(shader)
            pixel(shader, poses.getValue(40).transformPosition(0.5, 0.25, 0.5, Vector3d()), false)
            pixel(shader, poses.getValue(40).transformPosition(0.5, 0.75, 0.5, Vector3d()), true)
            // Fill-only change, then removal and reassignment of the direct slots.
            snapshots[40] = gameplaySnapshot(topologySnapshot().copy(bodyId = 40),
                floodingSnapshot().copy(bodyId = 40, streamSequence = 5))
            bindProgram(shader)
            pixel(shader, poses.getValue(40).transformPosition(0.5, 0.25, 0.5, Vector3d()), true)
            ships.removeIf { it.id <= 9 || it.id == 20L }
            bindProgram(shader)
            pixel(shader, queries.getValue(20), false)
            pixel(shader, queries.getValue(19), true)

            for (id in 41L..310L) add(id, Matrix4d().translation(id * 3.0, 0.0, 0.0))
            // A sparse16M-cell mask spans pages and contains an unsigned high-index partial record.
            add(1000, Matrix4d().translation(10000.0, 0.0, 0.0))
            snapshots[1000] = gameplaySnapshot(topologySnapshot().copy(bodyId = 1000, totalDomainVoxelCount = 2, voxels = listOf(
                VsiFluidTopologyVoxel(0, 0, 0, 1), VsiFluidTopologyVoxel(255, 255, 255, 1))),
                floodingSnapshot(VsiFluidFloodedVoxel(255, 255, 255, 17, 128, 0)).copy(bodyId = 1000))
            bindProgram(shader)
            assertEquals(ships.size - 9, glGetUniformi(shader, glGetUniformLocation(shader, "VsFluidOverflowCount")))
            pixel(shader, queries.getValue(310), true)
            pixel(shader, Vector3d(10255.5, 255.25, 255.5), false)
            pixel(shader, Vector3d(10255.5, 255.75, 255.5), true)
            pixel(shader, Vector3d(10254.5, 255.75, 255.5), false)
            pixel(shader, Vector3d(10000.5, 0.5, 0.5), true)

            val overflowField = FluidOcclusionRenderer::class.java.getDeclaredField("OVERFLOW").apply { isAccessible = true }
            val overflow = overflowField.get(null)
            val textureFields = listOf("maskTexture", "descriptorTexture").map {
                FluidOcclusionOverflow::class.java.getDeclaredField(it).apply { isAccessible = true }
            }
            val ownedTextures = textureFields.map { it.getInt(overflow) }
            assertTrue(ownedTextures.all { glIsTexture(it) })
            val uploadedField = FluidOcclusionOverflow::class.java.getDeclaredField("uploaded").apply { isAccessible = true }
            val layoutField = FluidOcclusionOverflow::class.java.getDeclaredField("layout").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val uploaded = uploadedField.get(overflow) as Array<FluidOcclusionMaskData>
            verifyAtlasBytes(ownedTextures[0], layoutField.get(overflow) as FluidOcclusionOverflow.Layout, uploaded)
            for (texture in ownedTextures) glDeleteTextures(texture)
            bindProgram(shader)
            pixel(shader, Vector3d(10255.5, 255.75, 255.5), true)
            val rebuiltTextures = textureFields.map { it.getInt(overflow) }
            FluidOcclusionRenderer.onProgramClosed(shader)
            assertTrue(rebuiltTextures.none { glIsTexture(it) })
            bindProgram(shader)
            pixel(shader, queries.getValue(310), true)
            FluidOcclusionRenderer.clear()
            assertTrue(textureFields.all { it.getInt(overflow) == 0 })
            // Empty overflow and the disabled path must still set distinct sampler units.
            ships.clear()
            bindProgram(shader)
            pixel(shader, queries.getValue(310), false)
            FluidOcclusionRenderer.disableForProgram(shader)
            pixel(shader, queries.getValue(310), false)
            verifyOverflowState(ships = listOf(FluidOcclusionOverflow.Entry(
                mockk<ClientShip>(relaxed = true) {
                    every { renderTransform.shipToWorld } returns Matrix4d()
                    every { renderTransform.worldToShip } returns Matrix4d()
                }, FluidOcclusionMaskDataTest.largeSparseMask())))
            println("Overflow GPU checks passed: 301 ships, page-spanning byte readback, partial fills, transforms, membership, resources and GL state")
        } finally {
            FluidOcclusionRenderer.clear()
            FloodedFluidClientCache.clear(); FluidTopologyClientCache.clear()
            VSGameConfig.CLIENT.renderFluidOcclusion = oldEnabled
            unmockkStatic(Minecraft::class)
            unmockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
            GlStateManager._activeTexture(GL_TEXTURE0); GlStateManager._bindTexture(0)
            glDeleteTextures(white); glDeleteVertexArrays(vao); glDeleteProgram(shader)
        }
    }

    private fun verifyAtlasBytes(texture: Int, layout: FluidOcclusionOverflow.Layout, masks: Array<FluidOcclusionMaskData>) {
        val active = glGetInteger(GL_ACTIVE_TEXTURE)
        try {
            glActiveTexture(GL_TEXTURE0 + FluidOcclusionOverflow.MASK_UNIT)
            glBindTexture(GL_TEXTURE_2D_ARRAY, texture)
            val bytes = BufferUtils.createByteBuffer(layout.width() * layout.pageHeight() * layout.layers() * 4)
            glGetTexImage(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes)
            for ((index, data) in masks.withIndex()) {
                val actual = ByteArray(data.bytes.capacity())
                val expected = ByteArray(actual.size)
                bytes.position(layout.offsets()[index] * 4); bytes.get(actual)
                data.bytes.duplicate().get(expected)
                assertArrayEquals(expected, actual, "atlas mask $index")
            }
            assertEquals(GL_NO_ERROR, glGetError())
        } finally { glActiveTexture(active) }
    }

    private fun verifyOverflowState(ships: List<FluidOcclusionOverflow.Entry>) {
        val overflow = FluidOcclusionOverflow()
        val keys = intArrayOf(GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_SKIP_ROWS,
            GL_UNPACK_SKIP_PIXELS, GL_UNPACK_IMAGE_HEIGHT, GL_UNPACK_SKIP_IMAGES)
        val initial = keys.map { glGetInteger(it) }
        val values = intArrayOf(8, 17, 3, 5, 19, 2)
        val unpack = glGenBuffers()
        val sentinels = IntArray(6) { glGenTextures() }
        val units = intArrayOf(6, 7, 10, 11, 12, 13)
        val active = glGetInteger(GL_ACTIVE_TEXTURE)
        try {
            for (index in units.indices) {
                glActiveTexture(GL_TEXTURE0 + units[index])
                glBindTexture(GL_TEXTURE_BUFFER, sentinels[index])
            }
            glActiveTexture(GL_TEXTURE0 + 11)
            for (index in keys.indices) glPixelStorei(keys[index], values[index])
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack)
            glBufferData(GL_PIXEL_UNPACK_BUFFER, 128L, GL_STATIC_DRAW)
            overflow.update(ships, 1.0, 2.0, 3.0)
            val program = glGetInteger(GL_CURRENT_PROGRAM)
            overflow.bind(glGetUniformLocation(program, "VsFluidOverflowMasks"),
                glGetUniformLocation(program, "VsFluidOverflowDescriptors"),
                glGetUniformLocation(program, "VsFluidOverflowCount"), ships.size)
            assertEquals(GL_TEXTURE0 + 11, glGetInteger(GL_ACTIVE_TEXTURE))
            assertEquals(unpack, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING))
            for (index in keys.indices) assertEquals(values[index], glGetInteger(keys[index]))
            for (index in units.indices) {
                glActiveTexture(GL_TEXTURE0 + units[index])
                assertEquals(sentinels[index], glGetInteger(GL_TEXTURE_BINDING_BUFFER))
            }
            val textureField = FluidOcclusionOverflow::class.java.getDeclaredField("maskTexture").apply { isAccessible = true }
            val layoutField = FluidOcclusionOverflow::class.java.getDeclaredField("layout").apply { isAccessible = true }
            verifyAtlasBytes(textureField.getInt(overflow), layoutField.get(overflow) as FluidOcclusionOverflow.Layout,
                ships.map { it.data() }.toTypedArray())
            // A failed replacement must remain retryable even when the descriptor dimensions are unchanged.
            val descriptorField = FluidOcclusionOverflow::class.java.getDeclaredField("descriptorTexture").apply { isAccessible = true }
            glDeleteTextures(descriptorField.getInt(overflow))
            mockkStatic(org.lwjgl.opengl.GL11::class)
            try {
                every { glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH) } returns 0
                assertThrows(IllegalStateException::class.java) { overflow.update(ships, 1.0, 2.0, 3.0) }
                assertEquals(0, descriptorField.getInt(overflow))
            } finally { unmockkStatic(org.lwjgl.opengl.GL11::class) }
            overflow.update(ships, 1.0, 2.0, 3.0)
            assertTrue(glIsTexture(descriptorField.getInt(overflow)))
            assertEquals(unpack, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING))
            for (index in keys.indices) assertEquals(values[index], glGetInteger(keys[index]))
            assertEquals(GL_NO_ERROR, glGetError())
        } finally {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0); glDeleteBuffers(unpack)
            for (index in keys.indices) glPixelStorei(keys[index], initial[index])
            overflow.close()
            for (texture in sentinels) glDeleteTextures(texture)
            glActiveTexture(active)
        }
    }

    private fun benchmarkDirectShaders(before: Int, after: Int, bind: (Int) -> Unit, points: List<Vector3d>) {
        val framebuffer = glGenFramebuffers()
        val texture = glGenTextures()
        val active = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0 + 15)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, texture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 512, 512, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER))
        glBindTexture(GL_TEXTURE_2D, previousTexture); glActiveTexture(active)
        glViewport(0, 0, 512, 512)
        fun measure(program: Int, point: Vector3d): Double {
            bind(program)
            glUniform3f(glGetUniformLocation(program, "TestPoint"), point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
            glFinish()
            val start = System.nanoTime()
            repeat(4000) { glDrawArrays(GL_TRIANGLES, 0, 3) }
            glFinish()
            return (System.nanoTime() - start) / 1e6
        }
        try {
            for ((scenario, point) in points.withIndex()) {
                repeat(2) { measure(before, point); measure(after, point) }
                repeat(8) { pair ->
                    val order = if (pair % 2 == 0) listOf(before, after) else listOf(after, before)
                    for (program in order) println("Fluid GPU pair scenario=$scenario pair=$pair side=${if (program == before) "before" else "after"} ms=${measure(program, point)}")
                }
            }
            assertEquals(GL_NO_ERROR, glGetError())
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glDeleteFramebuffers(framebuffer); glDeleteTextures(texture)
            glViewport(0, 0, 1, 1)
        }
    }

    private fun assertPixel(program: Int, x: Float, y: Float, z: Float, dry: Boolean) {
        glUniform3f(glGetUniformLocation(program, "TestPoint"), x, y, z)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        val pixel = BufferUtils.createByteBuffer(4)
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel)
        assertEquals(if (dry) 255 else 0, pixel.get(0).toInt() and 255, "pixel at $x, $y, $z")
    }

    private fun resource(path: String): String = javaClass.getResourceAsStream("/assets/minecraft/shaders/$path")
        ?.bufferedReader()?.use { it.readText() } ?: error("Missing shader resource $path")

    private fun expandImports(source: String): String = Regex("(?m)^#moj_import <([^>]+)>\\s*$")
        .replace(source) {
            expandImports(resource("include/${it.groupValues[1]}"))
                .replace(Regex("(?m)^#version[^\\n]*$"), "")
        }

    private fun program(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = glCreateShader(type)
            glShaderSource(shader, source)
            glCompileShader(shader)
            assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader))
            return shader
        }
        val vs = compile(GL_VERTEX_SHADER, vertex)
        val fs = compile(GL_FRAGMENT_SHADER, fragment)
        val program = glCreateProgram()
        glAttachShader(program, vs)
        glAttachShader(program, fs)
        glLinkProgram(program)
        try {
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program))
        } finally {
            glDeleteShader(vs)
            glDeleteShader(fs)
        }
        return program
    }
}
