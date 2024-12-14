package org.valkyrienskies.mod.common.util

import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import kotlin.math.cos
import kotlin.math.sin

object EntityLerper {
    fun lerpStep(dragInfo: EntityDraggingInformation, ship: ClientShip, entity: Entity) {
        if (dragInfo.lerpSteps > 0) {
            val previousX: Double = dragInfo.previousRelativePositionOnShip?.x() ?: return
            val previousY: Double = dragInfo.previousRelativePositionOnShip!!.y()
            val previousZ: Double = dragInfo.previousRelativePositionOnShip!!.z()

            val currentX: Double = dragInfo.relativePositionOnShip!!.x()
            val currentY: Double = dragInfo.relativePositionOnShip!!.y()
            val currentZ: Double = dragInfo.relativePositionOnShip!!.z()

            val previousYaw: Double = dragInfo.previousRelativeYawOnShip ?: return
            val currentYaw: Double = dragInfo.relativeYawOnShip ?: return

            println(previousX)

            println("previous pos : ${ship.transform.shipToWorld.transformPosition(dragInfo.previousRelativePositionOnShip!!, Vector3d())}")
            println("current pos : ${ship.transform.shipToWorld.transformPosition(dragInfo.relativePositionOnShip!!, Vector3d())}")

            val newX: Double = previousX + (currentX - previousX) / dragInfo.lerpSteps
            val newY: Double = previousY + (currentY - previousY) / dragInfo.lerpSteps
            val newZ: Double = previousZ + (currentZ - previousZ) / dragInfo.lerpSteps

            var newPos: Vector3dc = Vector3d(newX, newY, newZ)

            newPos = ship.shipToWorld.transformPosition(newPos, Vector3d())

            val previousEntityYawOnly: Vector3dc = Vector3d(sin(-previousYaw), 0.0, cos(-previousYaw))
            val entityYawOnly: Vector3dc = Vector3d(sin(-currentYaw), 0.0, cos(-currentYaw))

            val previousYawWorld = Math.toDegrees(
                ship.transform.transformDirectionNoScalingFromShipToWorld(previousEntityYawOnly, Vector3d()).y()
            )
            val yawWorld =
                Math.toDegrees(ship.transform.transformDirectionNoScalingFromShipToWorld(entityYawOnly, Vector3d()).y())

            entity.setPos(newPos.x(), newPos.y(), newPos.z())

            val g = Mth.wrapDegrees(yawWorld - previousYawWorld)
            entity.yRot = ((previousYawWorld + g.toFloat() / dragInfo.lerpSteps.toFloat()).toFloat())

            dragInfo.lerpSteps -= 1
            println("Lerped. Remaining steps: ${dragInfo.lerpSteps}")
            println("Change in position: ${newPos.x()}, ${newPos.y()}, ${newPos.z()}")
        } else {
            println("no lerp")
        }
    }
}
