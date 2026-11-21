package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Vector3d
import org.valkyrienskies.mod.mixinducks.client.ShipWalkAnimation
import kotlin.math.sqrt
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship

object EntityLerper {
    fun prepareTick(entity: Entity) {
        val info = (entity as? IEntityDraggingInformationProvider)?.draggingInformation ?: return
        info.pendingWalkAnimation = null
        if (!info.usesShipInterpolation || entity.isControlledByLocalInstance) return
        entity.lerpTo(entity.x, entity.y, entity.z, entity.yRot, entity.xRot, 0, true)
        entity.lerpHeadTo(entity.yHeadRot, 0)
    }

    fun lerpStep(info: EntityDraggingInformation, ship: Ship, entity: Entity) {
        if (ship !is ClientShip || !info.isEntityBeingDraggedByAShip()) return
        val before = Vector3d(entity.x, entity.y, entity.z)
        val position = ship.prevTickTransform.worldToShip.transformPosition(before, Vector3d())
        val carried = ship.transform.shipToWorld.transformPosition(position, Vector3d())
        val yaw = ShipDraggingMath.yawToShip(ship.prevTickTransform.shipToWorldRotation, entity.yRot.toDouble())
        var nextYaw = yawToWorld(ship, yaw)
        if (info.lerpSteps > 0) {
            info.lerpPositionOnShip?.let { position.lerp(it, 1.0 / info.lerpSteps) }
            info.lerpYawOnShip?.let {
                nextYaw += ShipDraggingMath.wrapDegrees(yawToWorld(ship, it) - nextYaw) / info.lerpSteps
            }
            info.lerpPitchOnShip?.let { entity.xRot += ((it - entity.xRot) / info.lerpSteps).toFloat() }
            info.lerpSteps--
        }
        val next = ship.transform.shipToWorld.transformPosition(position, Vector3d())
        entity.setPos(next.x, next.y, next.z)
        info.pendingWalkAnimation?.let { includeVertical ->
            // Exclude translation and rotation of the deck itself. Do not update twice:
            // vanilla's earlier update was deferred, preserving its smoothing and phase.
            val dx = next.x - carried.x
            val dy = if (includeVertical) next.y - carried.y else 0.0
            val dz = next.z - carried.z
            (entity as? ShipWalkAnimation)?.`vs$updateShipWalkAnimation`(sqrt(dx * dx + dy * dy + dz * dz).toFloat())
            info.pendingWalkAnimation = null
        }
        val yawChange = ShipDraggingMath.wrapDegrees(nextYaw - entity.yRot)
        entity.yRot = nextYaw.toFloat()
        if (entity is LivingEntity) entity.yBodyRot += yawChange.toFloat()
        info.relativePositionOnShip = position
        info.relativeYawOnShip = yawToShip(ship, nextYaw)
        info.addedMovementLastTick = carried.sub(before)
        info.addedYawRotLastTick = yawChange
        lerpHeadStep(info, ship, entity)
    }

    fun lerpHeadStep(info: EntityDraggingInformation, ship: Ship, entity: Entity) {
        if (ship !is ClientShip) return
        val localYaw = ShipDraggingMath.yawToShip(ship.prevTickTransform.shipToWorldRotation, entity.yHeadRot.toDouble())
        var next = yawToWorld(ship, localYaw)
        if (info.headLerpSteps > 0 && info.lerpHeadYawOnShip != null) {
            next += ShipDraggingMath.wrapDegrees(yawToWorld(ship, info.lerpHeadYawOnShip!!) - next) / info.headLerpSteps
            info.headLerpSteps--
        }
        entity.yHeadRot = next.toFloat()
        info.relativeHeadYawOnShip = yawToShip(ship, next)
    }

    fun yawToWorld(ship: Ship, yaw: Double): Double =
        ShipDraggingMath.yawToWorld(ship.transform.shipToWorldRotation, yaw)

    fun yawToShip(ship: Ship, yaw: Double): Double =
        ShipDraggingMath.yawToShip(ship.transform.shipToWorldRotation, yaw)
}
