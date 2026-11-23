package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.mod.common.fluid.FluidParcelRenderer
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.api.shipWorld
import org.valkyrienskies.mod.client.ClientBlockStateInfo
import org.valkyrienskies.mod.common.assembly.SeamlessChunksManager
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.fluid.ClientFluidTypes
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck

/**
 * Registers VS2's network messages ([VsMessages]) and their handlers.
 *
 * GAME handlers run on the game thread in arrival order (the same queue as vanilla packets), so they never
 * re-dispatch with `execute` themselves (that would re-queue them behind later packets).
 */
object VSGamePackets {

    fun register() = VsMessages.register(vsCore)

    fun registerHandlers() {
        VsMessages.FLUID_PARCEL.onReceiveFromServer { packet ->
            Minecraft.getInstance().connection?.let { FluidParcelRenderer.enqueue(packet, it) }
        }
        VsMessages.FLUID_PARCEL_BATCH.onReceiveFromServer { batch ->
            Minecraft.getInstance().connection?.let { FluidParcelRenderer.enqueue(batch.packets(), it) }
        }
        VsMessages.GRAVITRON_FREEZE.onReceiveFromClient { packet, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            if (packet.hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                org.valkyrienskies.mod.common.item.DebugGravitron.freeze(player)
            }
        }
        VsMessages.PHYSICS_DEBUG.onReceiveFromServer { packet ->
            Minecraft.getInstance().connection?.let {
                org.valkyrienskies.mod.client.debug.ShipDebugRenderer.enqueue(packet, it)
            }
        }
        VsMessages.PLAYER_DRIVING.onReceiveFromClient { driving, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            val seat = player.vehicle as? ShipMountingEntity ?: return@onReceiveFromClient
            if (seat.isController) {
                val ship = seat.level().getLoadedShipManagingPos(seat.blockPosition()) as? LoadedServerShip
                    ?: return@onReceiveFromClient

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
        VsMessages.SYNC_ENTITY_TYPES.onReceiveFromServer { syncEntities ->
            syncEntities.entity2Handler.forEach { (id, handler) ->
                VSEntityManager.pair(
                    BuiltInRegistries.ENTITY_TYPE.byId(id),
                    ResourceLocation.tryParse(handler)?.let { VSEntityManager.getHandler(it) }
                        ?: throw IllegalStateException("No handler: $handler")
                )
            }
        }

        // sync mass/friction/elasticity to the client
        VsMessages.SYNC_BLOCK_STATE_INFO.onReceiveFromServer(::applyBlockStateInfo)

        VsMessages.ENTITY_SHIP_MOTION.onReceiveFromServer { motion -> ClientShipMovement.receive(motion) }

        VsMessages.PLAYER_SHIP_MOTION.onReceiveFromClient { motion, iPlayer ->
            ServerShipMovement.handle((iPlayer as MinecraftPlayer).player as ServerPlayer, motion)
        }

        VsMessages.CHANGE_KNOWN_SHIPS.onReceiveFromClient { ships, iPlayer ->
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

        // Seamless chunk copying: one handler for the whole game, forwarding to the current connection's manager.
        VsMessages.STOP_CHUNK_UPDATES.onReceiveFromServer { SeamlessChunksManager.get()?.onStopUpdates(it) }
        VsMessages.RESTART_CHUNK_UPDATES.onReceiveFromServer { SeamlessChunksManager.get()?.onRestartUpdates(it) }
    }

    /** Applies a block state info sync on the client. */
    @JvmStatic
    fun applyBlockStateInfo(syncBsi: PacketSyncBlockStateInfo) {
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
}
