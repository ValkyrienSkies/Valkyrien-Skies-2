package org.valkyrienskies.mod.common

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import org.valkyrienskies.core.api.world.VSPipeline

/**
 * This class saves/loads ship data for a world.
 *
 * This is only a temporary solution, and should be replaced eventually because it is very inefficient.
 */
class ShipSavedData : SavedData() {

    companion object {
        const val SAVED_DATA_ID = "vs_ship_data"
        private const val QUERYABLE_SHIP_DATA_NBT_KEY = "queryable_ship_data"
        private const val CHUNK_ALLOCATOR_NBT_KEY = "chunk_allocator"
        private const val PIPELINE_NBT_KEY = "vs_pipeline"

        fun createEmpty(): ShipSavedData {
            return ShipSavedData().apply { pipeline = vsCore.newPipeline() }
        }

        @JvmStatic
        fun load(compoundTag: CompoundTag): ShipSavedData {
            val data = ShipSavedData()

            // Read bytes from the [CompoundTag]
            val queryableShipDataAsBytes = compoundTag.getByteArray(QUERYABLE_SHIP_DATA_NBT_KEY)
            val chunkAllocatorAsBytes = compoundTag.getByteArray(CHUNK_ALLOCATOR_NBT_KEY)
            val pipelineAsBytes = compoundTag.getByteArray(PIPELINE_NBT_KEY)

            try {
                if (pipelineAsBytes.isNotEmpty()) {
                    data.pipeline = vsCore.newPipeline(pipelineAsBytes)
                } else if (queryableShipDataAsBytes.isNotEmpty() && chunkAllocatorAsBytes.isNotEmpty()) {
                    data.pipeline = vsCore.newPipelineLegacyData(queryableShipDataAsBytes, chunkAllocatorAsBytes)
                } else {
                    throw IllegalStateException("Couldn't find serialized ship data")
                }
            } catch (ex: Exception) {
                data.loadingException = ex
            }
            return data
        }
    }

    lateinit var pipeline: VSPipeline

    var loadingException: Throwable? = null
        private set

    override fun save(compoundTag: CompoundTag): CompoundTag {
        compoundTag.putByteArray(PIPELINE_NBT_KEY, vsCore.serializePipeline(pipeline))

        return compoundTag
    }

    /**
     * This is not efficient, but it will work for now.
     */
    override fun isDirty(): Boolean {
        return true
    }
}
