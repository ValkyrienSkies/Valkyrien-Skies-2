package org.valkyrienskies.mod.common.assembly

import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.internal.world.VsiPhysLevel

internal class WeldPullDrive(val dimension: DimensionId, val targetId: Long, val sourceId: Long,
                            val modelCenter: Vector3d, val relativeRotation: Quaterniond,
                            val radius: Double, val tolerance: Double) {
    @Volatile var active = true
    @Volatile var gravityOverride: Double? = null
    @Volatile var settledSeconds = 0.0
    @Volatile var invalid = false
    private var previousGoalVelocity: Vector3d? = null
    private var previousGoalOmega: Vector3d? = null

    @OptIn(PhysTickOnly::class)
    fun tick(level: PhysLevel, delta: Double) {
        if (!active || level.dimension != dimension || delta <= 0.0 || !delta.isFinite()) return
        val target = level.getShipById(targetId)
        val source = level.getShipById(sourceId)
        if (target == null || source == null || source.isStatic) {
            settledSeconds = 0.0
            return
        }
        val goal = WeldPullController.goal(pose(target), target.shipToWorld, modelCenter, relativeRotation)
        val goalAcceleration = previousGoalVelocity?.let { Vector3d(goal.velocity).sub(it).div(delta) } ?: Vector3d()
        val goalAngularAcceleration = previousGoalOmega?.let { Vector3d(goal.omega).sub(it).div(delta) } ?: Vector3d()
        previousGoalVelocity = Vector3d(goal.velocity)
        previousGoalOmega = Vector3d(goal.omega)
        val command = WeldPullController.command(pose(source), goal, radius,
            WeldPullController.limited(goalAcceleration, WeldPullController.MAX_ACCELERATION),
            WeldPullController.limited(goalAngularAcceleration, 4.0))
        if (!command.acceleration.isFinite || !command.angularAcceleration.isFinite || !command.displacement.isFinite() ||
            !command.relativeSpeed.isFinite() || !source.mass.isFinite() || source.mass <= 0.0) {
            invalid = true
            return
        }
        val gravity = Vector3d((level as VsiPhysLevel).getGravity())
        gravityOverride?.let { gravity.y = it }
        val force = Vector3d(command.acceleration).sub(gravity).mul(source.mass)
        val bodyAcceleration = Quaterniond(source.transform.rotation).invert().transform(Vector3d(command.angularAcceleration))
        val bodyTorque = source.momentOfInertia.transform(bodyAcceleration)
        val torque = source.transform.rotation.transform(bodyTorque)
        if (!force.isFinite || !torque.isFinite) { invalid = true; return }
        if (!active) return
        source.applyWorldForceToBodyPos(force)
        source.applyWorldTorque(torque)
        settledSeconds = if (command.aligned(tolerance)) settledSeconds + delta else 0.0
    }

    @OptIn(PhysTickOnly::class)
    private fun pose(ship: PhysShip) = WeldPullController.Pose(ship.kinematics.position, ship.transform.rotation, ship.velocity, ship.angularVelocity)
}
