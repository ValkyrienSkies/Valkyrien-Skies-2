package org.valkyrienskies.mod.common.fluid

import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion
import kotlin.math.abs

class FluidSurfaceReconstructionTest {
    private val full = (16 shl 15) or (16 shl 20) or (16 shl 25)
    private val cube = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
    private fun voxel(x: Int, y: Int, z: Int, height: Double, axis: Int = 1, sign: Int = 1, id: Int = 17): VsiFluidFloodedVoxel {
        val n = DoubleArray(3).also { it[axis] = sign.toDouble() }
        val lower = if (sign > 0) 0.0 else -1.0
        return VsiFluidFloodedVoxel(x, y, z, id, (height * 255).toInt(), 0, emptyList(),
            listOf(VsiFluidSurfaceRegion(full, n[0], n[1], n[2], lower, lower + height,
                listOf(VsiFluidSurfaceConstituent(id, height * 255)))))
    }
    private fun key(cell: VsiFluidFloodedVoxel) = FloodedFluidClientCache.pack(cell.positionX, cell.positionY, cell.positionZ)
    private fun reconstruct(cells: List<VsiFluidFloodedVoxel>) = FluidSurfaceReconstruction.build(cells) { it }
    private fun volume(faces: List<FluidSurfaceReconstruction.Face>) = faces.sumOf { face ->
        val p = face.points()
        (1 until p.size - 1).sumOf { i -> p[0].dot(Vector3d(p[i]).cross(p[i + 1])) / 6.0 }
    }
    private fun verifyVolume(cell: VsiFluidFloodedVoxel, faces: List<FluidSurfaceReconstruction.Face>) {
        val r = cell.surfaceRegions.single()
        val wanted = FluidSurfaceGeometry.volumeBelow(cube, FluidSurfaceGeometry.Plane(r.normalX, r.normalY, r.normalZ, r.upperProjection))
        assertEquals(wanted, volume(faces), 1e-10)
        for (face in faces) for (p in face.points()) for (axis in 0..2) assertTrue(p[axis] >= 0 && p[axis] <= 1)
        for (face in faces.filter { it.top() }) for (n in face.normals()) assertEquals(1.0, n.length(), 1e-12)
    }

    @Test fun `unequal adjacent fills retain each volume and meet with shared edge normals on all gravity axes`() {
        for (axis in 0..2) for (sign in listOf(-1, 1)) for (low in listOf(0.00001, 0.05, 0.3)) {
            val lateral = (axis + 1) % 3
            val pos = intArrayOf(15, 15, 15)
            val a = voxel(pos[0], pos[1], pos[2], low, axis, sign)
            pos[lateral]++
            val b = voxel(pos[0], pos[1], pos[2], 1 - low, axis, sign)
            val mesh = reconstruct(listOf(a, b))
            assertEquals(2, mesh.size)
            val left = mesh.getValue(key(a)); val right = mesh.getValue(key(b))
            verifyVolume(a, left); verifyVolume(b, right)
            fun edge(faces: List<FluidSurfaceReconstruction.Face>, coordinate: Double) = faces.filter { it.top() }
                .flatMap { face -> face.points().zip(face.normals()) }.filter { abs(it.first[lateral] - coordinate) < 1e-12 }
                .distinctBy { it.first }.sortedBy { it.first[(axis + 2) % 3] }
            val e0 = edge(left, 1.0); val e1 = edge(right, 0.0)
            assertEquals(2, e0.size); assertEquals(2, e1.size)
            e0.zip(e1).forEach { (p, q) ->
                assertEquals(p.first[axis], q.first[axis], 1e-12)
                assertEquals(p.second, q.second)
            }
            val center = left.filter { it.top() }.flatMap { it.points() }.first {
                it[lateral] == 0.5 && it[(axis + 2) % 3] == 0.5
            }
            val height = if (sign > 0) center[axis] else 1 - center[axis]
            assertTrue(height <= low + 1e-12, "The lower cell must retain its depression")
        }
    }

    @Test fun `curved connected patches keep independent volumes and do not flatten into one plane`() {
        val cells = (0..2).flatMap { x -> (0..2).map { z -> voxel(x, 0, z, if(x == 1 && z == 1) 0.2 else 0.7) } }
        val mesh = reconstruct(cells)
        assertEquals(9, mesh.size)
        cells.forEach { verifyVolume(it, mesh.getValue(key(it))) }
        val center = mesh.getValue(key(cells[4])).filter { it.top() }.flatMap { it.points() }
            .first { it.x == 0.5 && it.z == 0.5 }
        assertTrue(center.y < 0.2)
        val reverse = reconstruct(cells.reversed())
        cells.forEach { assertEquals(volume(mesh.getValue(key(it))), volume(reverse.getValue(key(it))), 1e-12) }
    }

    @Test fun `a continuous tilted plane is unchanged by reconstruction`() {
        val nx = kotlin.math.sin(0.2); val ny = kotlin.math.cos(0.2)
        val cells = (0..2).map { x ->
            val offset = 0.85 - nx * x
            val amount = FluidSurfaceGeometry.volumeBelow(cube, FluidSurfaceGeometry.Plane(nx, ny, 0.0, offset)) * 255
            voxel(x, 0, 0, 0.5).copy(surfaceRegions = listOf(VsiFluidSurfaceRegion(full, nx, ny, 0.0, 0.0, offset,
                listOf(VsiFluidSurfaceConstituent(17, amount)))))
        }
        val mesh = reconstruct(cells)
        assertTrue(mesh.isEmpty(), "An already continuous plane must keep its original geometry without extra tessellation")
    }

    @Test fun `hull gaps diagonal contacts material boundaries and layered voids are not joined`() {
        val a = voxel(0, 0, 0, 0.2)
        assertTrue(reconstruct(listOf(a, voxel(2, 0, 0, 0.8))).isEmpty())
        assertTrue(reconstruct(listOf(a, voxel(1, 0, 1, 0.8))).isEmpty())
        assertTrue(reconstruct(listOf(a, voxel(1, 0, 0, 0.8, id = 18))).isEmpty())
        val b = voxel(1, 0, 0, 0.8)
        val r = b.surfaceRegions.single()
        assertTrue(reconstruct(listOf(a, b.copy(surfaceRegions = listOf(r.copy(lowerProjection = 0.2))))).isEmpty())
        assertTrue(reconstruct(listOf(a, b.copy(surfaceRegions = listOf(r.copy(bounds = full - (8 shl 15)))))).isEmpty())
        assertTrue(reconstruct(listOf(a, b.copy(surfaceRegions = listOf(r, r)))) .isEmpty())
    }
}
