package org.valkyrienskies.mod.common.fluid

import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FluidSurfaceGeometryTest {
    private val cube = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

    @Test
    fun `numerical material films have no visible faces while thin real layers retain volume`() {
        val bounds = (16 shl 15) or (16 shl 20) or (16 shl 25)
        for (axis in 0..2) for (sign in listOf(-1.0, 1.0)) {
            val normal = DoubleArray(3).also { it[axis] = sign }
            val lower = if (sign < 0) -1.0 else 0.0
            fun layer(thickness: Double) = org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion(
                bounds, normal[0], normal[1], normal[2], lower, lower + thickness,
                listOf(org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent(29, thickness * 255)))
            assertTrue(FluidVoxelGeometry.faces(layer(5.551115123125783e-17)).isEmpty())
            val thin = layer(1e-6)
            assertEquals(1e-6, meshVolume(FluidVoxelGeometry.faces(thin)), 1e-12)
            assertEquals(255e-6, thin.constituents.single().amount, 1e-15)
        }
    }

    @Test
    fun `geometry accepts the surface contract normal tolerance without changing the plane`() {
        val normal = 1.0 + 2e-7
        val surface = org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion(
            (16 shl 15) or (16 shl 20) or (16 shl 25), 0.0, normal, 0.0, 0.0, normal * 0.5,
            listOf(org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent(17, 127.5)))
        assertEquals(0.5, meshVolume(FluidVoxelGeometry.faces(surface)), 1e-12)
    }

    @Test
    fun `point and body immersion share the exported tilted surface`() {
        val n = 1.0 / kotlin.math.sqrt(2.0)
        val bounds = (16 shl 15) or (16 shl 20) or (16 shl 25)
        val surface = org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion(bounds, n, n, 0.0, 0.0, n,
            listOf(org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent(17, 127.5)))
        val voxel = org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel(0, 0, 0, 17, 128, 99,
            emptyList(), listOf(surface))
        assertEquals(17, FluidVoxelGeometry.fluidAt(voxel, 0.1, 0.75, 0.5))
        assertEquals(0, FluidVoxelGeometry.fluidAt(voxel, 0.9, 0.25, 0.5))
        assertEquals(0.5, meshVolume(FluidVoxelGeometry.faces(surface)), 1e-12)
        val identity = org.joml.Matrix4d()
        val volume = FluidVolumeGeometry.Box(voxel, surface, identity, identity, net.minecraft.world.phys.Vec3.ZERO)
        assertTrue(volume.intersectionTop(AABB(0.8, 0.3, 0.1, 0.9, 0.5, 0.9)).isNaN())
        assertEquals(0.2, volume.intersectionTop(AABB(0.8, 0.0, 0.1, 0.9, 0.5, 0.9)), 1e-12)
    }

    @Test
    fun `neighboring cells with one gravity plane meet at identical edge heights`() {
        val nx = kotlin.math.sin(0.35)
        val ny = kotlin.math.cos(0.35)
        val height = 2.0
        val bounds = (16 shl 15) or (16 shl 20) or (16 shl 25)
        fun edge(cellX: Int) = FluidVoxelGeometry.faces(
            org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion(bounds, nx, ny, 0.0,
                0.0, height - nx * cellX - ny,
                listOf(org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent(17, 128.0))))
            .flatten().map { Vector3d(it).add(cellX.toDouble(), 1.0, 0.0) }
            .filter { kotlin.math.abs(it.x - 1.0) < 1e-9 && kotlin.math.abs(nx * it.x + ny * it.y - height) < 1e-9 }
            .map { it.z to it.y }.distinct().sortedBy { it.first }
        val left = edge(0)
        val right = edge(1)
        assertEquals(2, left.size)
        assertEquals(2, right.size)
        left.zip(right).forEach { (a, b) ->
            assertEquals(a.first, b.first, 1e-9)
            assertEquals(a.second, b.second, 1e-9)
            assertEquals((height - nx) / ny, a.second, 1e-9)
        }
    }

    @Test
    fun `tilted unit cube has the analytical corner tetrahedron volume`() {
        val n = 1.0 / kotlin.math.sqrt(3.0)
        assertEquals(1.0 / 6.0, FluidSurfaceGeometry.volumeBelow(cube,
            FluidSurfaceGeometry.Plane(n, n, n, n)), 1e-12)
        assertEquals(0.5, FluidSurfaceGeometry.volumeBelow(cube,
            FluidSurfaceGeometry.Plane(n, n, n, n * 1.5)), 1e-12)
    }

    @Test
    fun `surface solving and closed polygon volume conserve every partial fill under tilt`() {
        for (normal in listOf(Vector3d(0.0, 1.0, 0.0), Vector3d(1.0, 2.0, 3.0),
            Vector3d(-2.0, 1.0, -3.0), Vector3d(1e-10, 1.0, 1e-11))) {
            for (fill in 1..254) {
                val wanted = fill / 255.0
                val plane = FluidSurfaceGeometry.atVolume(listOf(cube), normal.x, normal.y, normal.z, wanted)
                assertEquals(wanted, FluidSurfaceGeometry.volumeBelow(cube, plane), 1e-9)
                assertEquals(wanted, meshVolume(FluidSurfaceGeometry.clip(cube, plane, null)), 1e-9)
            }
        }
    }

    @Test
    fun `multiple disjoint void boxes and material layers retain constituent volume`() {
        val boxes = listOf(AABB(0.0, 0.0, 0.0, 0.25, 1.0, 1.0), AABB(0.75, 0.0, 0.0, 1.0, 1.0, 1.0))
        val bottom = FluidSurfaceGeometry.atVolume(boxes, 1.0, 2.0, 1.0, 0.15)
        val top = FluidSurfaceGeometry.atVolume(boxes, 1.0, 2.0, 1.0, 0.4)
        assertEquals(0.15, boxes.sumOf { meshVolume(FluidSurfaceGeometry.clip(it, bottom, null)) }, 1e-10)
        assertEquals(0.25, boxes.sumOf { meshVolume(FluidSurfaceGeometry.clip(it, top, bottom)) }, 1e-10)
    }

    @Test
    fun `zero and complete fill do not add duplicate surface caps`() {
        for (fill in listOf(0.0, 1.0)) {
            val plane = FluidSurfaceGeometry.atVolume(listOf(cube), 0.0, 1.0, 0.0, fill)
            assertEquals(fill, meshVolume(FluidSurfaceGeometry.clip(cube, plane, null)), 1e-12)
        }
    }

    private fun meshVolume(faces: List<List<Vector3d>>): Double = faces.sumOf { face ->
        (1 until face.size - 1).sumOf { i -> face[0].dot(Vector3d(face[i]).cross(face[i + 1])) / 6.0 }
    }
}
