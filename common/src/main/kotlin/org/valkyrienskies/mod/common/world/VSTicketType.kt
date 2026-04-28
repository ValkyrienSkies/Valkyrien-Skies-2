package org.valkyrienskies.mod.common.world

import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import java.util.Comparator

/**
 * Custom ticket type for transient shipyard chunk access that only needs FULL status.
 *
 * Used with radius 0, this loads only the requested chunk with zero neighbor chunks:
 * - Vanilla FORCED ticket: level 31 (entity ticking) = 2-chunk radius = ~25 chunks per ship chunk
 * - Previous VS2 ticket: radius 1 (level 32, block ticking) = 1-chunk radius = ~9 chunks per ship chunk
 * - SHIP_CHUNK ticket: radius 0 (level 33, FULL) = 0-chunk radius = 1 chunk per ship chunk
 *
 * This is appropriate for preload/copy/update flows where VS only needs the chunk data itself.
 * It is not appropriate for normal live ship chunk management because ships still need vanilla
 * random ticking, block ticking, and entity ticking.
 */
object VSTicketType {
    @JvmField
    val SHIP_CHUNK: TicketType<ChunkPos> = TicketType.create(
        "vs_ship_chunk", Comparator.comparingLong(ChunkPos::toLong)
    )
}
