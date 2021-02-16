package org.valkyrienskies.mod

import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import org.valkyrienskies.core.game.ShipData
import org.valkyrienskies.core.game.ShipObjectWorld

object VSGameUtils {
    fun getShipManagingPos(world: World, chunkPos: ChunkPos): ShipData? {
        val shipObjectWorld = getShipObjectWorldFromWorld(world)
        return if (shipObjectWorld.chunkAllocator.isChunkInShipyard(chunkPos.x, chunkPos.z)) {
            shipObjectWorld.queryableShipData.getShipDataFromChunkPos(chunkPos.x, chunkPos.z)
        } else {
            null
        }
    }

    fun getShipObjectWorldFromWorld(world: World): ShipObjectWorld {
        return (world as IShipObjectWorldProvider).shipObjectWorld
    }
}