package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.valkyrienskies.core.game.IEntityProvider
import org.valkyrienskies.mod.common.shipObjectWorld
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos

class EntityDragger {
    companion object {
        /**
         * Drag these entities with the ship they're standing on.
         */
        fun dragEntitiesWithShips(entities: Iterable<Entity>) {
            entities.forEach { entity ->
                val vsEntity = (entity as IEntityProvider).vsEntity
                val shipDraggingEntity = vsEntity.lastShipStoodOn
                if (shipDraggingEntity != null && vsEntity.ticksSinceStoodOnShip < 5) {
                    // Drag the entity
                    val shipData = entity.level.shipObjectWorld.queryableShipData.getById(shipDraggingEntity)
                    if (shipData != null) {
                        // region Position dragging
                        val newPosIdeal = shipData.shipTransform.shipToWorldMatrix.transformPosition(
                            shipData.prevTickShipTransform.worldToShipMatrix.transformPosition(
                                Vector3d(entity.x, entity.y, entity.z)
                            )
                        )
                        val addedMovementFromDragging = Vector3d(
                            newPosIdeal.x - entity.x,
                            newPosIdeal.y - entity.y,
                            newPosIdeal.z - entity.z
                        )

                        // TODO: Do collision on [addedMovementFromDragging], as currently this can push players into
                        //  blocks

                        // Apply [addedMovementFromDragging]
                        entity.setPos(
                            entity.x + addedMovementFromDragging.x,
                            entity.y + addedMovementFromDragging.y,
                            entity.z + addedMovementFromDragging.z
                        )
                        vsEntity.addedMovementLastTick = addedMovementFromDragging
                        // endregion

                        // region Look dragging
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
                        if (addedYRotFromDragging > 180.0) addedYRotFromDragging -= 360.0
                        if (addedYRotFromDragging < -180.0) addedYRotFromDragging += 360.0

                        // Apply [addedYRotFromDragging]
                        entity.yRot += addedYRotFromDragging.toFloat()
                        vsEntity.addedYawRotLastTick = addedYRotFromDragging
                        // endregion

                        vsEntity.ticksSinceStoodOnShip++
                    }
                }
            }
        }
    }
}
