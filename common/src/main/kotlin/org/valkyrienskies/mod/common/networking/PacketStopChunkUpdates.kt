package org.valkyrienskies.mod.common.networking

import org.joml.Vector2i
import org.valkyrienskies.core.impl.networking.simple.SimplePacket

data class PacketStopChunkUpdates(val chunks: List<Vector2i>) : SimplePacket
data class PacketRestartChunkUpdates(val chunks: List<Vector2i>) : SimplePacket
