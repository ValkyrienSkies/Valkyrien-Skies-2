package org.valkyrienskies.mod.common.fluid

import io.mockk.every
import io.mockk.mockk
import java.nio.FloatBuffer
import org.joml.Matrix4d
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ClientShip

class FluidOcclusionOverflowTest {
    @Test fun `hundreds of masks pack across pages without a ship count limit`() {
        val layout = FluidOcclusionOverflow.Layout.plan(IntArray(300) { 4096 }, 4096, 2)
        assertEquals(4096, layout.width())
        assertEquals(256, layout.pageHeight())
        assertEquals(2, layout.layers())
        assertEquals(10, layout.descriptorHeight())
        assertArrayEquals(IntArray(300) { it * 4096 }, layout.offsets())
    }

    @Test fun `page height grows for layer limits and small texture hardware`() {
        val layout = FluidOcclusionOverflow.Layout.plan(intArrayOf(4096 * 400, 8192), 1024, 2)
        assertEquals(1024, layout.width())
        assertEquals(1024, layout.pageHeight())
        assertEquals(2, layout.layers())
        assertArrayEquals(intArrayOf(0, 4096 * 400), layout.offsets())
    }

    @Test fun `hardware and address limits reject the whole plan`() {
        assertThrows(IllegalArgumentException::class.java) {
            FluidOcclusionOverflow.Layout.plan(intArrayOf(4096 * 600, 8192), 1024, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FluidOcclusionOverflow.Layout.plan(intArrayOf(4096), 4096, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FluidOcclusionOverflow.Layout.plan(IntArray(3) { 1 shl 30 }, 16384, 2048)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FluidOcclusionOverflow.Layout.plan(IntArray(9) { 4096 }, 8, 65536)
        }
    }

    @Test fun `descriptor preserves high integer offsets and conservative transformed bounds`() {
        val data = FluidOcclusionMaskData.build(maskTopology(Triple(-7, 3, 9)), null)!!
        val transform = Matrix4d().translation(1e6, -17.0, -2e6).rotateXYZ(0.3, 0.6, -0.2).scale(0.25, 2.0, 1.5)
        val ship = mockk<ClientShip>(relaxed = true)
        every { ship.renderTransform.shipToWorld } returns transform
        every { ship.renderTransform.worldToShip } returns Matrix4d(transform).invert()
        val buffer = FloatBuffer.allocate(32)
        val offset = 0x7fff1234
        val camera = Vector3d(1e6 + 10, -20.0, -2e6 - 15)
        FluidOcclusionOverflow.describe(buffer, FluidOcclusionOverflow.Entry(ship, data), offset, camera.x, camera.y, camera.z)
        assertEquals(offset, buffer[3].toInt() + (buffer[7].toInt() shl 16))
        assertEquals(32, buffer.position())
        for (corner in 0..7) {
            val p = transform.transformPosition(-7.0 + (corner and 1), 3.0 + ((corner shr 1) and 1),
                9.0 + ((corner shr 2) and 1), Vector3d()).sub(camera)
            for (axis in 0..2) {
                assertTrue(buffer[axis] <= p[axis], "minimum axis $axis")
                assertTrue(buffer[4 + axis] >= p[axis], "maximum axis $axis")
            }
        }
        val local = Matrix4d(transform).invert().transformPosition(camera, Vector3d()).sub(-7.0, 3.0, 9.0)
        for (axis in 0..2) assertEquals(local[axis].toFloat(), buffer[12 + axis])
    }
}
