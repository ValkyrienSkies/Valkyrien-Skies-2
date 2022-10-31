package org.valkyrienskies.mod.common.networking

import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.api.getAttachment
import org.valkyrienskies.core.api.setAttachment
import org.valkyrienskies.core.game.ships.ShipObjectServer
import org.valkyrienskies.core.networking.simple.register
import org.valkyrienskies.core.networking.simple.registerServerHandler
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.util.MinecraftPlayer

object VSGamePackets {

    fun register() {
        PacketPlayerDriving::class.register()
        PacketStopChunkUpdates::class.register()
        PacketRestartChunkUpdates::class.register()
    }

    fun registerHandlers() {
        PacketPlayerDriving::class.registerServerHandler { driving, iPlayer ->
            val player = (iPlayer as MinecraftPlayer).player as ServerPlayer
            if (player.vehicle is ShipMountingEntity && (player.vehicle as ShipMountingEntity).isController) {
                val seat = player.vehicle!! as ShipMountingEntity
                val ship = seat.level.getShipObjectManagingPos(seat.blockPosition())!! as ShipObjectServer
                val attachment: SeatedControllingPlayer = ship.getAttachment()
                    ?: SeatedControllingPlayer(seat.direction.opposite).apply { ship.setAttachment(this) }


                attachment.forwardImpulse = driving.impulse.z
                attachment.leftImpulse = driving.impulse.x
                attachment.upImpulse = driving.impulse.y
                attachment.sprintOn = driving.sprint
                attachment.cruise = driving.cruise
            }
        }
    }
}
