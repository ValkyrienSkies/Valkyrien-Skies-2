package org.valkyrienskies.mod.common.fluid

import io.mockk.mockk
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion

class FluidInterfaceGeometryTest {
    private val full = (16 shl 15) or (16 shl 20) or (16 shl 25)
    private fun region(axis: Int, low: Double, high: Double, id: Int, bounds: Int = full): VsiFluidSurfaceRegion {
        val n = DoubleArray(3).also { it[axis] = 1.0 }
        return VsiFluidSurfaceRegion(bounds, n[0], n[1], n[2], low, high,
            listOf(VsiFluidSurfaceConstituent(id, 100.0)))
    }
    private fun cell(regions: List<VsiFluidSurfaceRegion>, neighbors: List<VsiFluidFloodedVoxel> = emptyList()) =
        RetainedFluidRenderer.Cell(VsiFluidFloodedVoxel(0, 0, 0, 17, 255, 0, emptyList(), regions),
            null, FluidAppearance(mockk(), mockk(), -1, 0), 0, neighbors, regions.first())

    private fun area(points: List<Vector3d>) = (1 until points.size - 1).sumOf {
        Vector3d(points[it]).sub(points[0]).cross(Vector3d(points[it + 1]).sub(points[0])).length() * 0.5
    }

    @Test
    fun `opposing material interfaces are single sided on every axis without moving vertices`() {
        for (axis in 0..2) {
            val first = region(axis, 0.0, 0.5, 17)
            val second = region(axis, 0.5, 1.0, 18)
            for (regions in listOf(listOf(first, second), listOf(second, first))) {
                val cell = cell(regions)
                val faces = FluidVoxelGeometry.faces(cell.surface())
                var sharedArea = 0.0
                for (face in faces) {
                    val normal = Vector3d(face[1]).sub(face[0]).cross(Vector3d(face[2]).sub(face[0])).normalize()
                    val pieces = FluidInterfaceGeometry.partition(cell, face, normal, true)
                    assertEquals(area(face), pieces.sumOf { area(it.points()) }, 1e-12)
                    for (piece in pieces.filter { it.interfaceFace() }) {
                        assertTrue(piece.points().all { it[axis] == 0.5 })
                        sharedArea += area(piece.points())
                    }
                }
                assertEquals(1.0, sharedArea, 1e-12)
            }
        }
    }

    @Test
    fun `partial neighbor interface leaves the uncovered side double sided`() {
        val own = region(1, 0.0, 1.0, 17)
        val other = VsiFluidFloodedVoxel(1, 0, 0, 18, 128, 0, emptyList(), listOf(region(1, 0.0, 0.5, 18)))
        val cell = cell(listOf(own), listOf(other))
        val face = FluidVoxelGeometry.faces(own).first { points -> points.all { it.x == 1.0 } }
        val parts = FluidInterfaceGeometry.partition(cell, face, Vector3d(1.0, 0.0, 0.0), true)
        assertEquals(0.5, parts.filter { it.interfaceFace() }.sumOf { area(it.points()) }, 1e-12)
        assertEquals(0.5, parts.filterNot { it.interfaceFace() }.sumOf { area(it.points()) }, 1e-12)
        assertTrue(parts.filterNot { it.interfaceFace() }.flatMap { it.points() }.all { it.y >= 0.5 })
        val separated = other.copy(positionX = 2)
        val exposed = FluidInterfaceGeometry.partition(cell(listOf(own), listOf(separated)), face,
            Vector3d(1.0, 0.0, 0.0), true)
        assertTrue(exposed.none { it.interfaceFace() })
        assertEquals(1.0, exposed.sumOf { area(it.points()) }, 1e-12)
    }

    @Test
    fun `same fluid removes only the submerged part of a shared side`() {
        val own = region(1, 0.0, 1.0, 17)
        val other = VsiFluidFloodedVoxel(1, 0, 0, 17, 128, 0, emptyList(), listOf(region(1, 0.0, 0.5, 17)))
        val face = FluidVoxelGeometry.faces(own).first { points -> points.all { it.x == 1.0 } }
        val parts = FluidInterfaceGeometry.partition(cell(listOf(own), listOf(other)), face, Vector3d(1.0, 0.0, 0.0), true)
        assertEquals(0.5, parts.sumOf { area(it.points()) }, 1e-12)
        assertTrue(parts.none { it.interfaceFace() })
        assertTrue(parts.flatMap { it.points() }.all { it.y >= 0.5 - 1e-12 })
    }

    @Test
    fun `crossing tilted surfaces leave no overlapping internal water faces`() {
        fun tilted(x: Int, slope: Double, offset: Double): VsiFluidFloodedVoxel {
            val n = Vector3d(0.0, 1.0, slope).normalize()
            val surface = VsiFluidSurfaceRegion(full, n.x, n.y, n.z, minOf(0.0, n.z), offset * n.y,
                listOf(VsiFluidSurfaceConstituent(17, 127.5)))
            return VsiFluidFloodedVoxel(x, 0, 0, 17, 128, 0, emptyList(), listOf(surface))
        }
        val a = tilted(0, 0.6, 0.8)
        val b = tilted(1, -0.6, 0.2)
        for ((own, other) in listOf(a to b, b to a)) {
            val side = if (own.positionX == 0) 1.0 else 0.0
            val normal = Vector3d(if (side == 1.0) 1.0 else -1.0, 0.0, 0.0)
            val face = FluidVoxelGeometry.faces(own.surfaceRegions.single()).first { points -> points.all { it.x == side } }
            val cell = RetainedFluidRenderer.Cell(own, null, FluidAppearance(mockk(), mockk(), -1, 0), 0,
                listOf(other), own.surfaceRegions.single())
            val parts = FluidInterfaceGeometry.partition(cell, face, normal, true)
            assertEquals(0.15, parts.sumOf { area(it.points()) }, 1e-12)
            assertTrue(parts.none { it.interfaceFace() })
            val otherPlane = other.surfaceRegions.single()
            assertTrue(parts.flatMap { it.points() }.all {
                otherPlane.normalY * it.y + otherPlane.normalZ * it.z >= otherPlane.upperProjection - 1e-12
            })
        }
    }

    @Test
    fun `steep bucket cells remove internal sheets without changing enclosed volume`() {
        val n = Vector3d(0.781970, -0.328629, 0.529647).normalize()
        val amounts = listOf(255.0, 254.072, 255.0, 255.0, 255.0, 255.0,
            236.083, 23.8467, 255.0, 202.888, 255.0, 247.083)
        val cube = net.minecraft.world.phys.AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        val voxels = amounts.mapIndexed { i, amount ->
            val plane = FluidSurfaceGeometry.atVolume(listOf(cube), n.x, n.y, n.z, amount / 255.0)
            VsiFluidFloodedVoxel(i / 6, (i % 6) / 2, i % 2, 17, kotlin.math.ceil(amount).toInt(), 0, emptyList(),
                listOf(VsiFluidSurfaceRegion(full, n.x, n.y, n.z, minOf(0.0, n.x) + minOf(0.0, n.y) + minOf(0.0, n.z),
                    plane.offset(), listOf(VsiFluidSurfaceConstituent(17, amount)))))
        }
        assertFalse(FluidSurfaceReconstruction.build(voxels) { it }.isEmpty())
        var beforeArea = 0.0
        var afterArea = 0.0
        var volume = 0.0
        for (own in voxels) {
            val neighbors = voxels.filter { other ->
                kotlin.math.abs(other.positionX - own.positionX) + kotlin.math.abs(other.positionY - own.positionY) +
                    kotlin.math.abs(other.positionZ - own.positionZ) == 1
            }
            val cell = RetainedFluidRenderer.Cell(own, null, FluidAppearance(mockk(), mockk(), -1, 0), 0,
                neighbors, own.surfaceRegions.single())
            for (face in FluidVoxelGeometry.faces(cell.surface())) {
                val normal = Vector3d(face[1]).sub(face[0]).cross(Vector3d(face[2]).sub(face[0]))
                if (normal.lengthSquared() < 1e-24) continue
                normal.normalize()
                beforeArea += area(face)
                for (piece in FluidInterfaceGeometry.partition(cell, face, normal, true)) {
                    afterArea += area(piece.points())
                    val points = piece.points().map { Vector3d(it).add(own.positionX.toDouble(), own.positionY.toDouble(), own.positionZ.toDouble()) }
                    volume += (1 until points.size - 1).sumOf { points[0].dot(Vector3d(points[it]).cross(points[it + 1])) / 6.0 }
                    val probe = piece.points().fold(Vector3d()) { sum, p -> sum.add(p) }.div(piece.points().size.toDouble()).fma(1e-7, normal)
                    assertTrue(neighbors.none { other ->
                        val local = Vector3d(probe).add((own.positionX - other.positionX).toDouble(),
                            (own.positionY - other.positionY).toDouble(), (own.positionZ - other.positionZ).toDouble())
                        local.x in 0.0..1.0 && local.y in 0.0..1.0 && local.z in 0.0..1.0 &&
                            n.dot(local) < other.surfaceRegions.single().upperProjection - 1e-9
                    }, "No emitted face may point into neighboring water")
                }
            }
        }
        assertTrue(afterArea < beforeArea * 0.75)
        assertEquals(amounts.sum() / 255.0, volume, 1e-10)
    }
}
