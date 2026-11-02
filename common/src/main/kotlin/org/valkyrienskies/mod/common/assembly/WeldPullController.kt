package org.valkyrienskies.mod.common.assembly

import org.joml.Matrix4dc
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin

internal object WeldPullController {
    const val MAX_SPEED = 3.0
    const val MAX_ACCELERATION = 10.0
    private const val POSITION_GAIN = 2.0
    private const val VELOCITY_GAIN = 8.0

    data class Pose(val position: Vector3dc, val rotation: Quaterniondc, val velocity: Vector3dc, val omega: Vector3dc)
    data class Goal(val position: Vector3d, val rotation: Quaterniond, val velocity: Vector3d, val omega: Vector3d)
    data class Command(val acceleration: Vector3d, val angularAcceleration: Vector3d,
                       val displacement: Double, val relativeSpeed: Double) {
        fun aligned(tolerance: Double) = displacement <= tolerance && relativeSpeed <= 0.1
    }

    fun goal(target: Pose, targetToWorld: Matrix4dc, modelCenter: Vector3dc, relativeRotation: Quaterniondc): Goal {
        val position = targetToWorld.transformPosition(Vector3d(modelCenter))
        val velocity = Vector3d(target.velocity).add(Vector3d(target.omega).cross(Vector3d(position).sub(target.position)))
        return Goal(position, Quaterniond(target.rotation).mul(relativeRotation).normalize(), velocity, Vector3d(target.omega))
    }

    fun command(source: Pose, goal: Goal, radius: Double, goalAcceleration: Vector3dc = Vector3d(),
                goalAngularAcceleration: Vector3dc = Vector3d()): Command {
        val positionError = Vector3d(goal.position).sub(source.position)
        val delta = Quaterniond(goal.rotation).mul(Quaterniond(source.rotation).invert()).normalize()
        if (delta.w < 0.0) delta.mul(-1.0)
        val vector = Vector3d(delta.x, delta.y, delta.z)
        val angle = 2.0 * atan2(vector.length(), delta.w)
        val angularError = if (vector.lengthSquared() > 1e-20) vector.normalize().mul(angle) else Vector3d()
        val desiredVelocity = limited(Vector3d(positionError).mul(POSITION_GAIN), MAX_SPEED).add(goal.velocity)
        val acceleration = limited(desiredVelocity.sub(source.velocity).mul(VELOCITY_GAIN).add(goalAcceleration), MAX_ACCELERATION)
        val angularSpeedLimit = min(1.2, MAX_SPEED / radius.coerceAtLeast(1.0))
        val angularAccelerationLimit = min(4.0, MAX_ACCELERATION / radius.coerceAtLeast(1.0))
        val desiredOmega = limited(angularError.mul(POSITION_GAIN), angularSpeedLimit).add(goal.omega)
        val angularAcceleration = limited(desiredOmega.sub(source.omega).mul(VELOCITY_GAIN).add(goalAngularAcceleration), angularAccelerationLimit)
        val displacement = positionError.length() + 2.0 * radius * sin(angle / 2.0)
        val relativeSpeed = Vector3d(source.velocity).sub(goal.velocity).length() + radius * Vector3d(source.omega).sub(goal.omega).length()
        return Command(acceleration, angularAcceleration, displacement, relativeSpeed)
    }

    fun limited(vector: Vector3d, maximum: Double): Vector3d {
        if (vector.lengthSquared() > maximum * maximum) vector.normalize().mul(maximum)
        return vector
    }
}
