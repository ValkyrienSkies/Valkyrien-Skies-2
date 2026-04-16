package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

/**
 * This packet is used to let the server know which ships the player can see on their client
 */
data class PacketChangeKnownShips(
    val add: Boolean,
    val shipID: Long,
): SimplePacket
