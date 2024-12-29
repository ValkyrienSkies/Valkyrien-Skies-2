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
    var changedShipLastTick = false
    var lastShipStoodOn: ShipId? = null
        set(value) {
            ticksSinceStoodOnShip = 0
            changedShipLastTick = field != value && field != null && value != null
            field = value
        }
    var ticksSinceStoodOnShip: Int = 0
    var ignoreNextGroundStand = false
    var mountedToEntity: Boolean = false

    var lerpPositionOnShip: Vector3dc? = null
    var relativeVelocityOnShip: Vector3dc? = null
    var lerpYawOnShip: Double? = null
    var lerpHeadYawOnShip: Double? = null
    var lerpPitchOnShip: Double? = null

    var relativePositionOnShip: Vector3dc? = null
    var previousRelativeVelocityOnShip: Vector3dc? = null
    var relativeYawOnShip: Double? = null
    var relativeHeadYawOnShip: Double? = null
    var relativePitchOnShip: Double? = null

    var lerpSteps: Int = 0
    var headLerpSteps: Int = 0

    // Used by the client rendering code only
    var cachedLastPosition: Vector3dc? = null
    var restoreCachedLastPosition = false

    var serverRelativePlayerPosition: Vector3dc? = null
    var serverRelativePlayerYaw: Double? = null

    fun isEntityBeingDraggedByAShip(): Boolean {
        return (lastShipStoodOn != null) && (ticksSinceStoodOnShip < TICKS_TO_DRAG_ENTITIES) && !mountedToEntity
    }

    fun bestRelativeEntityPosition(): Vector3dc? {
        return if (serverRelativePlayerPosition != null) {
            serverRelativePlayerPosition!!
        } else if (relativePositionOnShip != null) {
            relativePositionOnShip!!
        } else {
            null
        }
    }

    companion object {
        // Max number of ticks we will drag an entity after the entity has jumped off the ship
        private const val TICKS_TO_DRAG_ENTITIES = 25
    }
}

interface IEntityDraggingInformationProvider {
    val draggingInformation: EntityDraggingInformation

    fun `vs$shouldDrag`(): Boolean
}
