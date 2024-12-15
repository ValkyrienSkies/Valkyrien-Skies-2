package org.valkyrienskies.mod.common.networking

import org.valkyrienskies.core.impl.networking.simple.SimplePacket

data class PacketPlayerShipMotion(val shipID: Long, val x: Double, val y: Double, val z: Double, val yRot: Double): SimplePacket
