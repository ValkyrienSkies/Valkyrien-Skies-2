package org.valkyrienskies.mod.common.util

import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object EntityLerper {

    /**
     * Called from preAiStep. This function lerps the entity's movement while keeping it locked relative to the ship.
     */
    fun lerpStep(dragInfo: EntityDraggingInformation, refship: Ship, entity: Entity) {
        if (refship !is ClientShip) {
            return
        }
        val ship = refship as ClientShip
        if (dragInfo.lerpSteps > 0) {
            if (dragInfo.changedShipLastTick) {
                dragInfo.lerpSteps = 1
                dragInfo.changedShipLastTick = false
            }
            val currentX: Double = dragInfo.relativePositionOnShip?.x() ?: return
            val currentY: Double = dragInfo.relativePositionOnShip!!.y()
            val currentZ: Double = dragInfo.relativePositionOnShip!!.z()

            val lerpX: Double = dragInfo.lerpPositionOnShip!!.x()
            val lerpY: Double = dragInfo.lerpPositionOnShip!!.y()
            val lerpZ: Double = dragInfo.lerpPositionOnShip!!.z()

            val currentYaw: Double = dragInfo.relativeYawOnShip ?: return
            val lerpYaw: Double = dragInfo.lerpYawOnShip ?: return

            val newX: Double = currentX + (lerpX - currentX) / dragInfo.lerpSteps
            val newY: Double = currentY + (lerpY - currentY) / dragInfo.lerpSteps
            val newZ: Double = currentZ + (lerpZ - currentZ) / dragInfo.lerpSteps

            val newPos = ship.shipToWorld.transformPosition(newX, newY, newZ, Vector3d())

            val currentYawWorld = yawToWorld(ship, currentYaw)
            val lerpYawWorld = yawToWorld(ship, lerpYaw)

            dragInfo.relativePositionOnShip = Vector3d(newX, newY, newZ)
            entity.setPos(newPos.x(), newPos.y(), newPos.z())

            val g = Mth.wrapDegrees(lerpYawWorld - currentYawWorld)
            val newYaw = (currentYawWorld + g / dragInfo.lerpSteps).toFloat()

            entity.yRot = newYaw
            dragInfo.relativeYawOnShip = yawToShip(ship, newYaw.toDouble())

            dragInfo.lerpSteps -= 1
        }
    }

    /**
     * Additional function to lerp head separately, as it's a separate packet.
     */
    fun lerpHeadStep(dragInfo: EntityDraggingInformation, refship: Ship, entity: Entity) {
        if (refship !is ClientShip) {
            return
        }
        val ship = refship as ClientShip
        if (dragInfo.headLerpSteps > 0) {
            val currentHeadYaw: Double = dragInfo.relativeHeadYawOnShip ?: return
            val lerpHeadYaw: Double = dragInfo.lerpHeadYawOnShip ?: return

            val currentHeadYawWorld = yawToWorld(ship, currentHeadYaw)
            val lerpHeadYawWorld = yawToWorld(ship, lerpHeadYaw)

            val newHeadYaw = currentHeadYawWorld + Mth.wrapDegrees(lerpHeadYawWorld - currentHeadYawWorld) / dragInfo.headLerpSteps.toFloat()
            entity.xRot += (dragInfo.lerpPitchOnShip!!.toFloat() - entity.xRot) / dragInfo.headLerpSteps.toFloat()
            entity.yHeadRot = newHeadYaw.toFloat()
            dragInfo.relativeHeadYawOnShip = yawToShip(ship, newHeadYaw.toDouble())

            dragInfo.headLerpSteps--
        }
    }

    /**
     * Converts yaw to worldspace.
     *
     * Takes in radians, outputs degrees.
     */
    fun yawToWorld(ship: Ship, yaw: Double): Double {
        val entityYawOnly: Vector3dc = Vector3d(sin(yaw), 0.0, cos(yaw))

        val newLookIdeal = ship.transform.transformDirectionNoScalingFromShipToWorld(entityYawOnly, Vector3d())

        val newYRot = atan2(newLookIdeal.x(), newLookIdeal.z())

        return Mth.wrapDegrees(newYRot * 180.0 / Math.PI)
    }

    /**
     * Converts yaw to shipspace.
     *
     * Takes in degrees, outputs radians
     */
    fun yawToShip(ship: Ship, yaw: Double): Double {
        val entityYawOnly: Vector3dc = Vector3d(sin(yaw * Math.PI / 180.0), 0.0, cos(yaw * Math.PI / 180.0))

        val newLookIdeal = ship.transform.transformDirectionNoScalingFromWorldToShip(entityYawOnly, Vector3d())

        val newYRot = atan2(newLookIdeal.x(), newLookIdeal.z())
        return newYRot
    }
}
