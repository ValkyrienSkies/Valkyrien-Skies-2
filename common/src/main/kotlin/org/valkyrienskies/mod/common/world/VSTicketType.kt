package org.valkyrienskies.mod.common.world

import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import java.util.Comparator

/**
 * Custom ticket type for ship chunks. Used with radius 0, giving ticket level 33 (FULL status).
 *
 * This loads ONLY the requested chunk with zero neighbor chunks:
 * - Vanilla FORCED ticket: level 31 (entity ticking) = 2-chunk radius = ~25 chunks per ship chunk
 * - Previous VS2 ticket: radius 1 (level 32, ticking) = 1-chunk radius = ~9 chunks per ship chunk
 * - Current VS2 ticket: radius 0 (level 33, FULL) = 0-chunk radius = 1 chunk per ship chunk
 *
 * For 100 ships this means loading 100 chunks instead of 900 (or 2500 vanilla) — a 9x improvement.
 * FULL status is sufficient for shipyard chunks: block reads, block entities, and terrain updates
 * all work at FULL status. Entity ticking and block ticking are not needed in the shipyard.
 */
object VSTicketType {
    @JvmField
    val SHIP_CHUNK: TicketType<ChunkPos> = TicketType.create(
        "vs_ship_chunk", Comparator.comparingLong(ChunkPos::toLong)
    )
}
