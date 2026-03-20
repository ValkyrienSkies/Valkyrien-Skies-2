package org.valkyrienskies.mod.common.util

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId

/**
 * This stores the information needed to properly drag entities with ships.
 */
class EntityDraggingInformation {
    var addedMovementLastTick: Vector3dc = Vector3d()
    var addedYawRotLastTick: Double = 0.0
    var changedShipLastTick = false
    var shouldImpulseMovement = false
    private var predictedShipStoodOn: ShipId? = null
    var authoritativeShipStoodOn: ShipId? = null
        private set
    var lastShipStoodOn: ShipId?
        get() = authoritativeShipStoodOn ?: predictedShipStoodOn
        set(value) {
            if (value != null) {
                ticksSinceStoodOnShip = 0
            }
            shouldImpulseMovement = predictedShipStoodOn != value && value != null
            changedShipLastTick = predictedShipStoodOn != value && predictedShipStoodOn != null && value != null
            predictedShipStoodOn = value
        }
    var ticksSinceStoodOnShip: Int = 0
        set(value) {
            shouldImpulseMovement = false
            field = value
        }
    var ticksSinceLastServerPacket: Int = 0
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

    fun getPredictedShipStoodOn(): ShipId? = predictedShipStoodOn

    fun setAuthoritativeShipStoodOn(value: ShipId?) {
        authoritativeShipStoodOn = value
    }

    fun shouldUseClientPrediction(entity: Entity?): Boolean {
        return entity != null && entity.level().isClientSide && (entity is LocalPlayer || entity.isControlledByLocalInstance)
    }

    fun getDraggingShipId(entity: Entity?): ShipId? {
        return if (shouldUseClientPrediction(entity)) {
            predictedShipStoodOn ?: authoritativeShipStoodOn
        } else {
            authoritativeShipStoodOn ?: predictedShipStoodOn
        }
    }

    fun clearAuthoritativeClientState() {
        authoritativeShipStoodOn = null
        relativePositionOnShip = null
        previousRelativeVelocityOnShip = null
        relativeVelocityOnShip = null
        relativeYawOnShip = null
        relativeHeadYawOnShip = null
        relativePitchOnShip = null
        lerpPositionOnShip = null
        lerpYawOnShip = null
        lerpHeadYawOnShip = null
        lerpPitchOnShip = null
        lerpSteps = 0
        headLerpSteps = 0
        ticksSinceLastServerPacket = 0
    }

    fun clearPredictedShipState() {
        predictedShipStoodOn = null
        ticksSinceStoodOnShip = 0
        changedShipLastTick = false
        addedMovementLastTick = Vector3d()
        addedYawRotLastTick = 0.0
        shouldImpulseMovement = false
    }

    fun clearServerRelativeState() {
        serverRelativePlayerPosition = null
        serverRelativePlayerYaw = null
    }

    fun isEntityBeingDraggedByAShip(): Boolean {
        return (authoritativeShipStoodOn != null || (predictedShipStoodOn != null && ticksSinceStoodOnShip < TICKS_TO_DRAG_ENTITIES)) && !mountedToEntity
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
        const val TICKS_TO_DRAG_ENTITIES = 25 //Why was this private?
    }
}

interface IEntityDraggingInformationProvider {
    val draggingInformation: EntityDraggingInformation

    fun `vs$shouldDrag`(): Boolean

    fun `vs$isInSealedArea`(): Boolean {
        return false
    }

    fun `vs$setInSealedArea`(sealed: Boolean) {
        // Default no-op
    }

    /**
     * Shortcut for entity initializations that requires to set the entity dragged without sliding.
     */
    fun `vs$dragImmediately`(ship : Ship?)
}
