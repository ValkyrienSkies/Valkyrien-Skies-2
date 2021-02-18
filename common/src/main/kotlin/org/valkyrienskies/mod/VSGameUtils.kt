package org.valkyrienskies.mod

import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import org.valkyrienskies.core.game.ShipData
import org.valkyrienskies.core.game.ShipObject
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

    fun getShipObjectManagingPos(world: World, chunkPos: ChunkPos): ShipObject? {
        val shipObjectWorld = getShipObjectWorldFromWorld(world)
        if (shipObjectWorld.chunkAllocator.isChunkInShipyard(chunkPos.x, chunkPos.z)) {
            val shipDataManagingPos = shipObjectWorld.queryableShipData.getShipDataFromChunkPos(chunkPos.x, chunkPos.z)
            if (shipDataManagingPos != null) {
                return shipObjectWorld.uuidToShipObjectMap[shipDataManagingPos.shipUUID]
            }
        }
        return null
    }
}