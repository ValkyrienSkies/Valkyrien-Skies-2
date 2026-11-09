package org.valkyrienskies.mod.common.fluid

import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion
import kotlin.math.abs

class FluidSteepSurfaceTest {
    private val full = (16 shl 15) or (16 shl 20) or (16 shl 25)
    private val cube = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
    private fun cell(x: Int, y: Int, z: Int, amount: Double, n: Vector3d, id: Int = 17): VsiFluidFloodedVoxel {
        val plane = FluidSurfaceGeometry.atVolume(listOf(cube), n.x, n.y, n.z, amount)
        return VsiFluidFloodedVoxel(x, y, z, id, kotlin.math.ceil(amount * 255).toInt(), 0, emptyList(),
            listOf(VsiFluidSurfaceRegion(full, n.x, n.y, n.z, minOf(0.0, n.x) + minOf(0.0, n.y) + minOf(0.0, n.z),
                plane.offset(), listOf(VsiFluidSurfaceConstituent(id, amount * 255)))))
    }
    private fun key(c: VsiFluidFloodedVoxel) = FloodedFluidClientCache.pack(c.positionX, c.positionY, c.positionZ)
    private fun volume(faces: List<FluidSurfaceReconstruction.Face>) = faces.sumOf { f ->
        val p = f.points()
        (1 until p.size - 1).sumOf { p[0].dot(Vector3d(p[it]).cross(p[it + 1])) / 6.0 }
    }
    private fun verify(c: VsiFluidFloodedVoxel, faces: List<FluidSurfaceReconstruction.Face>) {
        assertEquals(c.surfaceRegions.single().constituents.single().amount / 255.0, volume(faces), 1e-10)
        faces.forEach { face ->
            face.points().forEach { p -> for (axis in 0..2) assertTrue(p[axis].isFinite() && p[axis] in 0.0..1.0) }
            face.normals().forEach { assertEquals(1.0, it.length(), 1e-10) }
        }
    }
    private fun seam(faces: List<FluidSurfaceReconstruction.Face>, axis: Int, side: Double) = faces
        .filter { it.top() }.flatMap { it.points().zip(it.normals()) }.filter { it.first[axis] == side }
        .distinctBy { it.first }.sortedWith(compareBy({ it.first[(axis + 1) % 3] }, { it.first[(axis + 2) % 3] }))

    @Test fun `steep wedges share their surface across every face direction while keeping unequal volumes`() {
        for (axis in 0..2) for (sign in listOf(-1, 1)) {
            val n = Vector3d(0.781970, -0.328629, 0.529647).normalize().mul(sign.toDouble())
            val p = intArrayOf(15, 15, 15)
            val a = cell(p[0], p[1], p[2], 0.7, n)
            p[axis]++
            val b = cell(p[0], p[1], p[2], 0.8, n)
            val mesh = FluidSurfaceReconstruction.build(listOf(a, b)) { it }
            assertEquals(2, mesh.size)
            verify(a, mesh.getValue(key(a))); verify(b, mesh.getValue(key(b)))
            val left = seam(mesh.getValue(key(a)), axis, 1.0)
            val right = seam(mesh.getValue(key(b)), axis, 0.0)
            assertTrue(left.size >= 2)
            assertEquals(left.size, right.size)
            left.zip(right).forEach { (l, r) ->
                for (d in 0..2) if (d != axis) assertEquals(l.first[d], r.first[d], 1e-12)
                assertEquals(l.second, r.second)
            }
        }
    }

    @Test fun `saved bucket wedges and full cells retain individual volumes and shared wet boundaries`() {
        val n = Vector3d(0.781970, -0.328629, 0.529647).normalize()
        val amounts = listOf(255.0, 254.072, 255.0, 255.0, 255.0, 255.0, 236.083, 23.8467, 255.0, 202.888, 255.0, 247.083)
        val cells = amounts.mapIndexed { i, a -> cell(i / 6, (i % 6) / 2, i % 2, a / 255, n) }
        val mesh = FluidSurfaceReconstruction.build(cells) { it }
        assertEquals(cells.size, mesh.size)
        cells.forEach { verify(it, mesh.getValue(key(it))) }
        val reverse = FluidSurfaceReconstruction.build(cells.reversed()) { it }
        assertEquals(mesh, reverse)
        var previouslyClippedArea = 0.0
        fun area(points: List<Vector3d>) = (1 until points.size - 1).sumOf {
            Vector3d(points[it]).sub(points[0]).cross(Vector3d(points[it + 1]).sub(points[0])).length() * 0.5
        }
        for (c in cells) {
            val faces = mesh.getValue(key(c))
            val neighbors = cells.filter { abs(it.positionX - c.positionX) + abs(it.positionY - c.positionY) + abs(it.positionZ - c.positionZ) == 1 }
            val filtered = RetainedFluidRenderer.surfaceNeighbors(c, neighbors, faces)
            val oldCell = RetainedFluidRenderer.Cell(c, null, null, 0, neighbors, c.surfaceRegions.single(), faces)
            val rebuiltCell = RetainedFluidRenderer.Cell(c, null, null, 0, filtered, c.surfaceRegions.single(), faces)
            for (face in faces.filterNot { it.internal() }) {
                val points = face.points()
                val nFace = Vector3d(points[1]).sub(points[0]).cross(Vector3d(points[2]).sub(points[0]))
                if (nFace.lengthSquared() < 1e-24) continue
                nFace.normalize()
                val wanted = area(points)
                previouslyClippedArea += wanted - FluidInterfaceGeometry.partition(oldCell, points, nFace, false).sumOf { area(it.points()) }
                assertEquals(wanted, FluidInterfaceGeometry.partition(rebuiltCell, points, nFace, false).sumOf { area(it.points()) }, 1e-10)
            }
        }
        assertTrue(previouslyClippedArea > 1e-6, "The old planes must not cut away the reconstructed boundary")
        for (a in cells) for (b in cells) {
            val delta = intArrayOf(b.positionX - a.positionX, b.positionY - a.positionY, b.positionZ - a.positionZ)
            if (delta.sum() != 1 || delta.any { it < 0 }) continue
            val axis = delta.indexOf(1)
            fun boundary(c: VsiFluidFloodedVoxel, side: Double) = mesh.getValue(key(c)).filter { f ->
                f.internal() && f.points().all { abs(it[axis] - side) < 1e-12 }
            }.flatMap { it.points() }.map { p -> p[(axis + 1) % 3] to p[(axis + 2) % 3] }.toSet()
            assertEquals(boundary(a, 1.0), boundary(b, 0.0))
        }
    }

    @Test fun `steep joining preserves tiny amounts and does not bridge dry faces or different materials`() {
        val n = Vector3d(1.0, 1.0, 1.0).normalize()
        val tiny = cell(0, 0, 0, 0.00001, n)
        val high = cell(-1, 0, 0, 0.9, n)
        val mesh = FluidSurfaceReconstruction.build(listOf(tiny, high)) { it }
        assertEquals(2, mesh.size)
        verify(tiny, mesh.getValue(key(tiny))); verify(high, mesh.getValue(key(high)))
        assertTrue(FluidSurfaceReconstruction.build(listOf(tiny, high.copy(fluidId = 18,
            surfaceRegions = high.surfaceRegions.map { it.copy(constituents = listOf(VsiFluidSurfaceConstituent(18, 229.5))) }))) { it }.isEmpty())
        assertTrue(FluidSurfaceReconstruction.build(listOf(tiny, high.copy(positionX = 1))) { it }.isEmpty())
    }

    @Test fun `coincident corner samples still retain each half-filled cell volume`() {
        val n = Vector3d(1.0, 1.0, 1.0).normalize()
        val cells = (0..2).flatMap { x -> (0..2).flatMap { y -> (0..2).map { z -> cell(x, y, z, 0.5, n) } } }
        val mesh = FluidSurfaceReconstruction.build(cells) { it }
        assertEquals(cells.size, mesh.size)
        cells.forEach { verify(it, mesh.getValue(key(it))) }
    }

    @Test fun `an already continuous steep plane crossing cell layers remains unchanged`() {
        val n = Vector3d(0.781970, -0.328629, 0.529647).normalize()
        val cells = (0..2).flatMap { x -> (0..2).flatMap { y -> (0..2).mapNotNull { z ->
            val offset = 0.8 - n.dot(x.toDouble(), y.toDouble(), z.toDouble())
            val amount = FluidSurfaceGeometry.volumeBelow(cube, FluidSurfaceGeometry.Plane(n.x, n.y, n.z, offset))
            if (amount == 0.0) null else cell(x, y, z, amount, n)
        } } }
        assertTrue(FluidSurfaceReconstruction.build(cells) { it }.isEmpty())
    }

    @Test fun `random tilted connected patches retain individual volumes`() {
        val random = java.util.Random(904)
        repeat(100) {
            val n = Vector3d(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize()
            val cells = (0..1).flatMap { x -> (0..1).map { y -> cell(x, y, 0, random.nextDouble(), n) } }
            val mesh = FluidSurfaceReconstruction.build(cells) { it }
            for (c in cells) mesh[key(c)]?.let { verify(c, it) }
        }
    }

    @Test fun `full cells in a clipped component retain the still texture on upward exposed faces`() {
        val n = Vector3d(0.1, 1.0, 0.0).normalize()
        val cells = listOf(cell(0, 0, 0, 1.0, n), cell(1, 0, 0, 0.999, n), cell(2, 0, 0, 0.2, n))
        val mesh = FluidSurfaceReconstruction.build(cells) { it }
        assertEquals(3, mesh.size)
        cells.forEach { verify(it, mesh.getValue(key(it))) }
        val top = mesh.getValue(key(cells.first())).filter { face -> face.points().all { it.y == 1.0 } }
        assertTrue(top.isNotEmpty())
        assertTrue(top.all { it.top() && !it.internal() })
    }
}
