package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import org.valkyrienskies.mod.common.blockstate.VSBlockProperties
import org.valkyrienskies.mod.common.blockstate.VSFluidProperties

/**
 * Packet to sync datapack-defined blockstate info from server to client
 */
data class PacketSyncBlockStateProperties(
    val blockState2Properties: Map<String, Map<String, VSBlockProperties>> = mapOf(),
    val fluidState2Properties: Map<String, Map<String, VSFluidProperties>> = mapOf()
) : SimplePacket
