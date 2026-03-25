package org.valkyrienskies.mod.common.world

import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import java.util.Comparator

/**
 * Custom ticket type for ship chunks that loads them to FULL status
 * without forcing neighbor chunks to load (unlike vanilla's FORCED ticket at level 31).
 *
 * Level 33 = "border" in MC's chunk system, which means:
 * - The chunk is fully generated and converted to LevelChunk
 * - Block access works normally
 * - But entity ticking and block ticking are NOT active
 * - Neighbor chunks are NOT required to be loaded
 *
 * This dramatically reduces the number of chunks MC loads per ship,
 * from ~25 (with FORCED at level 31) to exactly 1.
 */
object VSTicketType {
    @JvmField
    val SHIP_CHUNK: TicketType<ChunkPos> = TicketType.create(
        "vs_ship_chunk", Comparator.comparingLong(ChunkPos::toLong)
    )

    /**
     * The ticket level for ship chunks.
     * 33 = border level in MC, which fully generates the chunk without requiring neighbors.
     */
    const val SHIP_CHUNK_LEVEL = 33
}
