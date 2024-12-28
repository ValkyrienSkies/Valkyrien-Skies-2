package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

/**
 * This packet is used in place of [net.minecraft.network.protocol.game.ClientboundRotateHeadPacket] to update the head rotation of a mob being dragged by a ship.
 */
data class PacketMobShipRotation(val entityID: Int, val shipID: Long, val yaw: Double, val pitch: Double): SimplePacket
