package org.valkyrienskies.mod.common.networking

import org.joml.Vector2i
import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import org.valkyrienskies.core.impl.networking.simple.SimplePacketNetworking
import org.valkyrienskies.core.internal.world.VsiPlayer

data class PacketStopChunkUpdates(val chunks: List<Vector2i>) : SimplePacket
data class PacketRestartChunkUpdates(val chunks: List<Vector2i>) : SimplePacket

// Max chunk positions per packet. Each Vector2i ≈ 8 bytes serialized + overhead.
// 10,000 × ~16 bytes = ~160KB, well under the 1MB (1,048,576 byte) packet limit.
private const val MAX_CHUNKS_PER_PACKET = 10_000

/**
 * Send a [PacketStopChunkUpdates] to a player, automatically splitting into multiple
 * packets if the chunk list exceeds the vanilla payload size limit (1 MB).
 */
fun SimplePacketNetworking.sendStopChunkUpdates(chunks: List<Vector2i>, player: VsiPlayer) {
    if (chunks.size <= MAX_CHUNKS_PER_PACKET) {
        PacketStopChunkUpdates(chunks).sendToClient(player)
    } else {
        for (i in chunks.indices step MAX_CHUNKS_PER_PACKET) {
            val batch = chunks.subList(i, minOf(i + MAX_CHUNKS_PER_PACKET, chunks.size))
            PacketStopChunkUpdates(batch).sendToClient(player)
        }
    }
}

/**
 * Send a [PacketRestartChunkUpdates] to a player, automatically splitting into multiple
 * packets if the chunk list exceeds the vanilla payload size limit (1 MB).
 */
fun SimplePacketNetworking.sendRestartChunkUpdates(chunks: List<Vector2i>, player: VsiPlayer) {
    if (chunks.size <= MAX_CHUNKS_PER_PACKET) {
        PacketRestartChunkUpdates(chunks).sendToClient(player)
    } else {
        for (i in chunks.indices step MAX_CHUNKS_PER_PACKET) {
            val batch = chunks.subList(i, minOf(i + MAX_CHUNKS_PER_PACKET, chunks.size))
            PacketRestartChunkUpdates(batch).sendToClient(player)
        }
    }
}
