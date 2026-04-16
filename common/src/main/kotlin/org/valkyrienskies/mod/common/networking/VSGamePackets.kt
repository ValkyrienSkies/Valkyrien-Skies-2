package org.valkyrienskies.mod.common.networking

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.api.shipWorld
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.mod.common.util.EntityLerper
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck

object VSGamePackets {

    fun register() = with(vsCore.simplePacketNetworking) {
        PacketPlayerDriving::class.register()
        PacketStopChunkUpdates::class.register()
        PacketRestartChunkUpdates::class.register()
        PacketSyncVSEntityTypes::class.register()
        PacketEntityShipMotion::class.register()
        PacketMobShipRotation::class.register()
        PacketPlayerShipMotion::class.register()
        PacketChangeKnownShips::class.register()
    }

    fun registerHandlers() = with(vsCore.simplePacketNetworking) {
        PacketPlayerDriving::class.registerServerHandler { driving, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            val seat = player.vehicle as? ShipMountingEntity
                ?: return@registerServerHandler
            if (seat.isController) {
                val ship = seat.level().getLoadedShipManagingPos(seat.blockPosition()) as? LoadedServerShip
                    ?: return@registerServerHandler

                val attachment: SeatedControllingPlayer = ship.getAttachment<SeatedControllingPlayer>()
                    ?: SeatedControllingPlayer(seat.direction.opposite).apply { ship.setAttachment(this) }

                attachment.forwardImpulse = driving.impulse.z
                attachment.leftImpulse = driving.impulse.x
                attachment.upImpulse = driving.impulse.y
                attachment.sprintOn = driving.sprint
                attachment.cruise = driving.cruise
            }
        }

        // Syncs the entity handlers to the client
        PacketSyncVSEntityTypes::class.registerClientHandler { syncEntities ->
            syncEntities.entity2Handler.forEach { (id, handler) ->
                VSEntityManager.pair(
                    BuiltInRegistries.ENTITY_TYPE.byId(id),
                    ResourceLocation.tryParse(handler)?.let { VSEntityManager.getHandler(it) }
                        ?: throw IllegalStateException("No handler: $handler")
                )
            }
        }

        PacketEntityShipMotion::class.registerClientHandler { setMotion ->
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@registerClientHandler
            val entity = level.getEntity(setMotion.entityID) ?: return@registerClientHandler

            if (entity.isControlledByLocalInstance || mc.player?.id == entity.id) return@registerClientHandler

            if (entity is IEntityDraggingInformationProvider) {
                entity.draggingInformation.lastShipStoodOnServerWriteOnly = if (setMotion.shipID != -1L) {
                    setMotion.shipID
                } else {
                    null
                }
            }
            val ship = level.shipObjectWorld.allShips.getById(setMotion.shipID)

            if (ship == null) {
                if (entity is IEntityDraggingInformationProvider) {
                    entity.draggingInformation.lastShipStoodOn = null
                }
                return@registerClientHandler
            }

            if (entity is IEntityDraggingInformationProvider) {

                if (entity.draggingInformation.lastShipStoodOn == null || entity.draggingInformation.lastShipStoodOn != setMotion.shipID) {
                    entity.draggingInformation.lastShipStoodOn = if (setMotion.shipID != -1L) {
                        setMotion.shipID
                    } else {
                        null
                    }
                    entity.draggingInformation.ignoreNextGroundStand = true
                }
                entity.draggingInformation.shouldImpulseMovement = false
                entity.draggingInformation.ticksSinceLastServerPacket = 0

                entity.draggingInformation.relativePositionOnShip = ship.worldToShip.transformPosition(
                    Vector3d(entity.x, entity.y, entity.z)
                )
                entity.draggingInformation.previousRelativeVelocityOnShip = entity.draggingInformation.relativeVelocityOnShip
                entity.draggingInformation.relativeYawOnShip = EntityLerper.yawToShip(ship, entity.yRot.toDouble())

                entity.draggingInformation.lerpPositionOnShip = Vector3d(setMotion.x, setMotion.y, setMotion.z)
                entity.draggingInformation.relativeVelocityOnShip = Vector3d(setMotion.xVel, setMotion.yVel, setMotion.zVel)
                entity.draggingInformation.lerpYawOnShip = setMotion.yRot

                val previousWorldPosition = if (entity.draggingInformation.relativePositionOnShip != null) {
                    ship.renderTransform.shipToWorld.transformPosition(Vector3d(entity.draggingInformation.relativePositionOnShip))
                } else {
                    Vector3d(entity.x, entity.y, entity.z)
                }
                val worldPosition = ship.transform.shipToWorld.transformPosition(Vector3d(setMotion.x, setMotion.y, setMotion.z))
                entity.syncPacketPositionCodec(worldPosition.x, worldPosition.y, worldPosition.z)
                val worldVelocity = ship.transform.shipToWorld.transformDirection(Vector3d(setMotion.xVel, setMotion.yVel, setMotion.zVel))
                entity.setDeltaMovement(worldVelocity.x, worldVelocity.y, worldVelocity.z)
                entity.draggingInformation.lerpSteps = 3

                if(entity !is LivingEntity) { // EntityLerper is called only if the entity is ai-controlled. In other cases lerp is manual.
                    entity.setPos(previousWorldPosition.x, previousWorldPosition.y, previousWorldPosition.z)
                    entity.lerpTo(worldPosition.x, worldPosition.y, worldPosition.z, Math.toDegrees(setMotion.yRot).toFloat(), Math.toDegrees(setMotion.xRot).toFloat(), 3, true)
                }
            }
        }

        PacketMobShipRotation::class.registerClientHandler { setRotation ->
            val mc = Minecraft.getInstance() ?: return@registerClientHandler
            val level = mc.level ?: return@registerClientHandler
            val entity = level.getEntity(setRotation.entityID) ?: return@registerClientHandler

            if (entity.isControlledByLocalInstance || entity is LocalPlayer) return@registerClientHandler

            if (entity is IEntityDraggingInformationProvider) {
                entity.draggingInformation.lastShipStoodOnServerWriteOnly = if (setRotation.shipID != -1L) {
                    setRotation.shipID
                } else {
                    null
                }
            }

            val ship = level.shipObjectWorld.allShips.getById(setRotation.shipID)
                ?: return@registerClientHandler

            if (entity is IEntityDraggingInformationProvider) {
                if (entity.draggingInformation.lastShipStoodOn == null || entity.draggingInformation.lastShipStoodOn != setRotation.shipID) {
                    entity.draggingInformation.lastShipStoodOn = if (setRotation.shipID != -1L) {
                        setRotation.shipID
                    } else {
                        null
                    }
                    entity.draggingInformation.ignoreNextGroundStand = true
                }
                entity.draggingInformation.relativeHeadYawOnShip = EntityLerper.yawToShip(ship, entity.yHeadRot.toDouble())
                entity.draggingInformation.lerpHeadYawOnShip = setRotation.yaw
                entity.draggingInformation.relativePitchOnShip = entity.xRot.toDouble()
                entity.draggingInformation.lerpPitchOnShip = setRotation.pitch
                entity.draggingInformation.headLerpSteps = 3
            }
        }

        // PacketRequestEntityMotion::class.registerServerHandler { motion, player ->
        //     val player = (player as MinecraftPlayer).player as ServerPlayer?
        //         ?: return@registerServerHandler
        //     val level = player.level() ?: return@registerServerHandler
        //     val entity = level.getEntity(motion.entityId) ?: return@registerServerHandler
        //
        //     val dragInfo = (entity as? IEntityDraggingInformationProvider)?.draggingInformation
        //         ?: return@registerServerHandler
        //
        //     val ship = if (dragInfo.lastShipStoodOn != null) {
        //         (level as ServerLevel).shipObjectWorld.allShips.getById(dragInfo.lastShipStoodOn!!)
        //     } else {
        //         null
        //     }
        //
        //     val position = ship.getWorldToShip().transformPosition(new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
        //     if (dragInfo.getServerRelativePlayerPosition() != null) {
        //         position = new Vector3d(dragInfo.getServerRelativePlayerPosition());
        //     }
        //     Vector3d motion = ship.getTransform().getWorldToShip().transformDirection(new Vector3d(entity.getDeltaMovement().x(), entity.getDeltaMovement().y(), entity.getDeltaMovement().z()), new Vector3d());
        //     double yaw;
        //     if (!(t instanceof ClientboundRotateHeadPacket)) {
        //         yaw = EntityLerper.INSTANCE.yawToShip(ship, entity.getYRot());
        //     } else {
        //         yaw = EntityLerper.INSTANCE.yawToShip(ship, entity.getYHeadRot());
        //     }
        //     double pitch = entity.getXRot();
        //     val vsPacket = PacketEntityShipMotion(motion.entityId, ship?.id ?: -1L,
        //         position.x, position.y, position.z,
        //         motion.x, motion.y, motion.z,
        //         yaw, pitch);
        // }

        PacketPlayerShipMotion::class.registerServerHandler { motion, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer?
                ?: return@registerServerHandler

            if (player is IEntityDraggingInformationProvider) {
                if (player.draggingInformation.lastShipStoodOn == null || player.draggingInformation.lastShipStoodOn != motion.shipID) {
                    player.draggingInformation.lastShipStoodOn = if (motion.shipID != -1L) {
                        motion.shipID
                    } else {
                        null
                    }
                }
                player.draggingInformation.serverRelativePlayerPosition = Vector3d(motion.x, motion.y, motion.z)
                if (player.level() != null) {
                    val sLevel = (player.level() as ServerLevel)
                    val ship = sLevel.shipObjectWorld.allShips.getById(motion.shipID)
                    if (ship != null) {
                        val posUpdate = ship.shipToWorld.transformPosition(Vector3d(motion.x, motion.y, motion.z), Vector3d()).toMinecraft()
                        if ((player as PlayerDuck).vs_handledMovePacket()) {
                            player.setPos(posUpdate.x, posUpdate.y, posUpdate.z)
                            player.vs_setHandledMovePacket(false)
                        } else {
                            player.vs_setQueuedPositionUpdate(posUpdate)
                        }
                    }
                }
                player.draggingInformation.serverRelativePlayerYaw = motion.yRot
            }
        }

        PacketEntityShipMotion::class.registerServerHandler { motion, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            val entity = player.level().getEntity(motion.entityID) ?: return@registerServerHandler

            if (entity is IEntityDraggingInformationProvider) {
                if (entity.draggingInformation.lastShipStoodOn == null || entity.draggingInformation.lastShipStoodOn != motion.shipID) {
                    entity.draggingInformation.lastShipStoodOn = if (motion.shipID != -1L) {
                        motion.shipID
                    } else {
                        null
                    }
                    entity.draggingInformation.ignoreNextGroundStand = true
                }

                entity.draggingInformation.relativePositionOnShip = Vector3d(motion.x, motion.y, motion.z)
                entity.draggingInformation.relativeYawOnShip = motion.yRot

                if ((player.level() as ServerLevel).shipObjectWorld.allShips.getById(motion.shipID) != null) {
                    entity.setPos(player.level().toWorldCoordinates(Vec3(motion.x, motion.y, motion.z)))
                }
            }
        }


        PacketChangeKnownShips::class.registerServerHandler { ships, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as PlayerKnownShipsDuck
            if (!ships.add || iPlayer.player.level().shipWorld?.loadedShips?.contains(ships.shipID) == true) {
                if (ships.add) {
                    player.vs_addKnownShip(ships.shipID)
                } else {
                    player.vs_removeKnownShip(ships.shipID)
                }
            }
        }
    }
}
