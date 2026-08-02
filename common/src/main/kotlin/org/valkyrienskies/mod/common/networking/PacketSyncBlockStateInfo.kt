package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import org.valkyrienskies.mod.client.ClientBlockInfo

/**
 * Packet to sync datapack-defined blockstate info from server to client
 */
data class PacketSyncBlockStateInfo(
    val blockState2VS: Map<String, ClientBlockInfo>
) : SimplePacket
