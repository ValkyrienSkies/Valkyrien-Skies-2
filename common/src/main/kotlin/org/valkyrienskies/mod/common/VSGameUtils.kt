package org.valkyrienskies.mod.common

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import org.joml.Vector3d
import org.valkyrienskies.core.game.ShipData
import org.valkyrienskies.core.game.ShipObject
import org.valkyrienskies.core.game.ShipObjectWorld
import org.valkyrienskies.mod.common.util.toJOMLD

object VSGameUtils {
    @JvmStatic
    fun getShipManagingPos(world: World, chunkPos: ChunkPos): ShipData? {
        val shipObjectWorld = getShipObjectWorldFromWorld(world)
        return if (shipObjectWorld.chunkAllocator.isChunkInShipyard(chunkPos.x, chunkPos.z)) {
            shipObjectWorld.queryableShipData.getShipDataFromChunkPos(chunkPos.x, chunkPos.z)
        } else {
            null
        }
    }

    @JvmStatic
    fun getShipObjectWorldFromWorld(world: World): ShipObjectWorld {
        return world.shipObjectWorld
    }

    @JvmStatic
    fun getShipObjectManagingPos(world: World, chunkPos: ChunkPos): ShipObject? {
        return getShipObjectManagingPos(world, chunkPos.x, chunkPos.z)
    }

    @JvmStatic
    fun getShipObjectManagingPos(world: World, blockPos: BlockPos): ShipObject? {
        return getShipObjectManagingPos(world, blockPos.x shr 4, blockPos.z shr 4)
    }

    @JvmStatic
    fun getShipObjectManagingPos(world: World, chunkX: Int, chunkZ: Int): ShipObject? {
        val shipObjectWorld = world.shipObjectWorld
        if (shipObjectWorld.chunkAllocator.isChunkInShipyard(chunkX, chunkZ)) {
            val shipDataManagingPos = shipObjectWorld.queryableShipData.getShipDataFromChunkPos(chunkX, chunkZ)
            if (shipDataManagingPos != null) {
                return shipObjectWorld.uuidToShipObjectMap[shipDataManagingPos.shipUUID]
            }
        }
        return null
    }

    @JvmStatic
    fun getWorldCoordinates(world: World, pos: BlockPos): Vector3d {
        return getWorldCoordinates(world, pos.toJOMLD())
    }

    @JvmStatic
    fun getWorldCoordinates(world: World, pos: Vector3d): Vector3d {
        return getShipObjectManagingPos(world, pos.x.toInt() shr 4, pos.z.toInt() shr 4)
            ?.shipData?.shipTransform?.shipToWorldMatrix?.transformPosition(pos) ?: pos
    }
}

val World.shipObjectWorld get() = (this as IShipObjectWorldProvider).shipObjectWorld