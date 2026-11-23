package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Vector3d
import org.joml.Quaterniondc
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
        if (info.lerpSteps > 0) info.lerpPositionOnShip?.let { position.lerp(it, 1.0 / info.lerpSteps) }
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
        info.relativePositionOnShip = position
        info.addedMovementLastTick = carried.sub(before)
        lerpLookStep(info, ship, entity)
    }

    /** Seat position is owned by the mount, but the ship-relative look targets still advance. */
    fun lerpLookStep(info: EntityDraggingInformation, ship: ClientShip, entity: Entity) {
        val nextYaw = carriedLook(ship.prevTickTransform.shipToWorldRotation, ship.transform.shipToWorldRotation,
            entity.yRot.toDouble(), info.lerpYawOnShip, info.lerpSteps)
        if (info.lerpSteps > 0) {
            info.lerpPitchOnShip?.let { entity.xRot += ((it - entity.xRot) / info.lerpSteps).toFloat() }
            info.lerpSteps--
        }
        val yawChange = ShipDraggingMath.wrapDegrees(nextYaw - entity.yRot)
        entity.yRot = nextYaw.toFloat()
        if (entity is LivingEntity) entity.yBodyRot += yawChange.toFloat()
        info.relativeYawOnShip = yawToShip(ship, nextYaw)
        info.addedYawRotLastTick = yawChange
        lerpHeadStep(info, ship, entity)
    }

    fun lerpHeadStep(info: EntityDraggingInformation, ship: Ship, entity: Entity) {
        if (ship !is ClientShip) return
        val next = carriedLook(ship.prevTickTransform.shipToWorldRotation, ship.transform.shipToWorldRotation,
            entity.yHeadRot.toDouble(), info.lerpHeadYawOnShip, info.headLerpSteps)
        if (info.headLerpSteps > 0) info.headLerpSteps--
        entity.yHeadRot = next.toFloat()
        info.relativeHeadYawOnShip = yawToShip(ship, next)
    }

    internal fun carriedLook(previous: Quaterniondc, current: Quaterniondc, worldYaw: Double, localTarget: Double?, steps: Int): Double {
        val carried = ShipDraggingMath.yawToWorld(current, ShipDraggingMath.yawToShip(previous, worldYaw))
        if (steps <= 0 || localTarget == null) return carried
        return carried + ShipDraggingMath.wrapDegrees(ShipDraggingMath.yawToWorld(current, localTarget) - carried) / steps
    }

    fun yawToWorld(ship: Ship, yaw: Double): Double =
        ShipDraggingMath.yawToWorld(ship.transform.shipToWorldRotation, yaw)

    fun yawToShip(ship: Ship, yaw: Double): Double =
        ShipDraggingMath.yawToShip(ship.transform.shipToWorldRotation, yaw)
}
