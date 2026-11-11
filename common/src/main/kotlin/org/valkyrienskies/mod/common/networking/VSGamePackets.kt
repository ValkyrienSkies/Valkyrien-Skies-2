package org.valkyrienskies.mod.common.networking

import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.api.shipWorld
import org.valkyrienskies.mod.client.ClientBlockStateInfo
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.fluid.ClientFluidTypes
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck

object VSGamePackets {

    fun register() = with(vsCore.simplePacketNetworking) {
        PacketPlayerDriving::class.register()
        PacketStopChunkUpdates::class.register()
        PacketRestartChunkUpdates::class.register()
        PacketSyncVSEntityTypes::class.register()
        PacketSyncBlockStateInfo::class.register()
        PacketEntityShipMotion::class.register()
        PacketMobShipRotation::class.register()
        PacketPlayerShipMotion::class.register()
        PacketChangeKnownShips::class.register()
        PacketPhysicsDebug::class.register()
<<<<<<< Updated upstream
        PacketFluidParcel::class.register()
        PacketFluidParcelBatch::class.register()
    }

    fun registerHandlers() = with(vsCore.simplePacketNetworking) {
        PacketFluidParcel::class.registerClientHandler { packet ->
            Minecraft.getInstance().connection?.let {
                org.valkyrienskies.mod.common.fluid.FluidParcelRenderer.enqueue(packet, it)
            }
        }
        PacketFluidParcelBatch::class.registerClientHandler { batch ->
            Minecraft.getInstance().connection?.let {
                org.valkyrienskies.mod.common.fluid.FluidParcelRenderer.enqueue(batch.packets(), it)
=======
        PacketGravitronFreeze::class.register()
    }

    fun registerHandlers() = with(vsCore.simplePacketNetworking) {
        PacketGravitronFreeze::class.registerServerHandler { packet, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            if (packet.hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                player.server.execute { org.valkyrienskies.mod.common.item.DebugGravitron.freeze(player) }
>>>>>>> Stashed changes
            }
        }
        PacketPhysicsDebug::class.registerClientHandler { packet ->
            val minecraft = Minecraft.getInstance()
            val connection = minecraft.connection
            if (connection != null) {
                org.valkyrienskies.mod.client.debug.ShipDebugRenderer.enqueue(packet, connection)
            }
        }
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

        // sync mass/friction/elasticity to the client
        PacketSyncBlockStateInfo::class.registerClientHandler { syncBsi ->
            ClientFluidTypes.replace(syncBsi.liquidState2Fluid)
            if (syncBsi.blockState2VS.isEmpty()) {
                ClientBlockStateInfo.disable()
            } else {
                ClientBlockStateInfo.clientHasMassInfo = true
                syncBsi.blockState2VS.forEach { (id, state) ->
                    ClientBlockStateInfo.registerBlockInfo(ResourceLocation.of(id, ':'), state)
                }
            }
        }

        PacketEntityShipMotion::class.registerClientHandler { motion ->
            ClientShipMovement.enqueue(Minecraft.getInstance(), motion)
        }

        PacketPlayerShipMotion::class.registerServerHandler { motion, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            ServerShipMovement.enqueue(player, motion)
        }

        PacketChangeKnownShips::class.registerServerHandler { ships, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as PlayerKnownShipsDuck
            for (shipID in ships.shipIDs) {
                if (!ships.add || iPlayer.player.level().shipWorld?.loadedShips?.contains(shipID) == true) {
                    if (ships.add) {
                        player.vs_addKnownShip(shipID)
                    } else {
                        player.vs_removeKnownShip(shipID)
                    }
                }
            }
        }
    }
}
