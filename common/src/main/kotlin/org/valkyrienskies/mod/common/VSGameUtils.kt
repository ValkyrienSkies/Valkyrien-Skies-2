package org.valkyrienskies.mod.common

import net.minecraft.client.world.ClientWorld
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import org.joml.Vector3d
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.ships.ShipData
import org.valkyrienskies.core.game.ships.ShipDataClient
import org.valkyrienskies.core.game.ships.ShipObject
import org.valkyrienskies.core.game.ships.ShipObjectClient
import org.valkyrienskies.core.game.ships.ShipObjectServer
import org.valkyrienskies.mod.common.util.toJOMLD

val World.shipObjectWorld get() = (this as IShipObjectWorldProvider).shipObjectWorld
val ServerWorld.shipObjectWorld get() = (this as IShipObjectWorldServerProvider).shipObjectWorld
val ClientWorld.shipObjectWorld get() = (this as IShipObjectWorldClientProvider).shipObjectWorld

private fun getShipObjectManagingPosImpl(world: World, chunkX: Int, chunkZ: Int): ShipObject? {
    if (ChunkAllocator.isChunkInShipyard(chunkX, chunkZ)) {
        val shipDataManagingPos = world.shipObjectWorld.queryableShipData.getShipDataFromChunkPos(chunkX, chunkZ)
        if (shipDataManagingPos != null) {
            return world.shipObjectWorld.shipObjects[shipDataManagingPos.shipUUID]
        }
    }
    return null
}

// World
fun World.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ)

fun World.getShipObjectManagingPos(blockPos: BlockPos) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun World.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

// ClientWorld
fun ClientWorld.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as ShipObjectClient?

fun ClientWorld.getShipObjectManagingPos(blockPos: BlockPos) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ClientWorld.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

// ServerWorld
fun ServerWorld.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as ShipObjectServer?

fun ServerWorld.getShipObjectManagingPos(blockPos: BlockPos) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ServerWorld.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

private fun getShipManagingPosImpl(world: World, x: Int, z: Int): ShipDataClient? {
    return if (ChunkAllocator.isChunkInShipyard(x, z)) {
        world.shipObjectWorld.queryableShipData.getShipDataFromChunkPos(x, z)
    } else {
        null
    }
}

// World
fun World.getShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipManagingPosImpl(this, chunkX, chunkZ)

fun World.getShipManagingPos(blockPos: BlockPos) =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun World.getShipManagingPos(chunkPos: ChunkPos) =
    getShipManagingPos(chunkPos.x, chunkPos.z)

// ServerWorld
fun ServerWorld.getShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipManagingPosImpl(this, chunkX, chunkZ) as ShipData?

fun ServerWorld.getShipManagingPos(blockPos: BlockPos) =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ServerWorld.getShipManagingPos(chunkPos: ChunkPos) =
    getShipManagingPos(chunkPos.x, chunkPos.z)

fun ShipDataClient.toWorldCoordinates(pos: BlockPos) = shipTransform.shipToWorldMatrix.transformPosition(pos.toJOMLD())
fun ShipDataClient.toWorldCoordinates(x: Double, y: Double, z: Double) =
    shipTransform.shipToWorldMatrix.transformPosition(Vector3d(x, y, z))

object VSGameUtils {

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
