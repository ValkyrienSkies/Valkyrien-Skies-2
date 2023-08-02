package org.valkyrienskies.mod.common

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3ic
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.util.functions.DoubleTernaryConsumer
import org.valkyrienskies.core.api.world.LevelYRange
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.apigame.world.ShipWorldCore
import org.valkyrienskies.core.apigame.world.chunks.TerrainUpdate
import org.valkyrienskies.core.apigame.world.properties.DimensionId
import org.valkyrienskies.core.game.ships.ShipObjectServer
import org.valkyrienskies.core.impl.hooks.VSEvents.TickEndEvent
import org.valkyrienskies.core.impl.util.expand
import org.valkyrienskies.mod.common.util.DimensionIdProvider
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.util.set
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.common.world.DummyShipWorldClient
import org.valkyrienskies.mod.common.world.DummyShipWorldServer
import org.valkyrienskies.mod.mixin.accessors.resource.ResourceKeyAccessor
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck
import java.util.function.Consumer

val vsCore get() = ValkyrienSkiesMod.vsCore

val Level?.shipWorldNullable: ShipWorldCore?
    get() = when {
        this == null -> null
        this is ServerLevel -> server.shipObjectWorld
        this.isClientSide && this is ClientLevel -> this.shipObjectWorld
        else -> null
    }

val Level?.shipObjectWorld
    get() = shipWorldNullable ?: DummyShipWorldClient

val Level?.allShips get() = this.shipObjectWorld.allShips

val MinecraftServer.shipObjectWorld: ServerShipWorldCore
    get() = (this as IShipObjectWorldServerProvider).shipObjectWorld ?: DummyShipWorldServer
val MinecraftServer.vsPipeline get() = (this as IShipObjectWorldServerProvider).vsPipeline!!

val ServerLevel?.shipObjectWorld: ServerShipWorldCore get() = this?.server?.shipObjectWorld ?: DummyShipWorldServer

val Level.dimensionId: DimensionId
    get() {
        this as DimensionIdProvider
        return dimensionId
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
    // todo: don't use random vs-core internal stuff
    TickEndEvent.on { (shipWorld), handler ->
        if (shipWorld == this.shipObjectWorld && condition()) {
            toExecute.run()
            handler.unregister()
        }
    }
}

val Level.yRange get() = LevelYRange(minBuildHeight, maxBuildHeight)

fun Level.isTickingChunk(pos: ChunkPos) = isTickingChunk(pos.x, pos.z)
fun Level.isTickingChunk(chunkX: Int, chunkZ: Int) =
    (chunkSource as ServerChunkCache).isPositionTicking(ChunkPos.asLong(chunkX, chunkZ))

fun MinecraftServer.getLevelFromDimensionId(dimensionId: DimensionId): ServerLevel? {
    return getLevel(getResourceKey(dimensionId))
}

val Minecraft.shipObjectWorld get() = (this as IShipObjectWorldClientProvider).shipObjectWorld ?: DummyShipWorldClient
val ClientLevel?.shipObjectWorld get() = Minecraft.getInstance().shipObjectWorld

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
fun Level?.squaredDistanceBetweenInclShips(
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
    this.getShipManagingPos(x1.toInt() shr 4, z1.toInt() shr 4)?.shipToWorld?.let { m ->
        inWorldX1 = m.m00() * x1 + m.m10() * y1 + m.m20() * z1 + m.m30()
        inWorldY1 = m.m01() * x1 + m.m11() * y1 + m.m21() * z1 + m.m31()
        inWorldZ1 = m.m02() * x1 + m.m12() * y1 + m.m22() * z1 + m.m32()
    }
    this.getShipManagingPos(x2.toInt() shr 4, z2.toInt() shr 4)?.shipToWorld?.let { m ->
        inWorldX2 = m.m00() * x2 + m.m10() * y2 + m.m20() * z2 + m.m30()
        inWorldY2 = m.m01() * x2 + m.m11() * y2 + m.m21() * z2 + m.m31()
        inWorldZ2 = m.m02() * x2 + m.m12() * y2 + m.m22() * z2 + m.m32()
    }

    val dx = inWorldX2 - inWorldX1
    val dy = inWorldY2 - inWorldY1
    val dz = inWorldZ2 - inWorldZ1

    return dx * dx + dy * dy + dz * dz
}

private fun getShipObjectManagingPosImpl(world: Level?, chunkX: Int, chunkZ: Int): LoadedShip? {
    if (world != null && world.shipObjectWorld.isChunkInShipyard(chunkX, chunkZ, world.dimensionId)) {
        val ship = world.shipObjectWorld.allShips.getByChunkPos(chunkX, chunkZ, world.dimensionId)
        if (ship != null) {
            return world.shipObjectWorld.loadedShips.getById(ship.id)
        }
    }
    return null
}

/**
 * Get all ships intersecting an AABB in world-space, then call [cb] with the AABB itself,
 * followed by the AABB in the ship-space of the intersecting ships.
 */
fun Level?.transformFromWorldToNearbyShipsAndWorld(aabb: AABB, cb: Consumer<AABB>) {
    cb.accept(aabb)
    val tmpAABB = AABBd()
    getShipsIntersecting(aabb).forEach { ship ->
        cb.accept(tmpAABB.set(aabb).transform(ship.worldToShip).toMinecraft())
    }
}

fun Level?.transformToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabbRadius: Double): List<Vector3d> {
    val list = mutableListOf<Vector3d>()

    this?.transformToNearbyShipsAndWorld(x, y, z, aabbRadius) { x, y, z -> list.add(Vector3d(x, y, z)) }

    return list
}

fun Level?.transformToNearbyShipsAndWorld(
    x: Double, y: Double, z: Double, aabbRadius: Double, cb: DoubleTernaryConsumer
) {
    this?.transformToNearbyShipsAndWorld(x, y, z, aabbRadius, cb::accept)
}

inline fun Level?.transformToNearbyShipsAndWorld(
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

    for (nearbyShip in shipObjectWorld.allShips.getIntersecting(aabb)) {
        if (nearbyShip == currentShip) continue
        val posInShip = nearbyShip.worldToShip.transformPosition(posInWorld, temp0)
        cb(posInShip.x(), posInShip.y(), posInShip.z())
    }
}

// Level
fun Level.isChunkInShipyard(chunkX: Int, chunkZ: Int) =
    shipObjectWorld.isChunkInShipyard(chunkX, chunkZ, dimensionId)

fun Level.isBlockInShipyard(blockX: Int, blockY: Int, blockZ: Int) =
    shipObjectWorld.isBlockInShipyard(blockX, blockY, blockZ, dimensionId)

fun Level.isBlockInShipyard(pos: BlockPos) = isBlockInShipyard(pos.x, pos.y, pos.z)

fun Level.isBlockInShipyard(pos: Vec3) = isBlockInShipyard(pos.x.toInt(), pos.y.toInt(), pos.z.toInt())

fun Level.isBlockInShipyard(x: Double, y: Double, z: Double) =
    isBlockInShipyard(x.toInt(), y.toInt(), z.toInt())

fun Level?.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ)

fun Level?.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun Level?.getShipObjectManagingPos(pos: Vector3dc) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun Level?.getShipObjectManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipObjectManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun Level?.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

fun Level.getShipObjectEntityMountedTo(entity: Entity): LoadedShip? {
    val vehicle = entity.vehicle ?: return null
    return getShipObjectManagingPos(vehicle.position().toJOML())
}

// ClientLevel
fun ClientLevel?.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as ClientShip?

fun ClientLevel?.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ClientLevel?.getShipObjectManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipObjectManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun ClientLevel?.getShipObjectManagingPos(pos: Vector3dc) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun ClientLevel?.getShipObjectManagingPos(pos: Position) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun ClientLevel?.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

fun ClientLevel?.getShipObjectEntityMountedTo(entity: Entity): ClientShip? {
    val vehicle = entity.vehicle ?: return null
    return getShipObjectManagingPos(vehicle.position().toJOML())
}

// ServerWorld
fun ServerLevel?.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as ShipObjectServer?

fun ServerLevel?.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ServerLevel?.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

fun ServerLevel?.getShipObjectManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipObjectManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun ServerLevel?.getShipObjectManagingPos(pos: Vector3dc) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

private fun getShipManagingPosImpl(world: Level?, x: Int, z: Int): Ship? {
    return if (world != null && world.isChunkInShipyard(x, z)) {
        world.shipObjectWorld.allShips.getByChunkPos(x, z, world.dimensionId)
    } else {
        null
    }
}

fun ClientLevel?.transformRenderAABBToWorld(pos: Position, aabb: AABB): AABB {
    val ship = getShipObjectManagingPos(pos)
    if (ship != null) {
        return aabb.toJOML().transform(ship.renderTransform.shipToWorldMatrix).toMinecraft()
    }
    return aabb
}

fun Entity.getShipManaging(): Ship? = this.level.getShipManagingPos(this.position())

// Level
fun Level?.getShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipManagingPosImpl(this, chunkX, chunkZ)

fun Level?.getShipManagingPos(blockPos: BlockPos) =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun Level?.getShipManagingPos(pos: Position) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun Level?.getShipManagingPos(pos: Vector3dc) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun Level?.getShipManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun Level?.getShipManagingPos(posX: Float, posY: Float, posZ: Float) =
    getShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun Level?.getShipManagingPos(chunkPos: ChunkPos) =
    getShipManagingPos(chunkPos.x, chunkPos.z)

// ServerLevel
fun ServerLevel?.getShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipManagingPosImpl(this, chunkX, chunkZ) as ServerShip?

fun ServerLevel?.getShipManagingPos(blockPos: BlockPos) =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ServerLevel?.getShipManagingPos(pos: Vector3dc) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun ServerLevel?.getShipManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun ServerLevel?.getShipManagingPos(chunkPos: ChunkPos) =
    getShipManagingPos(chunkPos.x, chunkPos.z)

fun Ship.toWorldCoordinates(pos: BlockPos): Vector3d =
    shipToWorld.transformPosition(pos.toJOMLD())

fun Ship.toWorldCoordinates(pos: Vec3): Vec3 =
    shipToWorld.transformPosition(pos.toJOML()).toMinecraft()

fun Level?.toWorldCoordinates(pos: Vec3): Vec3 {
    return this?.getShipManagingPos(pos)?.toWorldCoordinates(pos) ?: pos
}

fun ClientLevel?.toShipRenderCoordinates(shipPos: Vec3, pos: Vec3): Vec3 =
    this?.getShipObjectManagingPos(shipPos)
        ?.renderTransform
        ?.worldToShip
        ?.transformPosition(pos.toJOML())
        ?.toMinecraft() ?: pos

fun Level?.toWorldCoordinates(pos: Vector3d): Vector3d {
    return this?.getShipManagingPos(pos)?.shipToWorld?.transformPosition(pos) ?: pos
}

@JvmOverloads
fun Level?.toWorldCoordinates(x: Double, y: Double, z: Double, dest: Vector3d = Vector3d()): Vector3d =
    getShipManagingPos(x, y, z)?.toWorldCoordinates(x, y, z) ?: dest.set(x, y, z)

@JvmOverloads
fun Ship.toWorldCoordinates(x: Double, y: Double, z: Double, dest: Vector3d = Vector3d()): Vector3d =
    transform.shipToWorld.transformPosition(dest.set(x, y, z))

fun LevelChunkSection.toDenseVoxelUpdate(chunkPos: Vector3ic): TerrainUpdate {
    val update = vsCore.newDenseTerrainUpdateBuilder(chunkPos.x(), chunkPos.y(), chunkPos.z())
    val info = BlockStateInfo.cache
    for (x in 0..15) {
        for (y in 0..15) {
            for (z in 0..15) {
                update.addBlock(x, y, z, info.get(getBlockState(x, y, z))?.second ?: vsCore.blockTypes.air)
            }
        }
    }
    return update.build()
}

/**
 * Transforms [pos] from ship space to world space if a ship exists there.
 *
 * Different from [getWorldCoordinates(World, Vector3d)] only in that resolves the ship owning
 * [blockPos] rather than inferring it from [pos], which might be helpful at the boundaries of ships.
 */
fun Level?.getWorldCoordinates(blockPos: BlockPos, pos: Vector3d): Vector3d {
    return this.getShipObjectManagingPos(blockPos)?.transform?.shipToWorld?.transformPosition(pos) ?: pos
}

fun Level?.getShipsIntersecting(aabb: AABB): Iterable<Ship> = allShips.getIntersecting(aabb.toJOML())
fun Level?.getShipsIntersecting(aabb: AABBdc): Iterable<Ship> = allShips.getShipDataIntersecting(aabb)

fun ServerLevel?.getShipsIntersecting(aabb: AABB): Iterable<ServerShip> = shipObjectWorld.loadedShips.getIntersecting(aabb.toJOML())

fun Level?.transformAabbToWorld(aabb: AABB): AABB = transformAabbToWorld(aabb.toJOML()).toMinecraft()
fun Level?.transformAabbToWorld(aabb: AABBd) = this?.transformAabbToWorld(aabb, aabb) ?: aabb
fun Level.transformAabbToWorld(aabb: AABBdc, dest: AABBd): AABBd {
    val ship1 = getShipManagingPos(aabb.minX(), aabb.minY(), aabb.minZ())
    val ship2 = getShipManagingPos(aabb.maxX(), aabb.maxY(), aabb.maxZ())

    // if both endpoints of the aabb are in the same ship, do the transform
    if (ship1 == ship2 && ship1 != null) {
        return aabb.transform(ship1.shipToWorld, dest)
    }

    return dest.set(aabb)
}

fun Entity.getPassengerPos(myRidingOffset: Double, partialTicks: Float): Vector3dc {
    return this.getPosition(partialTicks)
        .add(0.0, this.passengersRidingOffset + myRidingOffset, 0.0).toJOML()
}
