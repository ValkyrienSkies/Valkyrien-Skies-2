package org.valkyrienskies.mod.common.fluid

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexBuffer
import io.mockk.every
import io.mockk.mockk
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL15.*
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent
import org.valkyrienskies.core.internal.physics.VsiFluidCurrent

class RetainedFluidMeshTest {
    @Test fun `velocity changes preserve mesh geometry while a changed surface invalidates it`() {
        val bounds = (16 shl 15) or (16 shl 20) or (16 shl 25)
        val surface = VsiFluidSurfaceRegion(bounds, 0.0, 1.0, 0.0, 0.0, .5,
            listOf(VsiFluidSurfaceConstituent(17, 127.5)))
        val voxel = VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0, surfaceRegions = listOf(surface))
        val moving = voxel.copy(surfaceRegions = listOf(surface.copy(current = VsiFluidCurrent(1.0, 2.0, 3.0, .1, .2, .3))))
        assertEquals(RetainedFluidRenderer.geometry(voxel), RetainedFluidRenderer.geometry(moving))
        assertTrue(FluidVoxelGeometry.sameSurfaceGeometry(voxel.surfaceRegions, moving.surfaceRegions))
        val changed = moving.copy(surfaceRegions = listOf(surface.copy(upperProjection = .6)))
        assertNotEquals(RetainedFluidRenderer.geometry(voxel), RetainedFluidRenderer.geometry(changed))
        assertFalse(FluidVoxelGeometry.sameSurfaceGeometry(voxel.surfaceRegions, changed.surfaceRegions))
    }

    @Test
    fun `pressure changes retain geometry while material and volume changes invalidate it`() {
        val voxel = VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0)
        assertEquals(RetainedFluidRenderer.geometry(voxel), RetainedFluidRenderer.geometry(voxel.copy(pressureAmount = 900)))
        assertNotEquals(RetainedFluidRenderer.geometry(voxel), RetainedFluidRenderer.geometry(voxel.copy(fluidId = 18)))
        assertNotEquals(RetainedFluidRenderer.geometry(voxel), RetainedFluidRenderer.geometry(voxel.copy(fillAmount = 129)))
    }

    @Test
    fun `appearance preserves alpha and raises only block light to fluid emission`() {
        val a = FluidAppearance(mockk(), mockk(), 0x80402010.toInt(), 10)
        assertEquals(128 / 255f, a.alpha())
        assertEquals(64 / 255f, a.red())
        assertEquals(0x00B000A0, a.light(0x00B00020))
        assertEquals(0x00B000E0, a.light(0x00B000E0))
    }

    @Test
    @EnabledIfSystemProperty(named = "vs.test.fluidGL", matches = "true")
    fun `static mesh and camera sorting retain vertex storage and removal releases it`() {
        assertTrue(glfwInit())
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        val window = glfwCreateWindow(16, 16, "Retained mesh", 0, 0)
        assertNotEquals(0L, window)
        val mesh = RetainedFluidRenderer.Mesh(0, 0, 0, false)
        try {
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread()
            val sprite = mockk<TextureAtlasSprite>(relaxed = true)
            every { sprite.u1 } returns 1f
            every { sprite.v1 } returns 1f
            val appearance = FluidAppearance(sprite, sprite, -1, 0)
            val voxel = VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 0)
            val cell = RetainedFluidRenderer.Cell(voxel, null, appearance, 0, emptyList())
            val flooding = maskFlooding(voxel)
            mesh.update(listOf(cell), flooding)
            val bufferField = mesh.javaClass.getDeclaredField("buffer").apply { isAccessible = true }
            val idField = VertexBuffer::class.java.getDeclaredField("vertexBufferId").apply { isAccessible = true }
            val buffer = bufferField.get(mesh) as VertexBuffer
            val id = idField.getInt(buffer)
            fun bytes(): java.nio.ByteBuffer {
                glBindBuffer(GL_ARRAY_BUFFER, id)
                return BufferUtils.createByteBuffer(glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE)).also {
                    glGetBufferSubData(GL_ARRAY_BUFFER, 0, it)
                }
            }
            val original = bytes()
            repeat(8) {
                mesh.update(listOf(cell), flooding)
                mesh.sort(Vector3d(it.toDouble(), 3.0, -2.0))
                assertSame(buffer, bufferField.get(mesh))
                assertEquals(original, bytes(), "camera movement must upload only indices")
            }
            mesh.update(listOf(cell.copyWithLight(0xF000F0)), flooding)
            assertNotSame(buffer, bufferField.get(mesh))
            assertFalse(glIsBuffer(id))
            val replacementId = idField.getInt(bufferField.get(mesh))
            mesh.close()
            assertFalse(glIsBuffer(replacementId))
            every { sprite.getU(any()) } answers { firstArg<Double>().toFloat() / 16f }
            every { sprite.getV(any()) } answers { firstArg<Double>().toFloat() / 16f }
            for (axis in 0..2) {
                val normal = DoubleArray(3).also { it[axis] = 1.0 }
                val region = org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion(
                    (16 shl 15) or (16 shl 20) or (16 shl 25), normal[0], normal[1], normal[2], 0.0, 0.5,
                    listOf(org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent(17, 127.5)))
                val tilted = voxel.copy(surfaceRegions = listOf(region))
                mesh.update(listOf(RetainedFluidRenderer.Cell(tilted, null, appearance, 0, emptyList(), region)), maskFlooding(tilted))
                glBindBuffer(GL_ARRAY_BUFFER, idField.getInt(bufferField.get(mesh)))
                val vertices = BufferUtils.createByteBuffer(glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE))
                glGetBufferSubData(GL_ARRAY_BUFFER, 0, vertices)
                val surfaceUvs = (0 until vertices.capacity() step 36).filter {
                    vertices.get(it + 32 + axis).toInt() == 127
                }.map { vertices.getFloat(it + 16) to vertices.getFloat(it + 20) }
                assertTrue(surfaceUvs.isNotEmpty())
                assertTrue(surfaceUvs.maxOf { it.first } - surfaceUvs.minOf { it.first } > 0.99f)
                assertTrue(surfaceUvs.maxOf { it.second } - surfaceUvs.minOf { it.second } > 0.99f,
                    "surface texture must have area when gravity points along axis $axis")
                if (axis == 1) {
                    val sideUvs = (0 until vertices.capacity() step 36).filter {
                        vertices.get(it + 32).toInt() == 127
                    }.map { vertices.getFloat(it + 16) to vertices.getFloat(it + 20) }
                    assertEquals(0.5f, sideUvs.maxOf { it.first } - sideUvs.minOf { it.first }, 1e-6f)
                    assertEquals(0.25f, sideUvs.maxOf { it.second } - sideUvs.minOf { it.second }, 1e-6f,
                        "A half-height side crops half of the vanilla flowing-fluid tile")
                }
                mesh.close()
            }
            val bounds = (16 shl 15) or (16 shl 20) or (16 shl 25)
            fun filled(x: Int, h: Double) = VsiFluidFloodedVoxel(x, 0, 0, 17, (h * 255).toInt(), 0,
                emptyList(), listOf(org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion(bounds,
                    0.0, 1.0, 0.0, 0.0, h, listOf(org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent(17, h * 255)))))
            val low = filled(0, 0.1); val high = filled(1, 0.8)
            val full = filled(0, 1.0)
            fun wallCell(mask: Int) = RetainedFluidRenderer.Cell(full, null, appearance, 0, emptyList(),
                full.surfaceRegions.single(), null, mask)
            mesh.update(listOf(wallCell(55)), maskFlooding(full))
            val bucketBuffer = bufferField.get(mesh) as VertexBuffer
            glBindBuffer(GL_ARRAY_BUFFER, idField.getInt(bucketBuffer))
            assertEquals(8 * 36, glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE),
                "An opaque bucket leaves only the open water surface")
            mesh.update(listOf(wallCell(55)), maskFlooding(full))
            assertSame(bucketBuffer, bufferField.get(mesh))
            mesh.update(listOf(wallCell(0)), maskFlooding(full))
            assertNotSame(bucketBuffer, bufferField.get(mesh), "Removing walls must restore water sides without a fluid update")
            glBindBuffer(GL_ARRAY_BUFFER, idField.getInt(bufferField.get(mesh)))
            assertEquals(48 * 36, glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE))
            val lower = filled(0, 0.5).surfaceRegions.single()
            val upper = lower.copy(lowerProjection = 0.5, upperProjection = 1.0,
                constituents = listOf(org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent(18, 127.5)))
            val layered = filled(0, 1.0).copy(surfaceRegions = listOf(lower, upper))
            mesh.update(listOf(lower, upper).map {
                RetainedFluidRenderer.Cell(layered, null, appearance, 0xF000F0, emptyList(), it)
            }, maskFlooding(layered))
            glBindBuffer(GL_ARRAY_BUFFER, idField.getInt(bufferField.get(mesh)))
            val layers = BufferUtils.createByteBuffer(glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE))
            glGetBufferSubData(GL_ARRAY_BUFFER, 0, layers)
            var marked = 0
            for (offset in 0 until layers.capacity() step 36) {
                assertEquals(240, layers.getShort(offset + 28).toInt(), "Interface data must not overwrite block light")
                assertEquals(240, layers.getShort(offset + 30).toInt(), "Interface data must not overwrite sky light")
                if (layers.getShort(offset + 24).toInt() != 0) {
                    marked++
                    assertEquals(0.5f, layers.getFloat(offset + 4))
                    assertEquals(127, kotlin.math.abs(layers.get(offset + 33).toInt()))
                }
            }
            assertEquals(16, marked, "Both opposing material caps must carry the interface flag")
            fun reconstructedCell(): RetainedFluidRenderer.Cell {
                val faces = FluidSurfaceReconstruction.build(listOf(low, high)) { it }
                return RetainedFluidRenderer.Cell(low, null, appearance, 0, listOf(high), low.surfaceRegions.single(),
                    faces.getValue(FloodedFluidClientCache.pack(0, 0, 0)))
            }
            mesh.update(listOf(reconstructedCell()), maskFlooding(low))
            val continuousBuffer = bufferField.get(mesh) as VertexBuffer
            glBindBuffer(GL_ARRAY_BUFFER, idField.getInt(continuousBuffer))
            assertEquals(80 * 36, glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE),
                "Keep the entire lowered cap, omit the shared side, and retain the three exterior sides and bottom")
            mesh.update(listOf(reconstructedCell()), maskFlooding(low))
            assertSame(continuousBuffer, bufferField.get(mesh), "Equal reconstructed geometry must retain GPU storage")
            val steepNormal = Vector3d(0.781970, -0.328629, 0.529647).normalize()
            val savedAmounts = listOf(255.0, 254.072, 255.0, 255.0, 255.0, 255.0, 236.083, 23.8467, 255.0, 202.888, 255.0, 247.083)
            val steepVoxels = savedAmounts.mapIndexed { i, amount ->
                val plane = FluidSurfaceGeometry.atVolume(listOf(net.minecraft.world.phys.AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
                    steepNormal.x, steepNormal.y, steepNormal.z, amount / 255.0)
                VsiFluidFloodedVoxel(i / 6, (i % 6) / 2, i % 2, 17, kotlin.math.ceil(amount).toInt(), 0, emptyList(),
                    listOf(VsiFluidSurfaceRegion(bounds, steepNormal.x, steepNormal.y, steepNormal.z, steepNormal.y,
                        plane.offset(), listOf(VsiFluidSurfaceConstituent(17, amount)))))
            }
            val steepFaces = FluidSurfaceReconstruction.build(steepVoxels) { it }
            assertEquals(steepVoxels.size, steepFaces.size)
            val steepCells = steepVoxels.map { v ->
                val faces = steepFaces.getValue(FloodedFluidClientCache.pack(v.positionX, v.positionY, v.positionZ))
                val neighbors = steepVoxels.filter { kotlin.math.abs(it.positionX - v.positionX) +
                    kotlin.math.abs(it.positionY - v.positionY) + kotlin.math.abs(it.positionZ - v.positionZ) == 1 }
                RetainedFluidRenderer.Cell(v, null, appearance, 0, RetainedFluidRenderer.surfaceNeighbors(v, neighbors, faces),
                    v.surfaceRegions.single(), faces)
            }
            val expectedVertices = steepFaces.values.flatten().filterNot { it.internal() }.sumOf { face ->
                val p = face.points()
                if (Vector3d(p[1]).sub(p[0]).cross(Vector3d(p[2]).sub(p[0])).lengthSquared() < 1e-24) 0 else (p.size - 2) * 4
            }
            mesh.update(steepCells, maskFlooding(*steepVoxels.toTypedArray()))
            val steepBuffer = bufferField.get(mesh) as VertexBuffer
            glBindBuffer(GL_ARRAY_BUFFER, idField.getInt(steepBuffer))
            assertEquals(expectedVertices * 36, glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE),
                "The GPU mesh must retain the complete reconstructed boundary without old-plane clipping")
            mesh.update(steepCells, maskFlooding(*steepVoxels.toTypedArray()))
            assertSame(steepBuffer, bufferField.get(mesh))
            val dust = filled(0, 5.551115123125783e-17)
            mesh.update(listOf(RetainedFluidRenderer.Cell(dust, null, appearance, 0, emptyList(),
                dust.surfaceRegions.single())), maskFlooding(dust))
            assertNull(bufferField.get(mesh), "Numerical material films must not upload visible caps")
        } finally {
            mesh.close()
            VertexBuffer.unbind()
            GL.setCapabilities(null)
            glfwDestroyWindow(window)
            glfwTerminate()
        }
    }

    private fun RetainedFluidRenderer.Cell.copyWithLight(light: Int) =
        RetainedFluidRenderer.Cell(voxel(), box(), appearance(), light, neighbors())
}
