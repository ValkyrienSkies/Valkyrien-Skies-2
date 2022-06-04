package org.valkyrienskies.mod.common

import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraft.world.chunk.ChunkSection
import org.joml.Vector3d
import org.joml.Vector3ic
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.ships.ShipData
import org.valkyrienskies.core.game.ships.ShipDataCommon
import org.valkyrienskies.core.game.ships.ShipObject
import org.valkyrienskies.core.game.ships.ShipObjectClient
import org.valkyrienskies.core.game.ships.ShipObjectServer
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.physics_api.voxel_updates.DenseVoxelShapeUpdate
import org.valkyrienskies.physics_api.voxel_updates.KrunchVoxelStates
import kotlin.math.min

val World.shipObjectWorld get() = (this as IShipObjectWorldProvider).shipObjectWorld
val ServerWorld.shipObjectWorld get() = (this as IShipObjectWorldServerProvider).shipObjectWorld
val ClientWorld.shipObjectWorld get() = (this as IShipObjectWorldClientProvider).shipObjectWorld

/**
 * Like [Entity.squaredDistanceTo] except the destination is transformed into world coordinates if it is a ship
 */
fun Entity.squaredDistanceToInclShips(x: Double, y: Double, z: Double) =
    world.squaredDistanceBetweenInclShips(x, y, z, this.x, this.y, this.z)

/**
 * Calculates the squared distance between to points.
 * x1/y1/z1 are transformed into world coordinates if they are on a ship
 */
fun World.squaredDistanceBetweenInclShips(
    x1: Double,
    y1: Double,
    z1: Double,
    x2: Double,
    y2: Double,
    z2: Double
): Double {
    val origDx = x2 - x1
    val origDy = y2 - y1
    val origDz = z2 - z1

    val squareDistWithoutRespectToShips = origDx * origDx + origDy * origDy + origDz * origDz

    // If transform is null, then just return squareDistWithoutRespectToShips
    val transform = this.getShipManagingPos(x1.toInt() shr 4, z1.toInt() shr 4)?.shipTransform
        ?: return squareDistWithoutRespectToShips

    val m = transform.shipToWorldMatrix

    // Do this transform manually to avoid allocation
    val inWorldX = m.m00() * x1 + m.m10() * y1 + m.m20() * z1 + m.m30()
    val inWorldY = m.m01() * x1 + m.m11() * y1 + m.m21() * z1 + m.m31()
    val inWorldZ = m.m02() * x1 + m.m12() * y1 + m.m22() * z1 + m.m32()

    val dx = x2 - inWorldX
    val dy = y2 - inWorldY
    val dz = z2 - inWorldZ

    val squareDistWithRespectToShips = dx * dx + dy * dy + dz * dz

    return min(squareDistWithRespectToShips, squareDistWithoutRespectToShips)
}

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

private fun getShipManagingPosImpl(world: World, x: Int, z: Int): ShipDataCommon? {
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

fun ShipDataCommon.toWorldCoordinates(pos: BlockPos) = shipTransform.shipToWorldMatrix.transformPosition(pos.toJOMLD())
fun ShipDataCommon.toWorldCoordinates(x: Double, y: Double, z: Double) =
    shipTransform.shipToWorldMatrix.transformPosition(Vector3d(x, y, z))

fun ChunkSection.toDenseVoxelUpdate(chunkPos: Vector3ic): DenseVoxelShapeUpdate {
    val update = DenseVoxelShapeUpdate.createDenseVoxelShapeUpdate(chunkPos)
    update.setData { x: Int, y: Int, z: Int -> BlockStateInfo.get(getBlockState(x, y, z)).second.toByte() }
    return update
}

object VSGameUtils {

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
        return world.getShipObjectManagingPos(pos.x.toInt() shr 4, pos.z.toInt() shr 4)
            ?.shipData?.shipTransform?.shipToWorldMatrix?.transformPosition(pos) ?: pos
    }
}
