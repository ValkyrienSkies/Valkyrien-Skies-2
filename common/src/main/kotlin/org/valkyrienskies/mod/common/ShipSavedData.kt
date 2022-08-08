package org.valkyrienskies.mod.common

import com.fasterxml.jackson.module.kotlin.readValue
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.ships.MutableQueryableShipDataServer
import org.valkyrienskies.core.game.ships.QueryableShipsImpl
import org.valkyrienskies.core.game.ships.ShipData
import org.valkyrienskies.core.util.serialization.VSJacksonUtil

/**
 * This class saves/loads ship data for a world.
 *
 * This is only a temporary solution, and should be replaced eventually because it is very inefficient.
 */
class ShipSavedData : SavedData(SAVED_DATA_ID) {

    companion object {
        const val SAVED_DATA_ID = "vs_ship_data"
        private const val QUERYABLE_SHIP_DATA_NBT_KEY = "queryable_ship_data"
        private const val CHUNK_ALLOCATOR_NBT_KEY = "chunk_allocator"

        fun createEmpty(): ShipSavedData {
            val shipSavedData = ShipSavedData()
            shipSavedData.queryableShipData = QueryableShipsImpl()
            shipSavedData.chunkAllocator = ChunkAllocator.create()
            return shipSavedData
        }
    }

    private lateinit var queryableShipData: MutableQueryableShipDataServer
    private lateinit var chunkAllocator: ChunkAllocator

    override fun load(compoundTag: CompoundTag) {
        // Read bytes from the [CompoundTag]
        val queryableShipDataAsBytes = compoundTag.getByteArray(QUERYABLE_SHIP_DATA_NBT_KEY)
        val chunkAllocatorAsBytes = compoundTag.getByteArray(CHUNK_ALLOCATOR_NBT_KEY)

        // Convert bytes to objects
        val ships: List<ShipData> = VSJacksonUtil.defaultMapper.readValue(queryableShipDataAsBytes)

        queryableShipData = QueryableShipsImpl(ships)
        chunkAllocator = VSJacksonUtil.defaultMapper.readValue(chunkAllocatorAsBytes)
    }

    override fun save(compoundTag: CompoundTag): CompoundTag {
        // Convert objects to bytes
        val queryableShipDataAsBytes = VSJacksonUtil.defaultMapper.writeValueAsBytes(queryableShipData.toList())
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
}
