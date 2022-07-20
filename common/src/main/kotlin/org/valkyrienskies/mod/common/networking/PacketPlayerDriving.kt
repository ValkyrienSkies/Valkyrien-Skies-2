package org.valkyrienskies.mod.common.networking

import org.joml.Vector3f
import org.valkyrienskies.core.networking.simple.SimplePacket

data class PacketPlayerDriving(val impulse: Vector3f) : SimplePacket
