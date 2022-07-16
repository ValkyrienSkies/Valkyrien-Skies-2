package org.valkyrienskies.mod.common.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.mod.common.getShipObjectEntityMountedTo
import org.valkyrienskies.mod.common.shipObjectWorld
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos

class EntityDragger {
    companion object {
        // How much we decay the addedMovement each tick after player hasn't collided with a ship for at least 10 ticks.
        private const val addedMovementDecay = 0.9

        /**
         * Drag these entities with the ship they're standing on.
         */
        fun dragEntitiesWithShips(entities: Iterable<Entity>) {
            entities.forEach { entity ->
                val shipMountedTo = entity.level.getShipObjectEntityMountedTo(entity)
                // Don't drag entities that are already mounted to a ship
                if (shipMountedTo != null) return@forEach

                val entityDraggingInformation = (entity as IEntityDraggingInformationProvider).draggingInformation

                var dragTheEntity = false
                var addedMovement: Vector3dc? = null
                var addedYRot = 0.0

                val shipDraggingEntity = entityDraggingInformation.lastShipStoodOn
                if (shipDraggingEntity != null) {
                    if (entityDraggingInformation.isEntityBeingDraggedByAShip()) {
                        // Compute how much we should drag the entity
                        val shipData = entity.level.shipObjectWorld.queryableShipData.getById(shipDraggingEntity)
                        if (shipData != null) {
                            dragTheEntity = true

                            // region Compute position dragging
                            val newPosIdeal = shipData.shipTransform.shipToWorldMatrix.transformPosition(
                                shipData.prevTickShipTransform.worldToShipMatrix.transformPosition(
                                    Vector3d(entity.x, entity.y, entity.z)
                                )
                            )
                            addedMovement = Vector3d(
                                newPosIdeal.x - entity.x,
                                newPosIdeal.y - entity.y,
                                newPosIdeal.z - entity.z
                            )
                            // endregion

                            // region Compute look dragging
                            if (abs(entity.xRot) < 89.5) {
                                val newLookIdeal = shipData.shipTransform.shipToWorldMatrix.transformDirection(
                                    shipData.prevTickShipTransform.worldToShipMatrix.transformDirection(
                                        entity.getViewVector(1.0f).toJOML()
                                    )
                                )

                                // Get the X and Y rotation from [newLookIdeal]
                                val newXRot = asin(-newLookIdeal.y())
                                val xRotCos = cos(newXRot)
                                val newYRot = -atan2(newLookIdeal.x() / xRotCos, newLookIdeal.z() / xRotCos)

                                // The Y rotation of the entity before dragging
                                var entityYRotCorrected = entity.yRot % 360.0
                                // Limit [entityYRotCorrected] to be between -180 to 180 degrees
                                if (entityYRotCorrected < -180.0) entityYRotCorrected += 360.0
                                if (entityYRotCorrected > 180.0) entityYRotCorrected -= 360.0

                                // The Y rotation of the entity after dragging
                                val newYRotAsDegrees = Math.toDegrees(newYRot)
                                // Limit [addedYRotFromDragging] to be between -180 to 180 degrees
                                var addedYRotFromDragging = newYRotAsDegrees - entityYRotCorrected
                                if (addedYRotFromDragging < -180.0) addedYRotFromDragging += 360.0
                                if (addedYRotFromDragging > 180.0) addedYRotFromDragging -= 360.0

                                addedYRot = addedYRotFromDragging
                            }
                            // endregion
                        }
                    } else {
                        dragTheEntity = true
                        addedMovement = entityDraggingInformation.addedMovementLastTick
                            .mul(addedMovementDecay, Vector3d())
                        addedYRot = entityDraggingInformation.addedYawRotLastTick * addedMovementDecay
                    }
                }

                if (dragTheEntity && addedMovement != null && addedMovement.isFinite && addedYRot.isFinite()) {
                    // TODO: Do collision on [addedMovement], as currently this can push players into
                    //       blocks
                    // Apply [addedMovement]
                    entity.setPos(
                        entity.x + addedMovement.x(),
                        entity.y + addedMovement.y(),
                        entity.z + addedMovement.z()
                    )
                    entityDraggingInformation.addedMovementLastTick = addedMovement

                    // Apply [addedYRot]
                    // Don't apply it to server players to fix rotation of placed blocks
                    if (addedYRot.isFinite() && entity !is ServerPlayer) {
                        entity.yRot += addedYRot.toFloat()
                        entity.yHeadRot += addedYRot.toFloat()
                        entityDraggingInformation.addedYawRotLastTick = addedYRot
                    }
                }
                entityDraggingInformation.ticksSinceStoodOnShip++
            }
        }
    }
}
