package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

data class PacketPlayerShipPush(val active: Boolean, val left: Float, val forward: Float) : SimplePacket
