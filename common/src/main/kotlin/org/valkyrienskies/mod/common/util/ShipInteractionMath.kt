package org.valkyrienskies.mod.common.util

import org.joml.Matrix4dc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBdc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ShipInteractionMath {
    fun closingSpeed(velocity: Vector3dc, normal: Vector3dc): Double {
        if (!velocity.isFinite || !normal.isFinite || normal.lengthSquared() < 1.0e-12) return 0.0
        return max(0.0, -velocity.dot(normal) / normal.length())
    }

    fun impactForce(massA: Double, massB: Double, speed: Double, duration: Double,
        minSpeed: Double = 0.0): Double {
        if (!speed.isFinite() || speed <= 0.0 || !duration.isFinite() || duration <= 0.0
            || !minSpeed.isFinite() || speed < max(0.0, minSpeed)) return 0.0
        val inverseMass = (if (massA.isFinite() && massA > 0.0) 1.0 / massA else 0.0) +
            (if (massB.isFinite() && massB > 0.0) 1.0 / massB else 0.0)
        return if (inverseMass > 0.0) speed / inverseMass / duration else 0.0
    }

    fun impactDamage(speed: Double, threshold: Double, scale: Double, limit: Double): Float {
        if (!speed.isFinite()) return 0.0f
        return ((speed - max(0.0, threshold)) * max(0.0, scale))
            .coerceIn(0.0, max(0.0, limit)).toFloat()
    }

    /** Test a moving block box against a sensor box. Return the push normal. */
    fun sweptNormal(box: AABBdc, current: Matrix4dc, previous: Matrix4dc,
        sensor: AABBdc, sweep: Boolean): Vector3d? {
        val localCenter = Vector3d((box.minX() + box.maxX()) * 0.5,
            (box.minY() + box.maxY()) * 0.5, (box.minZ() + box.maxZ()) * 0.5)
        val center = current.transformPosition(localCenter, Vector3d())
        val start = if (sweep) previous.transformPosition(localCenter, Vector3d()) else Vector3d(center)
        val travel = center.sub(start, Vector3d())
        val sensorCenter = Vector3d((sensor.minX() + sensor.maxX()) * 0.5,
            (sensor.minY() + sensor.maxY()) * 0.5, (sensor.minZ() + sensor.maxZ()) * 0.5)
        val half = Vector3d((sensor.maxX() - sensor.minX()) * 0.5,
            (sensor.maxY() - sensor.minY()) * 0.5, (sensor.maxZ() - sensor.minZ()) * 0.5)
        val edges = arrayOf(
            current.transformDirection(Vector3d((box.maxX() - box.minX()) * 0.5, 0.0, 0.0)),
            current.transformDirection(Vector3d(0.0, (box.maxY() - box.minY()) * 0.5, 0.0)),
            current.transformDirection(Vector3d(0.0, 0.0, (box.maxZ() - box.minZ()) * 0.5)))
        val worldAxes = arrayOf(Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 1.0))
        val axes = ArrayList<Vector3d>(15)
        axes.addAll(worldAxes)
        axes.addAll(edges.map { Vector3d(it).normalize() })
        for (edge in edges) for (axis in worldAxes) axes.add(edge.cross(axis, Vector3d()))
        val offset = start.sub(sensorCenter, Vector3d())
        var enter = 0.0
        var leave = 1.0
        var hitNormal: Vector3d? = null
        var overlapNormal: Vector3d? = null
        var leastOverlap = Double.POSITIVE_INFINITY
        for (axis in axes) {
            if (axis.lengthSquared() < 1.0e-12) continue
            axis.normalize()
            val radius = edges.sumOf { abs(it.dot(axis)) } + abs(axis.x) * half.x +
                abs(axis.y) * half.y + abs(axis.z) * half.z + 0.002
            val distance = offset.dot(axis)
            val velocity = travel.dot(axis)
            val overlap = radius - abs(distance)
            if (overlap < leastOverlap) {
                leastOverlap = overlap
                overlapNormal = Vector3d(axis).mul(if (distance > 0.0) -1.0 else 1.0)
            }
            if (abs(velocity) < 1.0e-10) {
                if (abs(distance) > radius) return null
                continue
            }
            val t0 = (-radius - distance) / velocity
            val t1 = (radius - distance) / velocity
            val first = min(t0, t1)
            if (first > enter) {
                enter = first
                hitNormal = Vector3d(axis).mul(if (velocity > 0.0) 1.0 else -1.0)
            }
            leave = min(leave, max(t0, t1))
            if (enter > leave) return null
        }
        return hitNormal ?: overlapNormal
    }

    fun pointVelocity(velocity: Vector3dc, omega: Vector3dc, center: Vector3dc, point: Vector3dc): Vector3d =
        Vector3d(omega).cross(Vector3d(point).sub(center)).add(velocity)
}
