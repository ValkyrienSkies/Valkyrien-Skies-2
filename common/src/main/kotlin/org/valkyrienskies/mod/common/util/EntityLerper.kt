package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship

object EntityLerper {
    fun prepareTick(entity: Entity) {
        val info = (entity as? IEntityDraggingInformationProvider)?.draggingInformation ?: return
        if (!info.usesShipInterpolation || entity.isControlledByLocalInstance) return
        entity.lerpTo(entity.x, entity.y, entity.z, entity.yRot, entity.xRot, 0)
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
