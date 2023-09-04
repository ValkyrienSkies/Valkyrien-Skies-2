package org.valkyrienskies.mod.common

import org.valkyrienskies.core.api.ships.properties.ChunkClaim
import org.valkyrienskies.core.impl.game.ChunkAllocator

object VS2ChunkAllocator {
    // TODO: Move this to ChunkAllocator eventually
    fun isChunkInShipyardCompanion(chunkX: Int, chunkZ: Int): Boolean {
        val claimXIndex = ChunkClaim.getClaimXIndex(chunkX)
        val claimZIndex = ChunkClaim.getClaimZIndex(chunkZ)

        return (claimXIndex in ChunkAllocator.X_INDEX_START..ChunkAllocator.X_INDEX_END) and (claimZIndex in ChunkAllocator.Z_INDEX_START..ChunkAllocator.Z_INDEX_END)
    }
}
