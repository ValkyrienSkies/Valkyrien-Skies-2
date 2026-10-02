package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import org.valkyrienskies.mod.common.blockstate.StateProperties

/**
 * Packet to sync datapack-defined blockstate info from server to client
 */
data class PacketSyncBlockStateProperties(
    val blockState2properties: Map<String, Map<String, StateProperties>> = mapOf(),
) : SimplePacket
