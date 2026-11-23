package org.valkyrienskies.mod.common.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.mod.common.allShips
import org.valkyrienskies.mod.common.isBlockInShipyard
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.api.toMinecraft
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import org.valkyrienskies.mod.util.BugFixUtil
import org.valkyrienskies.mod.mixinducks.server.ShipMovementListener

object EntityDragger {
    fun dragEntitiesWithShips(entities: Iterable<Entity>, preTick: Boolean = false) {
        for (entity in entities) {
            val info = (entity as? IEntityDraggingInformationProvider)?.draggingInformation ?: continue
            info.presentedDragShip = null
            info.yawBeforeClientDrag = null
            info.mountedToEntity = entity.vehicle != null
            if (!isDraggable(entity)) continue
            val ship = info.lastShipStoodOn?.let { entity.level().shipObjectWorld.allShips.getById(it) }
            if (entity.isPassenger && ship is ClientShip && info.usesShipInterpolation && !entity.isControlledByLocalInstance &&
                org.valkyrienskies.mod.common.getShipMountedToData(entity, 1f)?.shipMountedTo?.id == ship.id) {
                EntityLerper.lerpLookStep(info, ship, entity)
            }
            if (entity.isPassenger && !entity.level().isBlockInShipyard(entity.blockPosition())) {
                org.valkyrienskies.mod.common.getShipMountedToData(entity, 1f)?.let { seat ->
                    WorldMountOffset.captureTree(entity)
                    val tickPos = seat.shipMountedTo.shipToWorld.transformPosition(seat.mountPosInShip, Vector3d())
                    entity.setPos(tickPos.x, tickPos.y, tickPos.z)
                    val heading = WorldMountOffset.seatHeading(entity, seat.shipMountedTo.transform.rotation,
                        WorldMountOffset.heading(entity, 1f), entity.yHeadRot, entity.xRot)
                    WorldMountOffset.placeTickTree(entity, 0f, seat.shipMountedTo.shipToWorld, heading)
                }
            }
            if (ship != null && info.isEntityBeingDraggedByAShip() && entity.vehicle == null) {
                if (EntityShipCollisionUtils.isCollidingWithUnloadedShips(entity)) continue
                val oldHeading = WorldMountOffset.heading(entity, 1f)
                if (entity.isVehicle) WorldMountOffset.captureTree(entity)
                if (ship is ClientShip && info.usesShipInterpolation && !entity.isControlledByLocalInstance) {
                    EntityLerper.lerpStep(info, ship, entity)
                    info.presentedDragShip = ship.id
                    if (entity.isVehicle) WorldMountOffset.placeTickTree(entity, net.minecraft.util.Mth.wrapDegrees(WorldMountOffset.heading(entity, 1f) - oldHeading))
                    info.finishTick()
                    continue
                }
                val position = Vector3d(entity.x, entity.y, entity.z)
                val pieces = entity.level().allShips.getSplitPieces(ship.id)
                val contactAge = info.ticksSinceStoodOnShip
                val refreshSupport = if (pieces.isEmpty()) contactAge == EntityDraggingInformation.TICKS_TO_DRAG_ENTITIES - 1 else contactAge > 0
                if (refreshSupport && entity.onGround() && !info.usesShipInterpolation) {
                    EntityShipCollisionUtils.recordSupportingShip(entity, ship.id)
                }
                val last = info.lastDragPiece ?: info.supportPiece
                val local = last?.worldToShip?.transformPosition(position, Vector3d())
                val piece = if (pieces.isEmpty()) null else {
                    val feet = entity.boundingBox.move(0.0, -0.01, 0.0).toJOML()
                    val nearby = pieces.filter { it.worldBounds.intersectsAABB(feet) }
                    val support = info.supportPiece
                    when {
                        support in nearby -> support
                        nearby.size == 1 -> nearby.first()
                        local != null -> nearby.ifEmpty { pieces }.minByOrNull {
                            it.shipToWorld.transformPosition(local, Vector3d()).distanceSquared(position)
                        }
                        else -> null
                    }
                }
                if (last != null && piece == null && local != null && info.lastPieces.isNotEmpty()) {
                    val target = ship.shipToWorld.transformPosition(local, Vector3d())
                    val survivor = info.lastPieces.minByOrNull { it.shipToWorld.transformPosition(local, Vector3d()).distanceSquared(target) }
                    if (survivor !== last) {
                        info.lastShipStoodOn = null
                        info.addedMovementLastTick = Vector3d()
                        info.addedYawRotLastTick = 0.0
                        info.finishTick()
                        continue
                    }
                }
                if (pieces.isNotEmpty() && piece == null) {
                    info.finishTick()
                    continue
                }
                val previous = if (piece != null && info.lastPieces.isNotEmpty()) {
                    val anchor = piece.worldToShip.transformPosition(position, Vector3d())
                    info.lastPieces.minBy { it.shipToWorld.transformPosition(anchor, Vector3d()).distanceSquared(position) }
                } else last ?: piece
                val previousToLocal = previous?.worldToShip ?: ship.prevTickTransform.worldToShip
                val currentToWorld = piece?.shipToWorld ?: ship.transform.shipToWorld
                info.supportPiece = piece
                info.lastDragPiece = piece
                info.lastPieces = pieces
                var movement: Vector3dc = ShipDraggingMath.movement(position, previousToLocal, currentToWorld)
                if (entity is Player && entity.isShiftKeyDown &&
                    (entity.onGround() || entity.fallDistance < entity.maxUpStep()) &&
                    !entity.abilities.flying && !entity.noPhysics && entity.deltaMovement.y <= 0.0 && movement.isFinite
                ) {
                    movement = keepSneakingPlayerOnShip(entity, ship, movement, previous, piece)
                }
                if (movement.isFinite) {
                    val collision = if (entity.noPhysics) null else
                        EntityShipCollisionUtils.adjustShipDraggingMovement(entity, ship.id, movement, piece)
                    val clipped = collision?.movement ?: movement
                    entity.setPos(entity.x + clipped.x(), entity.y + clipped.y(), entity.z + clipped.z())
                    val controllingPlayer = (entity as? ServerPlayer) ?: (entity.controllingPassenger as? ServerPlayer)
                    (controllingPlayer?.connection as? ShipMovementListener)?.`vs$onShipDrag`(entity, clipped)
                    if (info.shouldImpulseMovement && entity.isControlledByLocalInstance) {
                        val acceleration = info.addedMovementLastTick.sub(movement, Vector3d())
                        entity.push(acceleration.x, acceleration.y, acceleration.z)
                    }
                    info.addedMovementLastTick = clipped
                    val yaw = ShipDraggingMath.draggedYaw(
                        previous?.shipToWorld?.getUnnormalizedRotation(Quaterniond()) ?: ship.prevTickTransform.shipToWorldRotation,
                        info.supportRotation(ship),
                        entity.yRot.toDouble()
                    )
                    if (ship is ClientShip) info.yawBeforeClientDrag = entity.yRot.toDouble()
                    entity.yRot += yaw.toFloat()
                    entity.yHeadRot += yaw.toFloat()
                    if (entity is LivingEntity) entity.yBodyRot += yaw.toFloat()
                    info.addedYawRotLastTick = yaw
                    info.presentedDragShip = ship.id
                    if (entity.isVehicle) WorldMountOffset.placeTickTree(entity, net.minecraft.util.Mth.wrapDegrees(WorldMountOffset.heading(entity, 1f) - oldHeading))
                    if (clipped.distanceSquared(movement) > 1e-12) {
                        entity.hasImpulse = true
                        if (collision?.landedOnWorld == true) {
                            info.lastShipStoodOn = null
                            info.addedMovementLastTick = Vector3d()
                        }
                    }
                }
            } else {
                if (entity.isControlledByLocalInstance && entity.vehicle == null && info.addedMovementLastTick.isFinite) {
                    val movement = info.addedMovementLastTick
                    if (movement.lengthSquared() > 1e-6) entity.push(movement.x(), movement.y(), movement.z())
                }
                info.addedMovementLastTick = Vector3d()
                info.addedYawRotLastTick = 0.0
                if (ship == null || info.ticksSinceStoodOnShip >= EntityDraggingInformation.TICKS_TO_DRAG_ENTITIES) {
                    if (info.lastShipStoodOn != null) entity.hasImpulse = true
                    info.lastShipStoodOn = null
                }
            }
            info.finishTick()
        }
    }
    private fun keepSneakingPlayerOnShip(player: Player, ship: Ship, movement: Vector3dc,
        oldPiece: ShipPhysicalPiece?, newPiece: ShipPhysicalPiece?): Vector3dc {
        val previous = ship.prevTickTransform
        val current = ship.transform
        // Translation preserves the footprint exactly. Only changing orientation
        // or scale needs another support check after vanilla's movement check.
        if (oldPiece == null && newPiece == null && previous.shipToWorldRotation == current.shipToWorldRotation &&
            previous.shipToWorldScaling == current.shipToWorldScaling
        ) {
            return movement
        }
        val box = player.boundingBox
        val oldBounds = box.inflate(0.0, 0.05, 0.0).expandTowards(0.0, -player.maxUpStep().toDouble(), 0.0)
            .toJOML().transform(oldPiece?.worldToShip ?: previous.worldToShip)
        val nextBounds = box.move(movement.toMinecraft()).expandTowards(0.0, -player.maxUpStep().toDouble(), 0.0)
            .toJOML().transform(newPiece?.worldToShip ?: current.worldToShip)
        val localBounds = AABBd(oldBounds).union(nextBounds).toMinecraft()
        if (BugFixUtil.isCollisionBoxTooBig(localBounds)) {
            return movement
        }
        val support = ShipSneakingDrag(oldPiece?.shipToWorld ?: previous.shipToWorld, newPiece?.shipToWorld ?: current.shipToWorld)
        // One bounded shape scan on the carrying ship, shared by all fallback candidates.
        for (shape in player.level().getBlockCollisions(player, localBounds)) {
            if (oldPiece == null && newPiece == null) shape.forAllBoxes(support::addBox)
            else shape.forAllBoxes { x0,y0,z0,x1,y1,z1 ->
                val block = AABBd(x0,y0,z0,x1,y1,z1)
                val before = oldPiece ?: ShipPhysicalPiece.of(ship)
                val after = newPiece ?: ShipPhysicalPiece.of(ship)
                support.addClipped(ClippedShipBox.clip(block, before), ClippedShipBox.clip(block, after))
            }
        }
        val corrected = support.adjustDrag(box.toJOML(), movement, player.maxUpStep().toDouble())
        // Do not introduce a correction into stationary world blocks.
        return if (corrected === movement || player.level().noCollision(player, box.move(corrected.toMinecraft()))) {
            corrected
        } else {
            movement
        }
    }

    /**
     * Checks if the entity is a ServerPlayer and has a [serverRelativePlayerPosition] set. If it does, returns that, which is in ship space; otherwise, returns worldspace entity position.
     */
    fun Entity.serversidePosition(): Vec3 {
        if (this is IEntityDraggingInformationProvider && this.draggingInformation.isEntityBeingDraggedByAShip()) {
            if (this.draggingInformation.bestRelativeEntityPosition() != null) {
                return this.draggingInformation.bestRelativeEntityPosition()!!.toMinecraft()
            }
        }
        return this.position()
    }

    /**
     * Checks if the entity is a ServerPlayer and has a [serverRelativePlayerPosition] set. If it does, returns that, which is in ship space; otherwise, returns worldspace eye position.
     */
    fun Entity.serversideEyePosition(): Vec3 {
        if (this is IEntityDraggingInformationProvider && this.draggingInformation.isEntityBeingDraggedByAShip()) {
            if (this.draggingInformation.bestRelativeEntityPosition() != null) {
                return this.draggingInformation.bestRelativeEntityPosition()!!.add(0.0, this.getEyeHeight(pose).toDouble(), 0.0,
                    Vector3d())!!.toMinecraft()
            }
        }
        return this.eyePosition
    }

    /**
     * Checks if the entity is a ServerPlayer and has a [serverRelativePlayerYaw] set. If it does, returns that, which is in ship space; otherwise, returns worldspace eye rotation.
     */
    fun Entity.serversideEyeRotation(): Double {
        if (this is ServerPlayer && this is IEntityDraggingInformationProvider && this.draggingInformation.isEntityBeingDraggedByAShip()) {
            if (this.draggingInformation.serverRelativePlayerYaw != null) {
                return this.draggingInformation.serverRelativePlayerYaw!! * 180.0 / Math.PI
            }
        }
        return this.yRot.toDouble()
    }

    /**
     * Checks if the entity is a ServerPlayer and has a [serverRelativePlayerPosition] set. If it does, returns that, which is in ship space; otherwise, returns a default value.
     */
    fun Entity.serversideEyePositionOrDefault(default: Vec3): Vec3 {
        if (this is ServerPlayer && this is IEntityDraggingInformationProvider && this.draggingInformation.isEntityBeingDraggedByAShip()) {
            if (this.draggingInformation.serverRelativePlayerPosition != null) {
                return this.draggingInformation.serverRelativePlayerPosition!!.toMinecraft()
            }
        }
        return default
    }

    /**
     * Checks if the entity is a ServerPlayer and has a [serverRelativePlayerYaw] set. If it does, returns that, which is in ship space; otherwise, returns a default value.
     */
    fun Entity.serversideEyeRotationOrDefault(default: Double): Double {
        if (this is ServerPlayer && this is IEntityDraggingInformationProvider && this.draggingInformation.isEntityBeingDraggedByAShip()) {
            if (this.draggingInformation.serverRelativePlayerYaw != null) {
                return Math.toDegrees(this.draggingInformation.serverRelativePlayerYaw!!)
            }
        }
        return default
    }


    fun Entity.serversideWorldEyeRotationOrDefault(ship: Ship, default: Double): Double {
        if (this is ServerPlayer && this is IEntityDraggingInformationProvider && this.draggingInformation.isEntityBeingDraggedByAShip()) {
            if (this.draggingInformation.serverRelativePlayerYaw != null) {
                return ShipDraggingMath.yawToWorld(draggingInformation.supportRotation(ship), draggingInformation.serverRelativePlayerYaw!!)
            }
        }
        return default
    }

    /**
     * Check if the given entity should be dragged. Shipyard entities and ones marked as non-draggable return false.
     */
    @JvmStatic
    fun isDraggable(entity: Entity): Boolean {
        val provider = entity as? IEntityDraggingInformationProvider ?: return false
        if (!provider.`vs$shouldDrag`()) return false
        return !VSEntityManager.isShipyardEntity(entity) ||
            (provider.draggingInformation.supportPiece != null && !entity.level().isBlockInShipyard(entity.blockPosition()))
    }
}
