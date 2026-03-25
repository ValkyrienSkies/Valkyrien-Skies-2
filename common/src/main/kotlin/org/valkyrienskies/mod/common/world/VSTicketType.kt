package org.valkyrienskies.mod.common.world

import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import java.util.Comparator

/**
 * Custom ticket type for ship chunks. Used with radius 1, giving ticket level 32 (ticking).
 *
 * Vanilla's FORCED ticket uses level 31 (entity ticking) which requires a 2-chunk neighborhood,
 * causing ~25 chunks to be loaded per ship chunk. Our ticket at level 32 (ticking) only needs
 * a 1-chunk neighborhood (~9 chunks), reducing the chunk loading overhead by ~3x.
 *
 * Entity ticking is not needed for shipyard chunks since they don't contain natural entities.
 */
object VSTicketType {
    @JvmField
    val SHIP_CHUNK: TicketType<ChunkPos> = TicketType.create(
        "vs_ship_chunk", Comparator.comparingLong(ChunkPos::toLong)
    )
}
