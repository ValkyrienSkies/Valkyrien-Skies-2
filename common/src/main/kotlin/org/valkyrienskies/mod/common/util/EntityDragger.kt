package org.valkyrienskies.mod.common.util

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.api.toJOML
import org.valkyrienskies.mod.api.toMinecraft
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.EntityLerper.yawToWorld
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
            if (shipDraggingEntity != null && entity.vehicle == null && isDraggable(entity)) {
                if (entityDraggingInformation.isEntityBeingDraggedByAShip()) {
                    // Compute how much we should drag the entity
                    val shipData = entity.level().shipObjectWorld.allShips.getById(shipDraggingEntity)
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
                        val yViewRot = entity.yRot.toDouble()

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
                        if (entityYRotCorrected <= -180.0) entityYRotCorrected += 360.0
                        if (entityYRotCorrected >= 180.0) entityYRotCorrected -= 360.0

                        // The Y rotation of the entity after dragging
                        val newYRotAsDegrees = Math.toDegrees(newYRot)
                        // Limit [addedYRotFromDragging] to be between -180 to 180 degrees
                        var addedYRotFromDragging = newYRotAsDegrees - entityYRotCorrected
                        if (addedYRotFromDragging <= -180.0) addedYRotFromDragging += 360.0
                        if (addedYRotFromDragging >= 180.0) addedYRotFromDragging -= 360.0

                        addedYRot = addedYRotFromDragging
                        // endregion
                    }
                } else {
                    addedMovement = Vector3d(entityDraggingInformation.addedMovementLastTick)
                    addedYRot = 0.0
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

                if(entityDraggingInformation.shouldImpulseMovement && (!entity.level().isClientSide || entity is LocalPlayer)) { //This is the first Tick on the ship. Also, should push the entity in server side only and propagate the result.
                    val acceleration = Vector3d(entityDraggingInformation.addedMovementLastTick) // if it was on a different ship last tick, consider that too.
                        .sub(addedMovement) // relative velocity to current ship.
                    entity.push(acceleration.x, acceleration.y, acceleration.z)
                }

                entityDraggingInformation.addedMovementLastTick = addedMovement

                // Apply [addedYRot]
                if (addedYRot.isFinite()) {
                    if (!entity.level().isClientSide()) {
                        if (entity !is ServerPlayer) {
                            entity.yRot = ((entity.yRot + addedYRot.toFloat()) + 360f) % 360f
                            entity.yHeadRot = ((entity.yHeadRot + addedYRot.toFloat()) + 360f) % 360f
                            if(entity is LivingEntity) {
                                entity.yBodyRot = ((entity.yBodyRot + addedYRot.toFloat()) + 360f) % 360f
                            }
                        } else {
                            entity.yRot = Mth.wrapDegrees(entity.yRot + addedYRot.toFloat())
                            entity.yHeadRot = Mth.wrapDegrees(entity.yHeadRot + addedYRot.toFloat())
                            entity.yBodyRot = Mth.wrapDegrees(entity.yBodyRot + addedYRot.toFloat())
                        }
                    } else {
                        if (!entity.isControlledByLocalInstance && entity !is Player) {
                            entity.yRot = Mth.wrapDegrees(entity.yRot + addedYRot.toFloat())
                            entity.yHeadRot = Mth.wrapDegrees(entity.yHeadRot + addedYRot.toFloat())
                            if(entity is LivingEntity) {
                                entity.yBodyRot = Mth.wrapDegrees(entity.yBodyRot + addedYRot.toFloat())
                            }
                        } else {
                            entity.yRot = (entity.yRot + addedYRot.toFloat())
                            entity.yHeadRot = (entity.yHeadRot + addedYRot.toFloat())
                            if(entity is LivingEntity) {
                                entity.yBodyRot = (entity.yBodyRot + addedYRot.toFloat())
                            }
                        }
                    }

                    entityDraggingInformation.addedYawRotLastTick = addedYRot
                }
            } else if ((!entity.level().isClientSide || entity is LocalPlayer) && entityDraggingInformation.addedMovementLastTick.length() > 1e-3) {
                entity.push(entityDraggingInformation.addedMovementLastTick.x(),
                    entityDraggingInformation.addedMovementLastTick.y(),
                    entityDraggingInformation.addedMovementLastTick.z())
                entityDraggingInformation.addedMovementLastTick = Vector3d()
                entityDraggingInformation.addedYawRotLastTick = 0.0
            }
            entityDraggingInformation.ticksSinceStoodOnShip++
            entityDraggingInformation.mountedToEntity = entity.vehicle != null
        }
    }

    /**
     * Checks if the entity is a ServerPlayer and has a [serverRelativePlayerPosition] set. If it does, returns that, which is in ship space; otherwise, returns worldspace eye position.
     */
    fun Entity.serversideEyePosition(): Vec3 {
        if (this is ServerPlayer && this is IEntityDraggingInformationProvider && this.draggingInformation.isEntityBeingDraggedByAShip()) {
            if (this.draggingInformation.serverRelativePlayerPosition != null) {
                return this.draggingInformation.serverRelativePlayerPosition!!.toMinecraft()
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

    @JvmStatic
    fun backOff(vec3: Vec3, ship: Ship, player: Player, cLevel: Level): Vec3 {
        // Early exit: if there's ground ahead in the movement direction, allow full movement
        // This lets us walk away from edges without being blocked
        val movementLength = vec3.length()
        if (movementLength > 0) {
            val checkDistance = movementLength + player.bbWidth
            val checkOffset = vec3.normalize().scale(checkDistance)
            val checkPosWorld = player.position().add(checkOffset)
            // Transform to ship space for the raycast (where ship blocks actually exist)
            val checkPosShip = ship.worldToShip.transformPosition(checkPosWorld.toJOML(), Vector3d())
            val checkStart = Vec3(checkPosShip.x(), checkPosShip.y(), checkPosShip.z())
            val checkEnd = Vec3(checkPosShip.x(), checkPosShip.y() - player.maxUpStep().toDouble(), checkPosShip.z())
            val clipContext = ClipContext(checkStart, checkEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
            val result = cLevel.clip(clipContext)
            if (result.type == HitResult.Type.BLOCK) {
                // Ground ahead, allow full movement
                return vec3
            }
        }

        val transformedVec = ship.worldToShip.transformDirection(vec3.toJOML(), Vector3d())
        var d = transformedVec.x
        val e = transformedVec.y
        var f = transformedVec.z
        val halfWidth = player.bbWidth / 4.0 // players walking on a moving ship should be careful

        val stepDown = player.maxUpStep().toDouble()
        // Get player position in ship space for axis-specific checks
        val playerPosShip = ship.worldToShip.transformPosition(player.position().toJOML(), Vector3d())

        // Early exit for X axis: check both sides (offset by halfWidth in Z) for ground ahead
        if (d != 0.0) {
            val xCheckDist = d + (if (d > 0) -halfWidth else halfWidth)
            // Check in ship space (where ship blocks actually exist)
            val xStart1 = Vec3(playerPosShip.x() + xCheckDist, playerPosShip.y(), playerPosShip.z() + halfWidth)
            val xEnd1 = Vec3(xStart1.x, xStart1.y - stepDown, xStart1.z)
            val xResult1 = cLevel.clip(ClipContext(xStart1, xEnd1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))

            val xStart2 = Vec3(playerPosShip.x() + xCheckDist, playerPosShip.y(), playerPosShip.z() - halfWidth)
            val xEnd2 = Vec3(xStart2.x, xStart2.y - stepDown, xStart2.z)
            val xResult2 = cLevel.clip(ClipContext(xStart2, xEnd2, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))

            if (xResult1.type == HitResult.Type.BLOCK || xResult2.type == HitResult.Type.BLOCK) {
                d = transformedVec.x // Ground ahead on at least one side, keep original X movement
            } else {
                while (d != 0.0 && !isValidWalkablePosition(cLevel, ship, player, d, Direction.EAST, halfWidth)) {
                    if (d < 0.05 && d >= -0.05) {
                        d = 0.0
                    } else if (d > 0.0) {
                        d -= 0.05
                    } else {
                        d += 0.05
                    }
                }
            }
        }

        // Early exit for Z axis: check both sides (offset by halfWidth in X) for ground ahead
        if (f != 0.0) {
            val zCheckDist = f + (if (f > 0) -halfWidth else halfWidth)
            // Check in ship space (where ship blocks actually exist)
            val zStart1 = Vec3(playerPosShip.x() + halfWidth, playerPosShip.y(), playerPosShip.z() + zCheckDist)
            val zEnd1 = Vec3(zStart1.x, zStart1.y - stepDown, zStart1.z)
            val zResult1 = cLevel.clip(ClipContext(zStart1, zEnd1, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))

            val zStart2 = Vec3(playerPosShip.x() - halfWidth, playerPosShip.y(), playerPosShip.z() + zCheckDist)
            val zEnd2 = Vec3(zStart2.x, zStart2.y - stepDown, zStart2.z)
            val zResult2 = cLevel.clip(ClipContext(zStart2, zEnd2, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))

            if (zResult1.type == HitResult.Type.BLOCK || zResult2.type == HitResult.Type.BLOCK) {
                f = transformedVec.z // Ground ahead on at least one side, keep original Z movement
            } else {
                while (f != 0.0 && !isValidWalkablePosition(cLevel, ship, player, f, Direction.SOUTH, halfWidth)) {
                    if (f < 0.05 && f >= -0.05) {
                        f = 0.0
                    } else if (f > 0.0) {
                        f -= 0.05
                    } else {
                        f += 0.05
                    }
                }
            }
        }

        val motionLength = sqrt(d * d + e * e + f * f)
        return ship.shipToWorld.transformDirection(Vector3d(d, e, f)).normalize().mul(motionLength).toMinecraft()
    }

    private fun isValidWalkablePosition(
        level: Level, ship: Ship, player: Player, step: Double, dir: Direction, halfWidth: Double
    ): Boolean {
        val clipContext = stepTowardsEdge(ship, player, step, dir, halfWidth)
        val result = level.clip(clipContext)
        if (result.type != HitResult.Type.BLOCK) {
            return false
        }

        val hitShip = level.getLoadedShipManagingPos(result.blockPos)
        if (hitShip != null) {
            val hitSide = result.direction.normal.toJOMLD()
            val upDir: Vector3dc = Vector3d(0.0, 1.0, 0.0)
            val hitSideInWorld = hitShip.shipToWorld.transformDirection(hitSide, Vector3d()).normalize()
            // If the hit side is not facing up, we can't walk on it
            val dot = hitSideInWorld.dot(upDir)
            if (dot < 0.5 && dot > 0.001) {
                return false
            }
        }
        return true
    }

    private fun stepTowardsEdge(
        ship: Ship, player: Player, step: Double, dir: Direction, halfWidth: Double
    ): ClipContext {
        // Calculate check position: movement - half player width in movement direction
        val dirVec = ship.transform.shipToWorld.transformDirection(Vector3d(dir.step())).normalize()
        val offsetDistance = step + (if (step > 0) -halfWidth else halfWidth)
        val potentialPosition = player.position().add(dirVec.mul(offsetDistance).toMinecraft())

        // Raycast downward to check for ground
        val maxDistPos = potentialPosition.add(0.0, -player.maxUpStep().toDouble(), 0.0)

        return ClipContext(
            potentialPosition, maxDistPos, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, player
        )
    }
    /**
     * Check if the given entity should be dragged. Shipyard entities and ones marked as non-draggable return false.
     */
    @JvmStatic
    fun isDraggable(entity: Entity): Boolean {
        return !VSEntityManager.isShipyardEntity(entity) && entity is IEntityDraggingInformationProvider && (entity as IEntityDraggingInformationProvider).`vs$shouldDrag`()
    }
}
