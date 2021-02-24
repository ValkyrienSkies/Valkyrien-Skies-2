package org.valkyrienskies.mod

import net.minecraft.util.math.BlockPos
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
        return getShipObjectManagingPos(world, chunkPos.x, chunkPos.z)
    }

    fun getShipObjectManagingPos(world: World, blockPos: BlockPos): ShipObject? {
        return getShipObjectManagingPos(world, blockPos.x shr 4, blockPos.z shr 4)
    }

    fun getShipObjectManagingPos(world: World, chunkX: Int, chunkZ: Int): ShipObject? {
        val shipObjectWorld = getShipObjectWorldFromWorld(world)
        if (shipObjectWorld.chunkAllocator.isChunkInShipyard(chunkX, chunkZ)) {
            val shipDataManagingPos = shipObjectWorld.queryableShipData.getShipDataFromChunkPos(chunkX, chunkZ)
            if (shipDataManagingPos != null) {
                return shipObjectWorld.uuidToShipObjectMap[shipDataManagingPos.shipUUID]
            }
        }
        return null
    }
}