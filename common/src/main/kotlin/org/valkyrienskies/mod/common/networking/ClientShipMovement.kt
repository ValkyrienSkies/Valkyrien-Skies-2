package org.valkyrienskies.mod.common.networking

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import org.joml.Vector3d
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider

object ClientShipMovement {
    private val pending = PendingEntityUpdates<PacketEntityShipMotion>()

    fun enqueue(minecraft: Minecraft, packet: PacketEntityShipMotion) {
        val connection = minecraft.connection
        minecraft.execute {
            if (connection != null && minecraft.connection === connection) receive(packet)
        }
    }

    fun receive(packet: PacketEntityShipMotion) {
        val level = Minecraft.getInstance().level ?: return
        pending.useContext(level)
        pending.remove(packet.entityID)
        if (!apply(level, packet)) pending.put(packet.entityID, packet)
    }

    fun tick() {
        val level = Minecraft.getInstance().level
        pending.useContext(level)
        if (level != null) pending.tick { apply(level, it) }
    }

    fun clear() {
        pending.useContext(null)
    }

    internal fun apply(level: ClientLevel, packet: PacketEntityShipMotion): Boolean {
        val entity = level.getEntity(packet.entityID) ?: return false
        if (entity.isControlledByLocalInstance || entity is LocalPlayer) return true
        val ship = if (packet.shipID == -1L) null else
            level.shipObjectWorld.allShips.getById(packet.shipID) ?: return false
        val info = (entity as IEntityDraggingInformationProvider).draggingInformation
        info.applyNetworkShip(ship?.id, packet.ticksSinceContact)
        entity.syncPacketPositionCodec(packet.worldX, packet.worldY, packet.worldZ)
        entity.setOnGround(packet.onGround)
        val velocity = Vector3d(packet.xVel, packet.yVel, packet.zVel)
        if (ship == null) {
            info.addedMovementLastTick = Vector3d()
            info.addedYawRotLastTick = 0.0
            entity.lerpTo(packet.worldX, packet.worldY, packet.worldZ,
                packet.yRot.toFloat(), packet.xRot.toFloat(), 3)
            entity.lerpHeadTo(packet.headYaw.toFloat(), 3)
        } else {
            info.usesShipInterpolation = true
            info.lerpPositionOnShip = Vector3d(packet.x, packet.y, packet.z)
            info.lerpYawOnShip = packet.yRot
            info.lerpPitchOnShip = packet.xRot
            info.lerpHeadYawOnShip = packet.headYaw
            info.lerpSteps = 3
            info.headLerpSteps = 3
            ship.shipToWorld.transformDirection(velocity)
        }
        entity.setDeltaMovement(velocity.x, velocity.y, velocity.z)
        return true
    }
}
