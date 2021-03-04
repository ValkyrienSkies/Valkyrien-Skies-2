package org.valkyrienskies.mod.common

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import org.joml.Vector3d
import org.valkyrienskies.core.game.ships.ShipData
import org.valkyrienskies.core.game.ships.ShipObject
import org.valkyrienskies.mod.common.util.toJOMLD

val World.shipObjectWorld get() = (this as IShipObjectWorldProvider).shipObjectWorld

fun World.getShipObjectManagingPos(blockPos: BlockPos): ShipObject? =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun World.getShipObjectManagingPos(chunkPos: ChunkPos): ShipObject? =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

fun World.getShipObjectManagingPos(chunkX: Int, chunkZ: Int): ShipObject? {
    if (shipObjectWorld.chunkAllocator.isChunkInShipyard(chunkX, chunkZ)) {
        val shipDataManagingPos = shipObjectWorld.queryableShipData.getShipDataFromChunkPos(chunkX, chunkZ)
        if (shipDataManagingPos != null) {
            return shipObjectWorld.uuidToShipObjectMap[shipDataManagingPos.shipUUID]
        }
    }
    return null
}

fun World.getShipManagingPos(blockPos: BlockPos): ShipData? =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun World.getShipManagingPos(chunkPos: ChunkPos): ShipData? =
    getShipManagingPos(chunkPos.x, chunkPos.z)

fun World.getShipManagingPos(x: Int, z: Int): ShipData? {
    return if (shipObjectWorld.chunkAllocator.isChunkInShipyard(x, z)) {
        shipObjectWorld.queryableShipData.getShipDataFromChunkPos(x, z)
    } else {
        null
    }
}

fun ShipData.toWorldCoordinates(pos: BlockPos) = shipTransform.shipToWorldMatrix.transformPosition(pos.toJOMLD())
fun ShipData.toWorldCoordinates(x: Double, y: Double, z: Double) =
    shipTransform.shipToWorldMatrix.transformPosition(Vector3d(x, y, z))

object VSGameUtils {

    @JvmStatic
    fun getShipManagingPos(world: World, chunkPos: ChunkPos): ShipData? {
        return world.getShipManagingPos(chunkPos)
    }

    @JvmStatic
    fun getShipObjectManagingPos(world: World, chunkPos: ChunkPos): ShipObject? {
        return world.getShipObjectManagingPos(chunkPos.x, chunkPos.z)
    }

    @JvmStatic
    fun getShipObjectManagingPos(world: World, blockPos: BlockPos): ShipObject? {
        return world.getShipObjectManagingPos(blockPos)
    }

    @JvmStatic
    fun getShipObjectManagingPos(world: World, chunkX: Int, chunkZ: Int): ShipObject? {
        return world.getShipObjectManagingPos(chunkX, chunkZ)
    }

    @JvmStatic
    fun getWorldCoordinates(world: World, pos: BlockPos): Vector3d {
        return getWorldCoordinates(world, pos.toJOMLD())
    }

    /**
     * Transforms [pos] from ship space to world space if a ship exists there.
     *
     * Different from [getWorldCoordinates(World, Vector3d)] only in that resolves the ship owning
     * [blockPos] rather than inferring it from [pos], which might be helpful at the boundaries of ships.
     */
    @JvmStatic
    fun getWorldCoordinates(world: World, blockPos: BlockPos, pos: Vector3d): Vector3d {
        return world.getShipObjectManagingPos(blockPos)
            ?.shipData?.shipTransform?.shipToWorldMatrix?.transformPosition(pos) ?: pos
    }

    /**
     * Transform [pos] from ship space to world space if a ship exists there.
     */
    @JvmStatic
    fun getWorldCoordinates(world: World, pos: Vector3d): Vector3d {
        return getShipObjectManagingPos(world, pos.x.toInt() shr 4, pos.z.toInt() shr 4)
            ?.shipData?.shipTransform?.shipToWorldMatrix?.transformPosition(pos) ?: pos
    }
}
