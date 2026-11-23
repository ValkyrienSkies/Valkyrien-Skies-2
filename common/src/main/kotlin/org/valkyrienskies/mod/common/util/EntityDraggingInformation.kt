package org.valkyrienskies.mod.common.util

import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId

/**
 * This stores the information needed to properly drag entities with ships.
 */
class EntityDraggingInformation {
    var addedMovementLastTick: Vector3dc = Vector3d()
    var presentedDragShip: ShipId? = null
    // Completed interval, not current gameplay support. Landing can clear the latter before drawing.
    data class PieceDrag(val previous: org.joml.Matrix4dc, val current: ShipPhysicalPiece, val beforeYaw: Double, val turn: Double) {
        fun position(from: Vector3dc, to: Vector3dc, frame: ShipPhysicalPiece, t: Double): Vector3d =
            ShipDraggingMath.renderPosition(from, to, previous, current.worldToShip, frame.shipToWorld, t)

        fun yaw(frame: ShipPhysicalPiece, t: Double): Double {
            if (t == 0.0 || t == 1.0) return 0.0
            val start = previous.invert(org.joml.Matrix4d()).getUnnormalizedRotation(Quaterniond()).normalize()
            val shown = frame.shipToWorld.getUnnormalizedRotation(Quaterniond()).normalize()
            return ShipDraggingMath.wrapDegrees(ShipDraggingMath.draggedYaw(start, shown, beforeYaw) - t * turn)
        }
    }
    var presentedPiece: PieceDrag? = null
    var worldMountOffset: WorldMountOffset? = null
    // Heading before drag, not the player's (possibly since mouse-adjusted) current heading.
    var yawBeforeClientDrag: Double? = null
    var addedYawRotLastTick: Double = 0.0
    var changedShipLastTick = false
    var shouldImpulseMovement = false
    var lastShipStoodOn: ShipId? = null
        set(value) {
            if (field != value) {
                clearRelativeState()
                shouldImpulseMovement = value != null
                changedShipLastTick = true
            }
            field = value
            if (value != null) ticksSinceStoodOnShip = 0
        }
    var supportPiece: ShipPhysicalPiece? = null
        internal set
    internal var lastDragPiece: ShipPhysicalPiece? = null
    internal var lastPieces: List<ShipPhysicalPiece> = emptyList()

    fun worldToSupport(ship: Ship) = supportPiece?.worldToShip ?: ship.worldToShip
    fun supportRotation(ship: Ship) = supportPiece?.shipToWorld?.getUnnormalizedRotation(Quaterniond())
        ?: ship.transform.shipToWorldRotation

    var ticksSinceStoodOnShip: Int = 0
    var usesShipInterpolation = false
    var handlingClientMovement = false
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

    // Null unless vanilla requested an animation update before ship interpolation this tick.
    var pendingWalkAnimation: Boolean? = null

    var serverRelativePlayerPosition: Vector3dc? = null
    var serverRelativePlayerYaw: Double? = null

    fun applyNetworkShip(shipId: ShipId?, ticksSinceContact: Int) {
        lastShipStoodOn = shipId
        ticksSinceStoodOnShip = ticksSinceContact.coerceIn(0, TICKS_TO_DRAG_ENTITIES)
        shouldImpulseMovement = false
    }

    fun finishTick() {
        shouldImpulseMovement = false
        changedShipLastTick = false
        if (ticksSinceStoodOnShip < TICKS_TO_DRAG_ENTITIES) ticksSinceStoodOnShip++
    }

    private fun clearRelativeState() {
        supportPiece = null
        lastDragPiece = null
        lastPieces = emptyList()
        lerpPositionOnShip = null
        relativeVelocityOnShip = null
        lerpYawOnShip = null
        lerpHeadYawOnShip = null
        lerpPitchOnShip = null
        relativePositionOnShip = null
        previousRelativeVelocityOnShip = null
        relativeYawOnShip = null
        relativeHeadYawOnShip = null
        relativePitchOnShip = null
        serverRelativePlayerPosition = null
        serverRelativePlayerYaw = null
        lerpSteps = 0
        headLerpSteps = 0
        pendingWalkAnimation = null
        ignoreNextGroundStand = false
        usesShipInterpolation = false
    }

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
