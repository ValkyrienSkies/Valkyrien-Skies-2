package org.valkyrienskies.mod.common

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3ic
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.DimensionId
import org.valkyrienskies.core.game.IPlayer
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.core.game.ships.ShipData
import org.valkyrienskies.core.game.ships.ShipObject
import org.valkyrienskies.core.game.ships.ShipObjectClient
import org.valkyrienskies.core.game.ships.ShipObjectServer
import org.valkyrienskies.core.hooks.VSEvents.TickEndEvent
import org.valkyrienskies.core.util.DoubleTernaryConsumer
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.mixin.accessors.resource.ResourceKeyAccessor
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck
import org.valkyrienskies.physics_api.voxel_updates.DenseVoxelShapeUpdate

val Level.shipObjectWorld
    get() =
        // Call the correct overload
        when (this) {
            is ServerLevel -> server.shipObjectWorld
            is ClientLevel -> shipObjectWorld
            else -> throw IllegalArgumentException("World is neither ServerWorld nor ClientWorld")
        }

val Level.queryableShipData
    get() = when (this) {
        is ServerLevel -> server.shipObjectWorld.queryableShipData
        is ClientLevel -> shipObjectWorld.queryableShipData
        else -> throw IllegalArgumentException("World is neither ServerWorld nor ClientWorld")
    }

val MinecraftServer.shipObjectWorld get() = (this as IShipObjectWorldServerProvider).shipObjectWorld
val MinecraftServer.vsPipeline get() = (this as IShipObjectWorldServerProvider).vsPipeline

val ServerLevel.shipObjectWorld
    get() = server.shipObjectWorld

val Level.dimensionId: DimensionId
    get() {
        val dim = dimension()
        dim as ResourceKeyAccessor

        return dim.registryName.toString() + ":" + dim.location().toString()
    }

fun getResourceKey(dimensionId: DimensionId): ResourceKey<Level> {
    @Suppress("UNCHECKED_CAST") val cached = ResourceKeyAccessor.getValues()[dimensionId] as ResourceKey<Level>?
    if (cached == null) {
        val (registryNamespace, registryName, namespace, name) = dimensionId.split(":")
        return ResourceKeyAccessor.callCreate(
            ResourceLocation(registryNamespace, registryName), ResourceLocation(namespace, name)
        )
    }
    return cached
}

fun MinecraftServer.executeIf(condition: () -> Boolean, toExecute: Runnable) {
    TickEndEvent.on { (shipWorld), handler ->
        if (shipWorld == this.shipObjectWorld && condition()) {
            toExecute.run()
            handler.unregister()
        }
    }
}

fun Level.isTickingChunk(pos: ChunkPos) = isTickingChunk(pos.x, pos.z)
fun Level.isTickingChunk(chunkX: Int, chunkZ: Int) = chunkSource.isTickingChunk(BlockPos(chunkX shl 4, 0, chunkZ shl 4))

fun MinecraftServer.getLevelFromDimensionId(dimensionId: DimensionId): ServerLevel? {
    return getLevel(getResourceKey(dimensionId))
}

val Minecraft.shipObjectWorld get() = (this as IShipObjectWorldClientProvider).shipObjectWorld
val ClientLevel.shipObjectWorld get() = Minecraft.getInstance().shipObjectWorld

val IPlayer.mcPlayer: Player get() = (this as MinecraftPlayer).playerEntityReference.get()!!

val Player.playerWrapper get() = (this as PlayerDuck).vs_getPlayer()

/**
 * Like [Entity.squaredDistanceTo] except the destination is transformed into world coordinates if it is a ship
 */
fun Entity.squaredDistanceToInclShips(x: Double, y: Double, z: Double) =
    level.squaredDistanceBetweenInclShips(x, y, z, this.x, this.y, this.z)

/**
 * Calculates the squared distance between to points.
 * x1/y1/z1 are transformed into world coordinates if they are on a ship
 */
fun Level.squaredDistanceBetweenInclShips(
    x1: Double,
    y1: Double,
    z1: Double,
    x2: Double,
    y2: Double,
    z2: Double
): Double {
    var inWorldX1 = x1
    var inWorldY1 = y1
    var inWorldZ1 = z1
    var inWorldX2 = x2
    var inWorldY2 = y2
    var inWorldZ2 = z2

    // Do this transform manually to avoid allocation
    this.getShipManagingPos(x1.toInt() shr 4, z1.toInt() shr 4)?.shipTransform?.shipToWorldMatrix?.let { m ->
        inWorldX1 = m.m00() * x1 + m.m10() * y1 + m.m20() * z1 + m.m30()
        inWorldY1 = m.m01() * x1 + m.m11() * y1 + m.m21() * z1 + m.m31()
        inWorldZ1 = m.m02() * x1 + m.m12() * y1 + m.m22() * z1 + m.m32()
    }
    this.getShipManagingPos(x2.toInt() shr 4, z2.toInt() shr 4)?.shipTransform?.shipToWorldMatrix?.let { m ->
        inWorldX2 = m.m00() * x2 + m.m10() * y2 + m.m20() * z2 + m.m30()
        inWorldY2 = m.m01() * x2 + m.m11() * y2 + m.m21() * z2 + m.m31()
        inWorldZ2 = m.m02() * x2 + m.m12() * y2 + m.m22() * z2 + m.m32()
    }

    val dx = inWorldX2 - inWorldX1
    val dy = inWorldY2 - inWorldY1
    val dz = inWorldZ2 - inWorldZ1

    return dx * dx + dy * dy + dz * dz
}

private fun getShipObjectManagingPosImpl(world: Level, chunkX: Int, chunkZ: Int): ShipObject? {
    if (ChunkAllocator.isChunkInShipyard(chunkX, chunkZ)) {
        val shipDataManagingPos =
            world.shipObjectWorld.queryableShipData.getShipDataFromChunkPos(chunkX, chunkZ, world.dimensionId)
        if (shipDataManagingPos != null) {
            return world.shipObjectWorld.shipObjects[shipDataManagingPos.id]
        }
    }
    return null
}

fun Level.transformToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabbRadius: Double): List<Vector3d> {
    val list = mutableListOf<Vector3d>()

    transformToNearbyShipsAndWorld(x, y, z, aabbRadius) { x, y, z -> list.add(Vector3d(x, y, z)) }

    return list
}

fun Level.transformToNearbyShipsAndWorld(
    x: Double, y: Double, z: Double, aabbRadius: Double, cb: DoubleTernaryConsumer
) {
    transformToNearbyShipsAndWorld(x, y, z, aabbRadius, cb::accept)
}

inline fun Level.transformToNearbyShipsAndWorld(
    x: Double, y: Double, z: Double, aabbRadius: Double, cb: (Double, Double, Double) -> Unit
) {
    val currentShip = getShipManagingPos(x, y, z)
    val aabb = AABBd(x, y, z, x, y, z).expand(aabbRadius)

    val posInWorld = Vector3d(x, y, z)
    val temp0 = Vector3d()

    if (currentShip != null) {
        currentShip.shipToWorld.transformPosition(posInWorld)

        cb(posInWorld.x(), posInWorld.y(), posInWorld.z())
    }

    for (nearbyShip in shipObjectWorld.queryableShipData.getShipDataIntersecting(aabb)) {
        if (nearbyShip == currentShip) continue
        val posInShip = nearbyShip.worldToShip.transformPosition(posInWorld, temp0)
        cb(posInShip.x(), posInShip.y(), posInShip.z())
    }
}

// Level
fun Level.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ)

fun Level.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun Level.getShipObjectManagingPos(pos: Vector3dc) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun Level.getShipObjectManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipObjectManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun Level.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

fun Level.getShipObjectEntityMountedTo(entity: Entity): ShipObject? {
    val vehicle = entity.vehicle ?: return null
    return getShipObjectManagingPos(vehicle.position().toJOML())
}

// ClientLevel
fun ClientLevel.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as ShipObjectClient?

fun ClientLevel.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ClientLevel.getShipObjectManagingPos(pos: Vector3dc) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun ClientLevel.getShipObjectManagingPos(pos: Position) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun ClientLevel.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

fun ClientLevel.getShipObjectEntityMountedTo(entity: Entity): ShipObjectClient? {
    val vehicle = entity.vehicle ?: return null
    return getShipObjectManagingPos(vehicle.position().toJOML())
}

// ServerWorld
fun ServerLevel.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as ShipObjectServer?

fun ServerLevel.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ServerLevel.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

fun ServerLevel.getShipObjectManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipObjectManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

private fun getShipManagingPosImpl(world: Level, x: Int, z: Int): Ship? {
    return if (ChunkAllocator.isChunkInShipyard(x, z)) {
        world.shipObjectWorld.queryableShipData.getShipDataFromChunkPos(x, z, world.dimensionId)
    } else {
        null
    }
}

fun ClientLevel.transformRenderAABBToWorld(pos: Position, aabb: AABB): AABB {
    val ship = getShipObjectManagingPos(pos)
    if (ship != null) {
        return aabb.toJOML().transform(ship.renderTransform.shipToWorldMatrix).toMinecraft()
    }
    return aabb
}

fun Entity.getShipManaging(): Ship? = this.level.getShipManagingPos(this.position())

// Level
fun Level.getShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipManagingPosImpl(this, chunkX, chunkZ)

fun Level.getShipManagingPos(blockPos: BlockPos) =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun Level.getShipManagingPos(pos: Position) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun Level.getShipManagingPos(pos: Vector3dc) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun Level.getShipManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun Level.getShipManagingPos(chunkPos: ChunkPos) =
    getShipManagingPos(chunkPos.x, chunkPos.z)

// ServerLevel
fun ServerLevel.getShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipManagingPosImpl(this, chunkX, chunkZ) as ShipData?

fun ServerLevel.getShipManagingPos(blockPos: BlockPos) =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ServerLevel.getShipManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun ServerLevel.getShipManagingPos(chunkPos: ChunkPos) =
    getShipManagingPos(chunkPos.x, chunkPos.z)

fun Ship.toWorldCoordinates(pos: BlockPos): Vector3d =
    shipTransform.shipToWorldMatrix.transformPosition(pos.toJOMLD())

fun Ship.toWorldCoordinates(x: Double, y: Double, z: Double): Vector3d =
    shipTransform.shipToWorldMatrix.transformPosition(Vector3d(x, y, z))

fun LevelChunkSection.toDenseVoxelUpdate(chunkPos: Vector3ic): DenseVoxelShapeUpdate {
    val update = DenseVoxelShapeUpdate.createDenseVoxelShapeUpdate(chunkPos)
    update.setData { x: Int, y: Int, z: Int ->
        (BlockStateInfo.get(getBlockState(x, y, z))?.second ?: VSBlockType.AIR).toByte()
    }
    return update
}

fun LevelChunkSection.addChunkBlocksToShipVoxelAABB(chunkPos: Vector3ic, shipData: ShipData) {
    // Send the blocks in the chunk to the AABB generator of [shipData]
    val chunkBaseX = chunkPos.x() shl 4
    val chunkBaseY = chunkPos.y() shl 4
    val chunkBaseZ = chunkPos.z() shl 4
    for (y in 0 until 16) {
        for (z in 0 until 16) {
            for (x in 0 until 16) {
                val blockState: VSBlockType = BlockStateInfo.get(getBlockState(x, y, z))?.second ?: VSBlockType.AIR
                shipData.updateShipAABBGenerator(
                    chunkBaseX + x, chunkBaseY + y, chunkBaseZ + z, blockState != VSBlockType.AIR
                )
            }
        }
    }
}

/**
 * Transforms [pos] from ship space to world space if a ship exists there.
 *
 * Different from [getWorldCoordinates(World, Vector3d)] only in that resolves the ship owning
 * [blockPos] rather than inferring it from [pos], which might be helpful at the boundaries of ships.
 */
fun Level.getWorldCoordinates(blockPos: BlockPos, pos: Vector3d): Vector3d {
    return this.getShipObjectManagingPos(blockPos)
        ?.shipData?.shipTransform?.shipToWorldMatrix?.transformPosition(pos) ?: pos
}

fun Level.getShipsIntersecting(aabb: AABB): Iterable<Ship> = queryableShipData.getShipDataIntersecting(aabb.toJOML())
fun Level.getShipsIntersecting(aabb: AABBdc): Iterable<Ship> = queryableShipData.getShipDataIntersecting(aabb)

fun Level.transformAabbToWorld(aabb: AABB): AABB = transformAabbToWorld(aabb.toJOML()).toMinecraft()
fun Level.transformAabbToWorld(aabb: AABBd) = transformAabbToWorld(aabb, aabb)
fun Level.transformAabbToWorld(aabb: AABBdc, dest: AABBd): AABBd {
    val ship1 = getShipManagingPos(aabb.minX(), aabb.minY(), aabb.minZ())
    val ship2 = getShipManagingPos(aabb.maxX(), aabb.maxY(), aabb.maxZ())

    // if both endpoints of the aabb are in the same ship, do the transform
    if (ship1 == ship2 && ship1 != null) {
        return aabb.transform(ship1.shipTransform.shipToWorldMatrix, dest)
    }

    return dest.set(aabb)
}

fun Entity.getPassengerPos(myRidingOffset: Double, partialTicks: Float): Vector3dc {
    return this.getPosition(partialTicks)
        .add(0.0, this.passengersRidingOffset + myRidingOffset, 0.0).toJOML()
}
