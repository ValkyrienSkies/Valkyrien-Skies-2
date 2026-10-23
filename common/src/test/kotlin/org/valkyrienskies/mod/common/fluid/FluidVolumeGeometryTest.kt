package org.valkyrienskies.mod.common.fluid

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.random.Random

class FluidVolumeGeometryTest {
    private fun box(bounds: AABB, transform: Matrix4d = Matrix4d(), origin: Vec3 = Vec3.ZERO) =
        FluidVolumeGeometry.Box(bounds, transform, transform.invert(Matrix4d()), origin)

    @Test
    fun `tilted cube rejects dry AABB corners and reports the clipped surface height`() {
        val diamond = box(AABB(-0.5, -0.5, 0.0, 0.5, 0.5, 1.0), Matrix4d().rotateZ(Math.PI / 4))
        assertTrue(diamond.bounds.intersects(AABB(0.6, 0.6, 0.2, 0.65, 0.65, 0.8)))
        assertTrue(diamond.intersectionTop(AABB(0.6, 0.6, 0.2, 0.65, 0.65, 0.8)).isNaN())
        assertEquals(sqrt(0.5) - 0.6, diamond.intersectionTop(AABB(0.6, -1.0, 0.2, 0.65, 1.0, 0.8)), 1e-12)
    }

    @Test
    fun `partial tilted liquid clips its own surface rather than the enclosing box`() {
        val halfCell = box(AABB(-0.5, -0.25, 0.0, 0.5, 0.25, 1.0), Matrix4d().rotateZ(Math.PI / 4))
        assertEquals(sqrt(0.125) - 0.3,
            halfCell.intersectionTop(AABB(-0.4, -1.0, 0.2, -0.3, 1.0, 0.8)), 1e-12)
        assertTrue(halfCell.intersectionTop(AABB(-0.4, 0.1, 0.2, -0.3, 0.3, 0.8)).isNaN())
    }

    @Test
    fun `world liquid survives partial domains and is removed by their complete union`() {
        val liquid = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        val left = box(AABB(0.0, 0.0, 0.0, 0.6, 1.0, 1.0))
        val right = box(AABB(0.4, 0.0, 0.0, 1.0, 1.0, 1.0))
        assertEquals(1.0, FluidVolumeGeometry.uncoveredTop(liquid, listOf(left)), 1e-12)
        assertTrue(FluidVolumeGeometry.uncoveredTop(liquid, listOf(left, right)).isNaN())
        assertTrue(FluidVolumeGeometry.uncoveredTop(liquid, listOf(right, left)).isNaN())
        val ceiling = box(AABB(0.0, 0.4, 0.0, 1.0, 1.0, 1.0))
        assertEquals(0.4, FluidVolumeGeometry.uncoveredTop(liquid, listOf(left, ceiling)), 1e-12)
        val justTouching = box(AABB(1.0, 0.0, 0.0, 2.0, 1.0, 1.0))
        assertEquals(1.0, FluidVolumeGeometry.uncoveredTop(liquid, listOf(justTouching)), 1e-12)
    }

    @Test
    fun `face and edge touching do not count but very thin scaled fluid does`() {
        val unit = box(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
        assertTrue(unit.intersectionTop(AABB(1.0, 0.0, 0.0, 2.0, 1.0, 1.0)).isNaN())
        for (scale in listOf(1e-9, 1e-6, 1.0, 1e6)) {
            val transform = Matrix4d().rotateZ(Math.PI / 4).scale(scale)
            val thin = box(AABB(-0.5, -0.5 / 255, 0.0, 0.5, 0.5 / 255, 1.0), transform)
            val query = AABB(-scale, -scale, 0.2 * scale, scale, scale, 0.8 * scale)
            assertEquals((0.5 + 0.5 / 255) * sqrt(0.5) * scale, thin.intersectionTop(query), 1e-9 * scale)
        }
    }

    @Test
    fun `entity relative coordinates preserve clipping far from the origin`() {
        val origin = Vec3(30_000_000.0, -1024.0, -30_000_000.0)
        val transform = Matrix4d().translation(origin.x, origin.y, origin.z).rotateZ(Math.PI / 4)
        val diamond = box(AABB(-0.5, -0.5, 0.0, 0.5, 0.5, 1.0), transform, origin)
        assertEquals(sqrt(0.5) - 0.6, diamond.intersectionTop(AABB(0.6, -1.0, 0.2, 0.65, 1.0, 0.8)), 1e-8)
    }

    @Test
    fun `union subtraction matches a discrete oracle for many overlapping masks`() {
        val random = Random(1752)
        repeat(100) {
            val masked = Array(3) { Array(3) { BooleanArray(3) } }
            val boxes = (0 until 14).map {
                val x = random.nextInt(3); val y = random.nextInt(3); val z = random.nextInt(3)
                val dx = random.nextInt(x + 1, 4); val dy = random.nextInt(y + 1, 4); val dz = random.nextInt(z + 1, 4)
                for (i in x until dx) for (j in y until dy) for (k in z until dz) masked[i][j][k] = true
                box(AABB(x.toDouble(), y.toDouble(), z.toDouble(), dx.toDouble(), dy.toDouble(), dz.toDouble()))
            }
            var expected = Double.NaN
            for (x in 0..2) for (y in 0..2) for (z in 0..2) {
                if (!masked[x][y][z]) expected = if (expected.isNaN()) y + 1.0 else maxOf(expected, y + 1.0)
            }
            val actual = FluidVolumeGeometry.uncoveredTop(AABB(0.0, 0.0, 0.0, 3.0, 3.0, 3.0), boxes)
            if (expected.isNaN()) assertTrue(actual.isNaN()) else assertEquals(expected, actual, 1e-12)
        }
    }

    @Test
    fun `three dimensional rotated partitions cover their shared boundary without leaking water`() {
        for (sign in listOf(-1.0, 1.0)) {
            val transform = Matrix4d().rotateXYZ(0.3, 0.5, -0.7).scale(sign * 2.0, 0.3, 1.7)
            val left = box(AABB(-0.5, -0.5, -0.5, 0.0, 0.5, 0.5), transform)
            val right = box(AABB(0.0, -0.5, -0.5, 0.5, 0.5, 0.5), transform)
            val liquid = AABB(-0.05, -0.05, -0.05, 0.05, 0.05, 0.05)
            assertTrue(FluidVolumeGeometry.uncoveredTop(liquid, listOf(left, right)).isNaN())
            assertTrue(FluidVolumeGeometry.uncoveredTop(liquid, listOf(right, left)).isNaN())
        }
    }

    @Test
    fun `convex clipping agrees with independent separating axis tests across rotations and scales`() {
        val random = Random(1826)
        repeat(300) {
            val transform = Matrix4d().translation(random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0))
                .rotateXYZ(random.nextDouble(-3.0, 3.0), random.nextDouble(-3.0, 3.0), random.nextDouble(-3.0, 3.0))
                .scale(random.nextDouble(0.01, 2.0), random.nextDouble(0.01, 2.0), random.nextDouble(0.01, 2.0))
            val local = AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5)
            val query = AABB(-0.3, -0.6, -0.2, 0.3, 0.6, 0.2)
            val vertices = (0..7).map { i -> transform.transformPosition(Vector3d(
                if (i and 1 == 0) -0.5 else 0.5, if (i and 2 == 0) -0.5 else 0.5, if (i and 4 == 0) -0.5 else 0.5)) }
            val worldAxes = listOf(Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 1.0))
            val edges = listOf(vertices[1].sub(vertices[0], Vector3d()), vertices[2].sub(vertices[0], Vector3d()), vertices[4].sub(vertices[0], Vector3d()))
            val axes = worldAxes + listOf(edges[0].cross(edges[1], Vector3d()), edges[1].cross(edges[2], Vector3d()), edges[2].cross(edges[0], Vector3d())) +
                edges.flatMap { edge -> worldAxes.map { axis -> edge.cross(axis, Vector3d()) } }
            val overlaps = axes.filter { it.lengthSquared() > 1e-25 }.all { axis ->
                val projections = vertices.map { it.dot(axis) }
                val radius = 0.3 * kotlin.math.abs(axis.x) + 0.6 * kotlin.math.abs(axis.y) + 0.2 * kotlin.math.abs(axis.z)
                projections.max() > -radius && projections.min() < radius
            }
            val top = box(local, transform).intersectionTop(query)
            assertEquals(overlaps, top.isFinite(), "transform=$transform")
            if (top.isFinite()) assertTrue(top <= query.maxY + 1e-12 && top > query.minY)
        }
    }
}
