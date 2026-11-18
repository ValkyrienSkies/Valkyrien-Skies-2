package org.valkyrienskies.mod.common.assembly

import org.joml.Matrix4dc
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.abs
import kotlin.math.acos

internal class WeldPullDrive(
    val targetId: Long,
    val sourceId: Long,
    private val startCenter: Vector3d,
    private val startRotation: Quaterniond,
    private val modelCenter: Vector3d,
    private val relativeRotation: Quaterniond,
    distance: Double,
    radius: Double,
) {
    private val angle = 2.0 * acos(abs(startRotation.dot(relativeRotation)).coerceIn(0.0, 1.0))
    private val duration = ((distance + radius * angle) / 3.0).coerceIn(0.25, 10.0)
    private var elapsed = 0.0
    val complete get() = elapsed >= duration

    fun step(target: WeldPullController.Pose, targetToWorld: Matrix4dc): WeldPullController.Goal {
        elapsed = (elapsed + 0.05).coerceAtMost(duration)
        val progress = elapsed / duration
        val center = Vector3d(startCenter).lerp(modelCenter, progress)
        val rotation = Quaterniond(startRotation).slerp(relativeRotation, progress).normalize()
        return WeldPullController.goal(target, targetToWorld, center, rotation)
    }
}
