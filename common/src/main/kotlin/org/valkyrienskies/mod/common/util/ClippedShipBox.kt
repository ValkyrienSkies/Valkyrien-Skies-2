package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import kotlin.math.abs
import kotlin.math.atan2

internal class ClippedShipBox(val faces: List<List<Vector3d>>, val axes: List<Vector3d>) {
    enum class Location { RETAINED, CUT, REMOVED }

    val vertices = faces.flatten().distinct()
    fun intersects(bounds: AABBdc): Boolean {
        val center = Vector3d((bounds.minX() + bounds.maxX()) * 0.5, (bounds.minY() + bounds.maxY()) * 0.5,
            (bounds.minZ() + bounds.maxZ()) * 0.5)
        val half = Vector3d(bounds.maxX(), bounds.maxY(), bounds.maxZ()).sub(center)
        for (axis in axes) {
            val mid = axis.dot(center)
            val radius = abs(axis.x) * half.x + abs(axis.y) * half.y + abs(axis.z) * half.z
            if (vertices.all { axis.dot(it) < mid - radius - 1e-8 } ||
                vertices.all { axis.dot(it) > mid + radius + 1e-8 }) return false
        }
        return true
    }

    companion object {
        private val boxFaces = arrayOf(intArrayOf(0,2,6,4), intArrayOf(1,5,7,3),
            intArrayOf(0,4,5,1), intArrayOf(2,3,7,6), intArrayOf(0,1,3,2), intArrayOf(4,6,7,5))
        private val worldAxes = listOf(Vector3d(1.0,0.0,0.0), Vector3d(0.0,1.0,0.0), Vector3d(0.0,0.0,1.0))

        fun classify(box: AABBdc, piece: ShipPhysicalPiece): Location {
            val transform = piece.shipToWorld
            val hx = (box.maxX() - box.minX()) * 0.5
            val hy = (box.maxY() - box.minY()) * 0.5
            val hz = (box.maxZ() - box.minZ()) * 0.5
            val center = transform.transformPosition(Vector3d(box.minX() + hx, box.minY() + hy, box.minZ() + hz))
            var result = Location.RETAINED
            for (plane in piece.clippingPlanes) {
                val x = plane.x()
                val y = plane.y()
                val z = plane.z()
                val radius = hx * abs(x * transform.m00() + y * transform.m01() + z * transform.m02()) +
                    hy * abs(x * transform.m10() + y * transform.m11() + z * transform.m12()) +
                    hz * abs(x * transform.m20() + y * transform.m21() + z * transform.m22())
                val d = x * center.x + y * center.y + z * center.z + plane.w()
                if (d + radius <= 1e-9) return Location.REMOVED
                if (d - radius < -1e-9) result = Location.CUT
            }
            return result
        }

        fun intersectsPlacement(pieces: List<ShipPhysicalPiece>, shape: VoxelShape,
                                pos: BlockPos, playerBounds: AABBdc): Boolean {
            for (box in shape.toAabbs()) {
                val local = box.move(pos).toJOML()
                for (piece in pieces) {
                    if (clip(local, piece)?.intersects(playerBounds) == true) return true
                }
            }
            return false
        }

        fun clip(box: AABBdc, piece: ShipPhysicalPiece): ClippedShipBox? {
            val corners = Array(8) { i -> piece.shipToWorld.transformPosition(Vector3d(
                if (i and 1 == 0) box.minX() else box.maxX(),
                if (i and 2 == 0) box.minY() else box.maxY(),
                if (i and 4 == 0) box.minZ() else box.maxZ())) }
            var faces = boxFaces.map { face -> face.map { corners[it] } }
            for (plane in piece.clippingPlanes) {
                fun distance(p: Vector3dc) = plane.x()*p.x() + plane.y()*p.y() + plane.z()*p.z() + plane.w()
                if (faces.all { face -> face.all { distance(it) >= 0 } }) continue
                if (faces.none { face -> face.any { distance(it) > 1e-9 } }) return null
                val cap = ArrayList<Vector3d>()
                val next = ArrayList<List<Vector3d>>()
                for (face in faces) {
                    val kept = ArrayList<Vector3d>()
                    var start = face.last()
                    var a = distance(start)
                    for (end in face) {
                        val b = distance(end)
                        if ((a < 0) != (b < 0)) {
                            val point = start.lerp(end, a / (a - b), Vector3d())
                            kept.add(point)
                            if (cap.none { it.distanceSquared(point) < 1e-18 }) cap.add(point)
                        }
                        if (b >= 0) kept.add(end)
                        start = end
                        a = b
                    }
                    if (kept.size >= 3) next.add(kept)
                }
                if (cap.size >= 3) {
                    val center = cap.fold(Vector3d()) { acc, p -> acc.add(p) }.div(cap.size.toDouble())
                    val u = cap.first().sub(center, Vector3d()).normalize()
                    val v = Vector3d(plane.x(), plane.y(), plane.z()).cross(u).normalize()
                    cap.sortBy { p -> val r = p.sub(center, Vector3d()); atan2(r.dot(v), r.dot(u)) }
                    next.add(cap)
                }
                faces = next
            }
            val axes = ArrayList<Vector3d>(worldAxes)
            fun axis(direction: Vector3d) {
                if (direction.lengthSquared() <= 1e-18) return
                direction.normalize()
                if (axes.none { abs(it.dot(direction)) > 1.0 - 1e-12 }) axes.add(direction)
            }
            for (face in faces) {
                val edges = face.indices.map { i -> face[(i+1) % face.size].sub(face[i], Vector3d()) }
                for (i in edges.indices) {
                    axis(edges[i].cross(edges[(i+1) % edges.size], Vector3d()))
                    for (unit in worldAxes) axis(edges[i].cross(unit, Vector3d()))
                }
            }
            return ClippedShipBox(faces, axes)
        }
    }
}
