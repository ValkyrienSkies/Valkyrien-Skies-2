package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

data class PacketMobShipRotation(val entityID: Int, val shipID: Long, val yaw: Double): SimplePacket
