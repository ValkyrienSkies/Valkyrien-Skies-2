package org.valkyrienskies.mod.common.networking

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.core.api.ships.setAttachment
import org.valkyrienskies.core.impl.networking.simple.register
import org.valkyrienskies.core.impl.networking.simple.registerClientHandler
import org.valkyrienskies.core.impl.networking.simple.registerServerHandler
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.util.MinecraftPlayer

object VSGamePackets {

    fun register() {
        PacketPlayerDriving::class.register()
        PacketStopChunkUpdates::class.register()
        PacketRestartChunkUpdates::class.register()
        PacketSyncVSEntityTypes::class.register()
    }

    fun registerHandlers() {
        PacketPlayerDriving::class.registerServerHandler { driving, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            val seat = player.vehicle as? ShipMountingEntity
                ?: return@registerServerHandler
            if (seat.isController) {
                val ship = seat.level().getShipObjectManagingPos(seat.blockPosition()) as? LoadedServerShip
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
                    BuiltInRegistries.ENTITY_TYPE.byId(id),
                    ResourceLocation.tryParse(handler)?.let { VSEntityManager.getHandler(it) }
                        ?: throw IllegalStateException("No handler: $handler")
                )
            }
        }
    }
}
