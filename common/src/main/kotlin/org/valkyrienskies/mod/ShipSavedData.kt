package org.valkyrienskies.mod

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.QueryableShipData
import org.valkyrienskies.core.util.serialization.VSJacksonUtil

/**
 * This class saves/loads ship data for a world.
 *
 * This is only a temporary solution, and should be replaced eventually because it is very inefficient.
 */
class ShipSavedData: SavedData(SAVED_DATA_ID) {
    private lateinit var queryableShipData: QueryableShipData
    private lateinit var chunkAllocator: ChunkAllocator

    override fun load(compoundTag: CompoundTag) {
        // Read bytes from the [CompoundTag]
        val queryableShipDataAsBytes = compoundTag.getByteArray(QUERYABLE_SHIP_DATA_NBT_KEY)
        val chunkAllocatorAsBytes = compoundTag.getByteArray(CHUNK_ALLOCATOR_NBT_KEY)

        // Convert bytes to objects
        queryableShipData = VSJacksonUtil.defaultMapper.readValue(
            queryableShipDataAsBytes,
            QueryableShipData::class.java
        )
        chunkAllocator = VSJacksonUtil.defaultMapper.readValue(
            chunkAllocatorAsBytes,
            ChunkAllocator::class.java
        )
    }

    override fun save(compoundTag: CompoundTag): CompoundTag {
        // Convert objects to bytes
        val queryableShipDataAsBytes = VSJacksonUtil.defaultMapper.writeValueAsBytes(queryableShipData)
        val chunkAllocatorAsBytes = VSJacksonUtil.defaultMapper.writeValueAsBytes(chunkAllocator)

        // Save byte arrays
        compoundTag.putByteArray(QUERYABLE_SHIP_DATA_NBT_KEY, queryableShipDataAsBytes)
        compoundTag.putByteArray(CHUNK_ALLOCATOR_NBT_KEY, chunkAllocatorAsBytes)

        return compoundTag
    }

    /**
     * This is not efficient, but it will work for now.
     */
    override fun isDirty(): Boolean {
        return true
    }

    fun getQueryableShipData() = queryableShipData
    fun getChunkAllocator() = chunkAllocator

    companion object {
        const val SAVED_DATA_ID = "vs_ship_data"
        private const val QUERYABLE_SHIP_DATA_NBT_KEY = "queryable_ship_data"
        private const val CHUNK_ALLOCATOR_NBT_KEY = "chunk_allocator"

        fun createNewEmptyShipSavedData(): ShipSavedData {
            val shipSavedData = ShipSavedData()
            shipSavedData.queryableShipData = QueryableShipData()
            shipSavedData.chunkAllocator = ChunkAllocator.newChunkAllocator()
            return shipSavedData
        }
    }
}