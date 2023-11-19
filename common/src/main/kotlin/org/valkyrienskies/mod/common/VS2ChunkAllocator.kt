package org.valkyrienskies.mod.common

object VS2ChunkAllocator {
    // TODO: Move this to ChunkAllocator eventually
    fun isChunkInShipyardCompanion(chunkX: Int, chunkZ: Int): Boolean {
        return vsCore.isChunkInShipyard(chunkX, chunkZ)
    }
}
