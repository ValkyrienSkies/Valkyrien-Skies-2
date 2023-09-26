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

    // Used by the client rendering code only
    var cachedLastPosition: Vector3dc? = null
    var restoreCachedLastPosition = false

    fun isEntityBeingDraggedByAShip(): Boolean {
        return (lastShipStoodOn != null) && (ticksSinceStoodOnShip < 10)
    }
}

interface IEntityDraggingInformationProvider {
    val draggingInformation: EntityDraggingInformation
}
