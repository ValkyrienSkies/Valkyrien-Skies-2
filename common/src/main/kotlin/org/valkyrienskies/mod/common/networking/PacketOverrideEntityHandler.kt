package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

data class PacketOverrideEntityHandler(
    val entityID: Int,
    val handler: String
): SimplePacket
