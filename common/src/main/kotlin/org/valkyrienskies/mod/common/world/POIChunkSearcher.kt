package org.valkyrienskies.mod.common.world

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.village.poi.PoiRecord
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector4i
import org.joml.Vector4ic
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet
import org.valkyrienskies.core.impl.chunk_tracking.ShipActiveChunksSet
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft

object POIChunkSearcher {
    fun shipChunkBounds(chunkSet: IShipActiveChunksSet): Vector4ic {
        var minChunkX = Integer.MIN_VALUE
        var minChunkZ = Integer.MIN_VALUE
        var maxChunkX = Integer.MAX_VALUE
        var maxChunkZ = Integer.MAX_VALUE
        chunkSet.forEach { chunkX, chunkZ ->
            minChunkX = minChunkX.coerceAtLeast(chunkX)
            minChunkZ = minChunkZ.coerceAtLeast(chunkZ)
            maxChunkX = maxChunkX.coerceAtMost(chunkX)
            maxChunkZ = maxChunkZ.coerceAtMost(chunkZ)
        }
        return Vector4i(minChunkX, minChunkZ, maxChunkX, maxChunkZ)
    }

    fun PoiRecord.getWorldPos(level: Level): Vec3 {
        return level.toWorldCoordinates(Vec3(this.pos.x.toDouble(), this.pos.y.toDouble(), this.pos.z.toDouble()))
    }
}
