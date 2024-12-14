package org.valkyrienskies.mod.common.networking

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.core.api.ships.setAttachment
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.vsCore
import kotlin.math.cos
import kotlin.math.sin

object VSGamePackets {

    fun register() = with(vsCore.simplePacketNetworking) {
        PacketPlayerDriving::class.register()
        PacketStopChunkUpdates::class.register()
        PacketRestartChunkUpdates::class.register()
        PacketSyncVSEntityTypes::class.register()
        PacketEntityShipMotion::class.register()
    }

    fun registerHandlers() = with(vsCore.simplePacketNetworking) {
        PacketPlayerDriving::class.registerServerHandler { driving, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            val seat = player.vehicle as? ShipMountingEntity
                ?: return@registerServerHandler
            if (seat.isController) {
                val ship = seat.level.getShipObjectManagingPos(seat.blockPosition()) as? LoadedServerShip
                    ?: return@registerServerHandler

                val attachment: SeatedControllingPlayer = ship.getAttachment()
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
                    Registry.ENTITY_TYPE.byId(id),
                    ResourceLocation.tryParse(handler)?.let { VSEntityManager.getHandler(it) }
                        ?: throw IllegalStateException("No handler: $handler")
                )
            }
        }

        PacketEntityShipMotion::class.registerClientHandler { setMotion ->
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@registerClientHandler
            val entity = level.getEntity(setMotion.entityID) ?: return@registerClientHandler

            if (entity is LocalPlayer && entity.isLocalPlayer) return@registerClientHandler

            val ship = level.shipObjectWorld.allShips.getById(setMotion.shipID)
                ?: return@registerClientHandler

            if (entity is IEntityDraggingInformationProvider) {
                entity.draggingInformation.previousRelativePositionOnShip = entity.draggingInformation.relativePositionOnShip
                entity.draggingInformation.previousRelativeVelocityOnShip = entity.draggingInformation.relativeVelocityOnShip
                entity.draggingInformation.previousRelativeYawOnShip = entity.draggingInformation.relativeYawOnShip

                entity.draggingInformation.relativePositionOnShip = Vector3d(setMotion.z, setMotion.y, setMotion.z)
                entity.draggingInformation.relativeVelocityOnShip = Vector3d(setMotion.zVel, setMotion.yVel, setMotion.zVel)
                entity.draggingInformation.relativeYawOnShip = setMotion.yRot

                val previousWorldPosition = if (entity.draggingInformation.previousRelativePositionOnShip != null) {
                    ship.renderTransform.shipToWorld.transformPosition(Vector3d(entity.draggingInformation.previousRelativePositionOnShip))
                } else {
                    Vector3d(entity.x, entity.y, entity.z)
                }
                val worldPosition = ship.renderTransform.shipToWorld.transformPosition(Vector3d(setMotion.x, setMotion.y, setMotion.z))
                entity.setPacketCoordinates(worldPosition.x, worldPosition.y, worldPosition.z)
                val worldVelocity = ship.renderTransform.shipToWorld.transformDirection(Vector3d(setMotion.xVel, setMotion.yVel, setMotion.zVel))
                entity.setDeltaMovement(worldVelocity.x, worldVelocity.y, worldVelocity.z)
                entity.xRot = Math.toDegrees(setMotion.xRot).toFloat()
                entity.draggingInformation.lerpSteps = 3

                entity.setPos(previousWorldPosition.x, previousWorldPosition.y, previousWorldPosition.z)
                entity.lerpTo(worldPosition.x, worldPosition.y, worldPosition.z, Math.toDegrees(setMotion.yRot).toFloat(), Math.toDegrees(setMotion.xRot).toFloat(), 3, true)
            }
        }
    }
}
