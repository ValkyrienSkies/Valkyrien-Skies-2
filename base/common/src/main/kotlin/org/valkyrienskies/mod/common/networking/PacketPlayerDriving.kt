package org.valkyrienskies.mod.common.networking

import org.joml.Vector3f
import org.valkyrienskies.core.impl.networking.simple.SimplePacket

data class PacketPlayerDriving(
    val impulse: Vector3f,
    val sprint: Boolean,
    val cruise: Boolean,
) : SimplePacket
