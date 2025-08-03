package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

/**
 * This packet is used to update an entity's relative position while being dragged by a ship, in place of
 * [net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket], [net.minecraft.network.protocol.game.ClientboundMoveEntityPacket], and [net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket].
 */
data class PacketEntityShipMotion(
    val entityID: Int,
    val shipID: Long,
    val x: Double,
    val y: Double,
    val z: Double,
    val xVel: Double,
    val yVel: Double,
    val zVel: Double,
    val yRot: Double,
    val xRot: Double,
): SimplePacket
