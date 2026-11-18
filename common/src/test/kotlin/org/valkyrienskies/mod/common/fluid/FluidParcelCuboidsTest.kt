package org.valkyrienskies.mod.common.fluid

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

class FluidParcelCuboidsTest {
    private fun parcel(x: Double, y: Double = 0.0, z: Double = 0.0, volume: Double = 0.125, material: Int = 0) =
        FluidParcelCuboids.Parcel(Vector3d(x, y, z), volume, material)

    private fun volume(mesh: FluidParcelCuboids.Mesh, material: Int = 0): Double {
        val faces = mesh.faces().filter { it.material() == material && it.axis() == 0 }
        val origin = faces.firstOrNull()?.plane() ?: return 0.0
        return faces.sumOf { it.sign() * (it.plane() - origin) * (it.maxU() - it.minU()) * (it.maxV() - it.minV()) }
    }

    private fun check(parcels: List<FluidParcelCuboids.Parcel>) {
        val mesh = FluidParcelCuboids.build(parcels)
        for ((material, entries) in parcels.groupBy { it.material() }) {
            val expected = entries.sumOf { it.volume() }
            assertEquals(expected, volume(mesh, material), expected * 1e-8)
        }
        for (face in mesh.faces()) {
            assertTrue(face.maxU() > face.minU() && face.maxV() > face.minV())
            assertTrue(face.plane().isFinite())
        }
    }

    @Test
    fun `separate parcels remain six-face cubes at small and large coordinates`() {
        for (amount in listOf(1e-25, 1e-12, 0.01, 0.125, 1.0, 64.0)) {
            for (offset in listOf(0.0, 30_000_000.0)) {
                val inputs = listOf(parcel(offset, offset, -offset, amount))
                check(inputs)
                assertEquals(6, FluidParcelCuboids.build(inputs).faces().size)
            }
        }
    }

    @Test
    fun `touching faces disappear while overlapping and coincident parcels preserve material volumes`() {
        val adjacent = listOf(parcel(0.0), parcel(0.5))
        check(adjacent)
        assertEquals(10, FluidParcelCuboids.build(adjacent).faces().size)
        check(listOf(parcel(0.0), parcel(0.0)))
        assertEquals(6, FluidParcelCuboids.build(listOf(parcel(0.0), parcel(0.0))).faces().size)
        check(listOf(parcel(0.0), parcel(0.2, 0.1), parcel(0.3, volume = 0.01), parcel(0.0, material = 1)))
        val random = Random(1729)
        repeat(100) {
            check(List(16) { parcel(random.nextDouble(), random.nextDouble(), random.nextDouble(),
                0.001 + random.nextDouble() * 0.25, it % 2) })
        }
    }

    private fun bounds(mesh: FluidParcelCuboids.Mesh): DoubleArray {
        val result = doubleArrayOf(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (face in mesh.faces()) for (axis in 0..2) {
            result[axis] = minOf(result[axis], mesh.origin()[axis] + face.coordinate(axis, face.minU(), face.minV()))
            result[axis + 3] = maxOf(result[axis + 3], mesh.origin()[axis] + face.coordinate(axis, face.maxU(), face.maxV()))
        }
        return result
    }

    @Test
    fun `grid crossings and contact transitions do not jump the surface`() {
        val a = bounds(FluidParcelCuboids.build(listOf(parcel(0.079768265, volume = 0.01))))
        val b = bounds(FluidParcelCuboids.build(listOf(parcel(0.079788265, volume = 0.01))))
        assertEquals(0.00002, b[0] - a[0], 1e-12)
        assertEquals(0.00002, b[3] - a[3], 1e-12)
        for (sign in listOf(-1, 1)) for (center in listOf(0.0, 0.5, 0.25, -0.25)) {
            val before = listOf(parcel(0.0), parcel(center - sign * 1e-7))
            val after = listOf(parcel(0.0), parcel(center + sign * 1e-7))
            check(before); check(after)
            val x = bounds(FluidParcelCuboids.build(before))
            val y = bounds(FluidParcelCuboids.build(after))
            for (axis in x.indices) assertEquals(x[axis], y[axis], 2e-6)
        }
    }

    @Test
    fun `input ordering does not change the union and invalid data cannot poison other parcels`() {
        val inputs = listOf(parcel(0.0), parcel(0.2, 0.1), parcel(-0.3, volume = 0.03))
        val forward = FluidParcelCuboids.build(inputs)
        val reverse = FluidParcelCuboids.build(inputs.reversed())
        assertArrayEquals(bounds(forward), bounds(reverse), 1e-12)
        assertEquals(volume(forward), volume(reverse), 1e-12)
        val invalid = listOf(parcel(0.0, volume = Double.NaN), parcel(Double.NaN), parcel(0.0, volume = -1.0))
        assertEquals(6, FluidParcelCuboids.build(listOf(parcel(0.0)) + invalid).faces().size)
    }
    @Test
    fun `long separated streams preserve cubes along every sweep axis`() {
        for (axis in 0..2) {
            val inputs = List(1024) { i ->
                val position = Vector3d().setComponent(axis, i * 0.25)
                FluidParcelCuboids.Parcel(position, 0.001, 0)
            }
            check(inputs)
            assertEquals(6 * inputs.size, FluidParcelCuboids.build(inputs).faces().size)
        }
    }

    @Test
    fun `touching and overlapping streams preserve volume along every sweep axis`() {
        for (axis in 0..2) for (step in listOf(0.0, 0.2, 0.5)) {
            val inputs = List(32) { i ->
                val position = Vector3d().setComponent(axis, i * step)
                FluidParcelCuboids.Parcel(position, 0.125, 0)
            }
            check(inputs)
            if (step == 0.5) assertEquals(4 * inputs.size + 2, FluidParcelCuboids.build(inputs).faces().size)
        }
    }

    @Test
    fun `elongated mixed-size and mixed-material streams preserve volume`() {
        val random = Random(8675309)
        for (axis in 0..2) repeat(12) {
            val inputs = List(96) { i ->
                val position = Vector3d(random.nextDouble(), random.nextDouble(), random.nextDouble())
                position.setComponent(axis, position[axis] * 30.0)
                FluidParcelCuboids.Parcel(position, 0.001 + random.nextDouble() * 0.125, i % 2)
            }
            check(inputs)
            assertArrayEquals(bounds(FluidParcelCuboids.build(inputs)),
                bounds(FluidParcelCuboids.build(inputs.reversed())), 1e-10)
        }
    }

}
