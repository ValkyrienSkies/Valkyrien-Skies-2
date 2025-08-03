package org.valkyrienskies.mod.common.world

import net.minecraft.world.entity.ai.village.poi.PoiRecord
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector4i
import org.joml.Vector4ic
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet
import org.valkyrienskies.mod.common.toWorldCoordinates
import kotlin.math.max
import kotlin.math.min

object POIChunkSearcher {
    fun shipChunkBounds(chunkSet: IShipActiveChunksSet): Vector4ic? {
        if (chunkSet.size == 0) {
            return null
        }
        var minChunkX = Int.MAX_VALUE
        var minChunkZ = Int.MAX_VALUE
        var maxChunkX = Int.MIN_VALUE
        var maxChunkZ = Int.MIN_VALUE
        chunkSet.forEach { chunkX, chunkZ ->
            minChunkX = min(minChunkX, chunkX)
            minChunkZ = min(minChunkZ, chunkZ)
            maxChunkX = max(maxChunkX, chunkX)
            maxChunkZ = max(maxChunkZ, chunkZ)
        }
        if (minChunkX == Int.MAX_VALUE || minChunkZ == Int.MAX_VALUE || maxChunkX == Int.MIN_VALUE || maxChunkZ == Int.MIN_VALUE) {
            return null
        }
        return Vector4i(minChunkX, minChunkZ, maxChunkX, maxChunkZ)
    }

    fun PoiRecord.getWorldPos(level: Level): Vec3 {
        return level.toWorldCoordinates(this.pos)
    }
}
