package org.valkyrienskies.mod.common.networking

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.server.level.ServerPlayer
import org.joml.Vector3d
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.EntityDraggingInformation
import org.valkyrienskies.mod.common.util.EntityLerper
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider

object ServerShipMovement {
    fun enqueue(player: ServerPlayer, packet: PacketPlayerShipMotion) {
        player.server.execute { handle(player, packet) }
    }

    fun handle(player: ServerPlayer, packet: PacketPlayerShipMotion) {
        if (player.connection.player !== player || !player.connection.isAcceptingMessages || !isValid(packet)) return
        val entity = if (packet.vehicleID == -1) {
            if (player.isPassenger) return
            player
        } else {
            player.rootVehicle.takeIf {
                it !== player && it.id == packet.vehicleID && it.controllingPassenger === player
            } ?: return
        }
        val ship = player.serverLevel().shipObjectWorld.allShips.getById(packet.shipID) ?: return
        if (ship.chunkClaimDimension != player.level().dimensionId) return
        val position = ship.shipToWorld.transformPosition(Vector3d(packet.x, packet.y, packet.z))
        if (!position.isFinite) return
        val yaw = EntityLerper.yawToWorld(ship, packet.yRot).toFloat()
        val info = (entity as IEntityDraggingInformationProvider).draggingInformation
        info.handlingClientMovement = true
        try {
            if (packet.vehicleID == -1) {
                player.connection.handleMovePlayer(ServerboundMovePlayerPacket.PosRot(
                    position.x, position.y, position.z, yaw, packet.xRot, packet.onGround
                ))
            } else {
                val buffer = FriendlyByteBuf(Unpooled.buffer())
                try {
                    buffer.writeDouble(position.x).writeDouble(position.y).writeDouble(position.z)
                    buffer.writeFloat(yaw).writeFloat(packet.xRot)
                    player.connection.handleMoveVehicle(ServerboundMoveVehiclePacket(buffer))
                } finally {
                    buffer.release()
                }
            }
        } finally {
            info.handlingClientMovement = false
        }
        if (position.distanceSquared(entity.x, entity.y, entity.z) < 1e-8) {
            info.applyNetworkShip(ship.id, packet.ticksSinceContact)
            info.serverRelativePlayerPosition = ship.worldToShip.transformPosition(Vector3d(entity.x, entity.y, entity.z))
            info.serverRelativePlayerYaw = EntityLerper.yawToShip(ship, entity.yRot.toDouble())
        }
    }

    fun isValid(packet: PacketPlayerShipMotion): Boolean =
        packet.shipID != -1L && packet.x.isFinite() && packet.y.isFinite() && packet.z.isFinite() &&
            packet.yRot.isFinite() && packet.xRot.isFinite() &&
            packet.ticksSinceContact in 0 until EntityDraggingInformation.TICKS_TO_DRAG_ENTITIES
}
