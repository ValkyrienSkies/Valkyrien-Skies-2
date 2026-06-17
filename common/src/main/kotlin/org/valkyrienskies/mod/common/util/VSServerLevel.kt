package org.valkyrienskies.mod.common.util

// Used on Level to get a Level to remove a chunk from its tracking
interface VSServerLevel {
    fun removeChunk(chunkX: Int, chunkZ: Int)
    fun addPendingForcedChunk(chunkX: Int, chunkZ: Int)

    /**
     * Returns the set of pending forced chunk positions (as packed longs).
     * Used during startup to synchronously pre-load ship chunks.
     */
    fun getPendingForcedChunks(): it.unimi.dsi.fastutil.longs.LongOpenHashSet
}

// Used on LevelChunk to delete all blocks and block entities in a LevelChunk
interface VSLevelChunk {
    fun clearChunk()

    fun copyChunkFromOtherDimension(srcChunk: VSLevelChunk)
}
