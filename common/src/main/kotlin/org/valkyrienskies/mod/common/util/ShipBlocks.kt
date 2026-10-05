package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.util.GameTickOnly

@OptIn(GameTickOnly::class)
internal fun collectShipBlocks(level: ServerLevel, ship: LoadedServerShip): Set<BlockPos>? {
    val blocks = HashSet<BlockPos>()
    var missingChunk = false
    ship.activeChunksSet.forEach { cx, cz ->
        val chunk = level.chunkSource.getChunkNow(cx, cz)
        if (chunk == null) {
            missingChunk = true
            return@forEach
        }
        for (sectionIndex in chunk.sections.indices) {
            val section = chunk.sections[sectionIndex]
            if (section.hasOnlyAir()) continue
            val baseY = chunk.getSectionYFromSectionIndex(sectionIndex) shl 4
            for (x in 0..15) for (y in 0..15) for (z in 0..15) {
                if (!section.getBlockState(x, y, z).isAir) {
                    blocks.add(BlockPos((cx shl 4) + x, baseY + y, (cz shl 4) + z))
                }
            }
        }
    }
    return if (missingChunk) null else blocks
}
