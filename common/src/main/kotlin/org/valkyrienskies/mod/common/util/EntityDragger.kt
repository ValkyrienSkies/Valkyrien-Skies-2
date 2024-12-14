package org.valkyrienskies.mod.common.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.getShipMountedTo
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object EntityDragger {
    // How much we decay the addedMovement each tick after player hasn't collided with a ship for at least 10 ticks.
    private const val ADDED_MOVEMENT_DECAY = 0.9

    /**
     * Drag these entities with the ship they're standing on.
     */
    fun dragEntitiesWithShips(entities: Iterable<Entity>, preTick: Boolean = false) {
        for (entity in entities) {
            val entityDraggingInformation = (entity as IEntityDraggingInformationProvider).draggingInformation

            var dragTheEntity = false
            var addedMovement: Vector3dc? = null
            var addedYRot = 0.0

            val shipDraggingEntity = entityDraggingInformation.lastShipStoodOn

            // Only drag entities that aren't mounted to vehicles
            if (shipDraggingEntity != null && entity.vehicle == null) {
                if (entityDraggingInformation.isEntityBeingDraggedByAShip()) {
                    // Compute how much we should drag the entity
                    val shipData = entity.level.shipObjectWorld.allShips.getById(shipDraggingEntity)
                    if (shipData != null) {
                        dragTheEntity = true

                        val entityReferencePos: Vector3dc = if (preTick) {
                            Vector3d(entity.x, entity.y, entity.z)
                        } else {
                            Vector3d(entity.xo, entity.yo, entity.zo)
                        }

                        val referenceTransform = if (shipData is ClientShip) shipData.transform else shipData.transform

                        // region Compute position dragging
                        val newPosIdeal: Vector3dc = referenceTransform.shipToWorld.transformPosition(
                            shipData.prevTickTransform.worldToShip.transformPosition(
                                Vector3d(entityReferencePos)
                            )
                        )
                        addedMovement = newPosIdeal.sub(entityReferencePos, Vector3d())
                        // endregion

                        // region Compute look dragging
                        val yViewRot = if (preTick) {
                            entity.yRot
                        } else {
                            entity.yRotO
                        }.toDouble()

                        // Get the y-look vector of the entity only using y-rotation, ignore x-rotation
                        val entityLookYawOnly =
                            Vector3d(sin(-Math.toRadians(yViewRot)), 0.0, cos(-Math.toRadians(yViewRot)))

                        val newLookIdeal = referenceTransform.shipToWorld.transformDirection(
                            shipData.prevTickTransform.worldToShip.transformDirection(
                                entityLookYawOnly
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
                        // endregion
                    }
                } else {
                    dragTheEntity = true
                    addedMovement = entityDraggingInformation.addedMovementLastTick
                        .mul(ADDED_MOVEMENT_DECAY, Vector3d())
                    addedYRot = entityDraggingInformation.addedYawRotLastTick * ADDED_MOVEMENT_DECAY
                }
            }

            if (dragTheEntity && addedMovement != null && addedMovement.isFinite && addedYRot.isFinite()) {
                // TODO: Do collision on [addedMovement], as currently this can push players into
                //       blocks
                // Apply [addedMovement]
                val newBB = entity.boundingBox.move(addedMovement.toMinecraft())
                entity.boundingBox = newBB
                entity.setPos(
                    entity.x + addedMovement.x(),
                    entity.y + addedMovement.y(),
                    entity.z + addedMovement.z()
                )
                entityDraggingInformation.addedMovementLastTick = addedMovement

                // Apply [addedYRot]
                // Don't apply it to server players to fix rotation of placed blocks
                if (addedYRot.isFinite()) {
                    entity.yRot += addedYRot.toFloat()
                    entity.yHeadRot += addedYRot.toFloat()
                    entityDraggingInformation.addedYawRotLastTick = addedYRot
                }
            }
            entityDraggingInformation.ticksSinceStoodOnShip++
            entityDraggingInformation.mountedToEntity = entity.vehicle != null
        }
    }
}
