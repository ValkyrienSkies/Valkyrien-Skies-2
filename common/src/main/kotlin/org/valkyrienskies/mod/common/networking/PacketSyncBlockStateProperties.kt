package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import org.valkyrienskies.mod.common.blockstate.BlockStateProperties

/**
 * Packet to sync datapack-defined blockstate info from server to client
 */
data class PacketSyncBlockStateProperties(
    val blockState2properties: Map<String, Map<String, BlockStateProperties>> = mapOf(),
    val defaultMass: Double? = null,
    val defaultFriction: Double? = null,
    val defaultElasticity: Double? = null,
    val defaultDensity: Double? = null
) : SimplePacket
