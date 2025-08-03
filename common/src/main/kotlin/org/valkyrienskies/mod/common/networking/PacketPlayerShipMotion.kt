package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

/**
 * This packet is used to update the player's relative position and yaw rotation while being dragged by a ship, alongside
 * [net.minecraft.network.protocol.game.ServerboundMovePlayerPacket].
 */
data class PacketPlayerShipMotion(val shipID: Long, val x: Double, val y: Double, val z: Double, val yRot: Double): SimplePacket
