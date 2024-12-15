package org.valkyrienskies.mod.common.util

import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object EntityLerper {
    fun lerpStep(dragInfo: EntityDraggingInformation, ship: ClientShip, entity: Entity) {
        if (dragInfo.lerpSteps > 0) {
            val currentX: Double = dragInfo.relativePositionOnShip?.x() ?: return
            val currentY: Double = dragInfo.relativePositionOnShip!!.y()
            val currentZ: Double = dragInfo.relativePositionOnShip!!.z()

            val lerpX: Double = dragInfo.lerpPositionOnShip!!.x()
            val lerpY: Double = dragInfo.lerpPositionOnShip!!.y()
            val lerpZ: Double = dragInfo.lerpPositionOnShip!!.z()

            val currentYaw: Double = dragInfo.relativeYawOnShip ?: return
            val lerpYaw: Double = dragInfo.lerpYawOnShip ?: return

            // println(previousX)
            //
            // println("previous pos : ${ship.transform.shipToWorld.transformPosition(dragInfo.previousRelativePositionOnShip!!, Vector3d())}")
            // println("current pos : ${ship.transform.shipToWorld.transformPosition(dragInfo.relativePositionOnShip!!, Vector3d())}")

            // val previousPosRelWorld: Vector3dc = ship.shipToWorld.transformPosition(dragInfo.relativePositionOnShip, Vector3d())
            // entity.setPos(previousPosRelWorld.x(), previousPosRelWorld.y(), previousPosRelWorld.z())

            val newX: Double = currentX + (lerpX - currentX) / dragInfo.lerpSteps
            val newY: Double = currentY + (lerpY - currentY) / dragInfo.lerpSteps
            val newZ: Double = currentZ + (lerpZ - currentZ) / dragInfo.lerpSteps

            val newPos = ship.shipToWorld.transformPosition(newX, newY, newZ, Vector3d())

            // val previousEntityYawOnly: Vector3dc = Vector3d(sin(-currentYaw), 0.0, cos(-currentYaw))
            // val entityYawOnly: Vector3dc = Vector3d(sin(-lerpYaw), 0.0, cos(-lerpYaw))

            // val previousYawWorld = Math.toDegrees(
            //     ship.transform.transformDirectionNoScalingFromShipToWorld(previousEntityYawOnly, Vector3d()).y()
            // )
            // val yawWorld =
            //     Math.toDegrees(ship.transform.transformDirectionNoScalingFromShipToWorld(entityYawOnly, Vector3d()).y())
            val currentYawWorld = yawToWorld(ship, currentYaw)
            val lerpYawWorld = yawToWorld(ship, lerpYaw)

            dragInfo.relativePositionOnShip = Vector3d(newX, newY, newZ)
            entity.setPos(newPos.x(), newPos.y(), newPos.z())
            // entity.setDeltaMovement(
            //     newPos.x() - previousPosRelWorld.x(),
            //     newPos.y() - previousPosRelWorld.y(),
            //     newPos.z() - previousPosRelWorld.z())

            val g = Mth.wrapDegrees(lerpYawWorld - currentYawWorld)
            val inTermsOf360 = (currentYawWorld + g / dragInfo.lerpSteps).toFloat() % 360f
            val newYaw = if (inTermsOf360 < -180) {
                inTermsOf360 + 360f
            } else if (inTermsOf360 > 180) {
                inTermsOf360 - 360f
            } else {
                inTermsOf360
            }
            entity.yRot = newYaw
            dragInfo.relativeYawOnShip = yawToShip(ship, newYaw.toDouble())

            dragInfo.lerpSteps -= 1
            println("Lerped. Remaining steps: ${dragInfo.lerpSteps}")
            // println("Change in position: ${newPos.x()}, ${newPos.y()}, ${newPos.z()}")
            println("Change in rotation: ${entity.yRot} on ${Math.toDegrees(dragInfo.relativeYawOnShip!!.toDouble())}")
        } else {
            // println("no lerp")
        }
    }

    fun lerpHeadStep(dragInfo: EntityDraggingInformation, ship: ClientShip, entity: Entity) {
        if (dragInfo.headLerpSteps > 0) {
            val currentHeadYaw: Double = dragInfo.relativeHeadYawOnShip ?: return
            val lerpHeadYaw: Double = dragInfo.lerpHeadYawOnShip ?: return

            val currentHeadYawWorld = yawToWorld(ship, currentHeadYaw)
            val lerpHeadYawWorld = yawToWorld(ship, lerpHeadYaw)

            val g = Mth.wrapDegrees(lerpHeadYawWorld - currentHeadYawWorld)
            val inTermsOf360 = (currentHeadYawWorld + g / dragInfo.headLerpSteps).toFloat() % 360f
            val newHeadYaw = if (inTermsOf360 < -180) {
                inTermsOf360 + 360f
            } else if (inTermsOf360 > 180) {
                inTermsOf360 - 360f
            } else {
                inTermsOf360
            }
            entity.yHeadRot = newHeadYaw
            dragInfo.relativeHeadYawOnShip = yawToShip(ship, newHeadYaw.toDouble())

            dragInfo.headLerpSteps--
        }
    }

    /**
     * Takes in radians, outputs degrees
     */
    fun yawToWorld(ship: Ship, yaw: Double): Double {
        val entityYawOnly: Vector3dc = Vector3d(cos(yaw), 0.0, sin(yaw))

        val newLookIdeal = ship.transform.transformDirectionNoScalingFromShipToWorld(entityYawOnly, Vector3d())

        val newYRot = atan2(newLookIdeal.z(), newLookIdeal.x())

        return Math.toDegrees(newYRot)
    }

    /**
     * Takes in degrees, outputs radians
     */
    fun yawToShip(ship: Ship, yaw: Double): Double {
        val entityYawOnly: Vector3dc = Vector3d(cos(Math.toRadians(yaw)), 0.0, sin(Math.toRadians(yaw)))

        val newLookIdeal = ship.transform.transformDirectionNoScalingFromWorldToShip(entityYawOnly, Vector3d())

        val newYRot = atan2(newLookIdeal.z(), newLookIdeal.x())

        return newYRot
    }
}
