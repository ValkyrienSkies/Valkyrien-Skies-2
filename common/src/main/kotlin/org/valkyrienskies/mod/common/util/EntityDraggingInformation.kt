package org.valkyrienskies.mod.common.util

import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.properties.ShipId

/**
 * This stores the information needed to properly drag entities with ships.
 */
class EntityDraggingInformation {
    var addedMovementLastTick: Vector3dc = Vector3d()
    var addedYawRotLastTick: Double = 0.0
    var lastShipStoodOn: ShipId? = null
        set(value) {
            ticksSinceStoodOnShip = 0
            field = value
        }
    var ticksSinceStoodOnShip: Int = 0
    var mountedToEntity: Boolean = false

    var relativePositionOnShip: Vector3dc? = null
    var relativeVelocityOnShip: Vector3dc? = null
    var relativeYawOnShip: Double? = null

    var previousRelativePositionOnShip: Vector3dc? = null
    var previousRelativeVelocityOnShip: Vector3dc? = null
    var previousRelativeYawOnShip: Double? = null

    var lerpSteps: Int = 0

    // Used by the client rendering code only
    var cachedLastPosition: Vector3dc? = null
    var restoreCachedLastPosition = false

    fun isEntityBeingDraggedByAShip(): Boolean {
        return (lastShipStoodOn != null) && (ticksSinceStoodOnShip < TICKS_TO_DRAG_ENTITIES) && !mountedToEntity
    }

    companion object {
        // Max number of ticks we will drag an entity after the entity has jumped off the ship
        private const val TICKS_TO_DRAG_ENTITIES = 20
    }
}

interface IEntityDraggingInformationProvider {
    val draggingInformation: EntityDraggingInformation

    fun `vs$shouldDrag`(): Boolean
}
