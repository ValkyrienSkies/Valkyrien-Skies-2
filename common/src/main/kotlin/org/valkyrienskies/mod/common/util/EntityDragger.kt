package org.valkyrienskies.mod.common.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.api.toMinecraft
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.EntityLerper.yawToWorld
import org.valkyrienskies.mod.util.BugFixUtil
import org.valkyrienskies.mod.mixinducks.server.ShipMovementListener

object EntityDragger {
    fun dragEntitiesWithShips(entities: Iterable<Entity>, preTick: Boolean = false) {
        for (entity in entities) {
            val info = (entity as? IEntityDraggingInformationProvider)?.draggingInformation ?: continue
            info.mountedToEntity = entity.vehicle != null
            if (!isDraggable(entity)) continue
            val ship = info.lastShipStoodOn?.let { entity.level().shipObjectWorld.allShips.getById(it) }
            if (ship != null && info.isEntityBeingDraggedByAShip() && entity.vehicle == null) {
                if (EntityShipCollisionUtils.isCollidingWithUnloadedShips(entity)) continue
                if (ship is ClientShip && info.usesShipInterpolation && !entity.isControlledByLocalInstance) {
                    EntityLerper.lerpStep(info, ship, entity)
                    info.finishTick()
                    continue
                }
                val position = Vector3d(entity.x, entity.y, entity.z)
                var movement: Vector3dc = ShipDraggingMath.movement(
                    position, ship.prevTickTransform.worldToShip, ship.transform.shipToWorld
                )
                if (entity is Player && entity.isShiftKeyDown &&
                    (entity.onGround() || entity.fallDistance < entity.maxUpStep()) &&
                    !entity.abilities.flying && !entity.noPhysics && entity.deltaMovement.y <= 0.0 && movement.isFinite
                ) {
                    movement = keepSneakingPlayerOnShip(entity, ship, movement)
                }
                if (movement.isFinite) {
                    val clipped = if (entity.noPhysics) movement else
                        EntityShipCollisionUtils.adjustShipDraggingMovement(entity, ship.id, movement)
                    entity.setPos(entity.x + clipped.x(), entity.y + clipped.y(), entity.z + clipped.z())
                    val controllingPlayer = (entity as? ServerPlayer) ?: (entity.controllingPassenger as? ServerPlayer)
                    (controllingPlayer?.connection as? ShipMovementListener)?.`vs$onShipDrag`(entity, clipped)
                    if (info.shouldImpulseMovement && entity.isControlledByLocalInstance) {
                        val acceleration = info.addedMovementLastTick.sub(movement, Vector3d())
                        entity.push(acceleration.x, acceleration.y, acceleration.z)
                    }
                    info.addedMovementLastTick = clipped
                    val yaw = ShipDraggingMath.wrapDegrees(Math.toDegrees(
                        ShipDraggingMath.shipYaw(ship.prevTickTransform.shipToWorldRotation) -
                            ShipDraggingMath.shipYaw(ship.transform.shipToWorldRotation)
                    ))
                    entity.yRot += yaw.toFloat()
                    entity.yHeadRot += yaw.toFloat()
                    if (entity is LivingEntity) entity.yBodyRot += yaw.toFloat()
                    info.addedYawRotLastTick = yaw
                    if (clipped.distanceSquared(movement) > 1e-12) {
                        entity.hasImpulse = true
                        if (ShipDraggingMath.landedOnWorld(movement, clipped)) {
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
    private fun keepSneakingPlayerOnShip(player: Player, ship: Ship, movement: Vector3dc): Vector3dc {
        val previous = ship.prevTickTransform
        val current = ship.transform
        // Translation preserves the footprint exactly. Only changing orientation
        // or scale needs another support check after vanilla's movement check.
        if (previous.shipToWorldRotation == current.shipToWorldRotation &&
            previous.shipToWorldScaling == current.shipToWorldScaling
        ) {
            return movement
        }
        val box = player.boundingBox
        val oldBounds = box.inflate(0.0, 0.05, 0.0).expandTowards(0.0, -player.maxUpStep().toDouble(), 0.0)
            .toJOML().transform(previous.worldToShip)
        val nextBounds = box.move(movement.toMinecraft()).expandTowards(0.0, -player.maxUpStep().toDouble(), 0.0)
            .toJOML().transform(current.worldToShip)
        val localBounds = AABBd(oldBounds).union(nextBounds).toMinecraft()
        if (BugFixUtil.isCollisionBoxTooBig(localBounds)) {
            return movement
        }
        val support = ShipSneakingDrag(previous.shipToWorld, current.shipToWorld)
        // One bounded shape scan on the carrying ship, shared by all fallback candidates.
        for (shape in player.level().getBlockCollisions(player, localBounds)) {
            shape.forAllBoxes(support::addBox)
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
                return yawToWorld(ship, this.draggingInformation.serverRelativePlayerYaw!!)
            }
        }
        return default
    }

    /**
     * Check if the given entity should be dragged. Shipyard entities and ones marked as non-draggable return false.
     */
    @JvmStatic
    fun isDraggable(entity: Entity): Boolean {
        return !VSEntityManager.isShipyardEntity(entity) && entity is IEntityDraggingInformationProvider && (entity as IEntityDraggingInformationProvider).`vs$shouldDrag`()
    }
}
