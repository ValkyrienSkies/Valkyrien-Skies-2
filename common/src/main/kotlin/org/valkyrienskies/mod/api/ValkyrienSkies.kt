@file:JvmName("ValkyrienSkies")
package org.valkyrienskies.mod.api

import com.mojang.blaze3d.vertex.PoseStack
import net.mehvahdjukaar.ml_classes.Vec2i
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
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
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4d
import org.joml.Matrix4dc
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.joml.Quaterniondc
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector2d
import org.joml.Vector2f
import org.joml.Vector2fc
import org.joml.Vector2i
import org.joml.Vector2ic
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector3ic
import org.joml.Vector4f
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.slf4j.LoggerFactory
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.util.functions.DoubleTernaryConsumer
import org.valkyrienskies.core.api.world.ClientShipWorld
import org.valkyrienskies.core.api.world.LevelYRange
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.VS2ChunkAllocator
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.util.DimensionIdProvider
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.resource.ResourceKeyAccessor
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.sqrt

val vsCore: VsiCore get() = ValkyrienSkiesMod.vsCore

/**
 * The singleton instance of [VsApi].
 */
@get:JvmName("api")
val vsApi: VsApi by lazy {
    try {
        val modClass = Class.forName("org.valkyrienskies.mod.common.ValkyrienSkiesMod")
        val getApi = modClass.getDeclaredMethod("getApi")
        val instance = getApi.invoke(null) as VsApi

        instance
    } catch (ex: Exception) {
        throw IllegalStateException("Failed initialize the Valkyrien Skies API. " +
            "Suggestion: Ensure that you have Valkyrien Skies installed.", ex)
    }
}

fun isConnectivityEnabled(isClient: Boolean): Boolean {
    return if (isClient) {
        VSGameConfig.CLIENT.Connectivity.enableClientConnectivity
    } else {
        VSCoreConfig.SERVER.sp.enableConnectivity
    }
}

// region misc extensions
val VsiPlayer.mcPlayer: Player get() = (this as MinecraftPlayer).playerEntityReference.get()!!
val Player.playerWrapper: MinecraftPlayer get() = (this as PlayerDuck).vs_getPlayer()
// endregion

// region Entity extensions
/**
 * Convenience function for
 * `entity.level().getShipManagingBlock(entity.position())`.
 *
 * @see getShipManagingBlock
 */
fun Entity?.getShipManagingEntity(): Ship? =
    this?.level()?.getShipManagingBlock(position())

fun Entity?.getShipManaging(): Ship? = this?.let { this.level().getShipManagingPos(this.position()) }

@GameTickOnly
fun Entity?.getEnclosingShip(): Ship? {
    if (this !is IEntityDraggingInformationProvider) return null
    val info = this.draggingInformation
    if (!info.isEntityBeingDraggedByAShip()) return null
    val shipId = info.lastShipStoodOn ?: return null
    return level().shipWorld?.loadedShips?.getById(shipId)
}
// endregion

// region Level extensions
/**
 * The String/[DimensionId] used within vs-core for representing this [Level].
 */
val Level.dimensionId: DimensionId
    get() = (this as DimensionIdProvider).dimensionId

@GameTickOnly
val Level?.shipWorld: ShipWorld?
    get() = when {
        this == null -> null
        this is ServerLevel -> this.server.shipWorld
        this is ClientLevel && this.isClientSide -> this.shipWorld
        else -> null
    }

@GameTickOnly val Level?.allShips get() = this.shipWorld?.allShips
@GameTickOnly val Level?.unloadedShips get() = this.shipWorld?.unloadedShips
@GameTickOnly val Level?.loadedShips get() = this.shipWorld?.loadedShips

@GameTickOnly val Level?.allBodies get() = this.shipWorld?.allBodies
@GameTickOnly val Level?.unloadedBodies get() = this.shipWorld?.unloadedBodies

val Level.yRange get() = LevelYRange(minBuildHeight, maxBuildHeight - 1)

private val levelResourceKeyMap: MutableMap<DimensionId, ResourceKey<Level>> = HashMap()

fun getResourceKey(dimensionId: DimensionId): ResourceKey<Level> {
    val cached = levelResourceKeyMap[dimensionId]
    if (cached == null) {
        val (registryNamespace, registryName, namespace, name) = dimensionId.split(":")
        val toReturn: ResourceKey<Level> = ResourceKeyAccessor.callCreate(
            ResourceLocation(registryNamespace, registryName), ResourceLocation(namespace, name)
        )
        levelResourceKeyMap[dimensionId] = toReturn
        return toReturn
    }
    return cached
}

@GameTickOnly
fun Level?.getShipById(id: ShipId): Ship? =
    shipWorld?.allShips?.getById(id)

@GameTickOnly
fun Level?.isBlockInShipyard(blockX: Int, blockZ: Int): Boolean =
    isChunkInShipyard(blockX shr 4, blockZ shr 4)

@GameTickOnly
fun Level?.isBlockInShipyard(pos: BlockPos): Boolean =
    isBlockInShipyard(pos.x, pos.z)

@GameTickOnly
fun Level?.isChunkInShipyard(chunkX: Int, chunkZ: Int): Boolean =
    this != null && shipWorld?.isChunkInShipyard(chunkX, chunkZ, dimensionId) ?: false

fun Level.isTickingChunk(pos: ChunkPos) = isTickingChunk(pos.x, pos.z)
fun Level.isTickingChunk(chunkX: Int, chunkZ: Int) =
    (chunkSource as ServerChunkCache).isPositionTicking(ChunkPos.asLong(chunkX, chunkZ))

/**
 * Check if a chunk is loaded enough for non-ticking VS2 work.
 *
 * This is for flows such as ship assembly and terrain copies that only need direct chunk access.
 * It is not a substitute for [isTickingChunk] when gameplay needs random, block, or entity ticks.
 *
 * Shipyard chunks loaded through [org.valkyrienskies.mod.common.world.VSTicketType.SHIP_CHUNK]
 * only reach FULL status, so they won't pass [isPositionTicking]. For those chunks we accept a
 * non-null [ServerChunkCache.getChunkNow].
 */
fun Level.isChunkLoadedForVS(pos: ChunkPos): Boolean {
    if (VS2ChunkAllocator.isChunkInShipyardCompanion(pos.x, pos.z)) {
        return (chunkSource as ServerChunkCache).getChunkNow(pos.x, pos.z) != null
    }
    return isTickingChunk(pos)
}

@GameTickOnly
fun Level?.getShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipManagingPosImpl(this, chunkX, chunkZ)

@GameTickOnly
fun Level?.getShipManagingPos(blockPos: Vec3i) =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

@GameTickOnly
fun Level?.getShipManagingPos(pos: Position) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun Level?.getShipManagingPos(pos: Vector3dc) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun Level?.getShipManagingPos(posX: Double, posZ: Double) =
    getShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

@GameTickOnly
fun Level?.getShipManagingPos(chunkPos: ChunkPos) =
    getShipManagingPos(chunkPos.x, chunkPos.z)

@GameTickOnly
fun Level?.getShipManagingBlock(x: Int, z: Int): Ship? =
    getShipManagingPos(x shr 4, z shr 4)

@GameTickOnly
fun Level?.getShipManagingBlock(pos: BlockPos?): Ship? =
    pos?.let { getShipManagingBlock(pos.x, pos.z) }

@GameTickOnly
fun Level?.getShipManagingBlock(x: Double, z: Double): Ship? =
    getShipManagingBlock(x.toInt(), z.toInt())

@GameTickOnly
fun Level?.getShipManagingBlock(v: Vector3dc?): Ship? =
    v?.let { getShipManagingBlock(v.x(), v.z()) }

@GameTickOnly
fun Level?.getShipManagingBlock(v: Position?): Ship? =
    v?.let { getShipManagingBlock(v.x(), v.z()) }
/**
 * If both endpoints of the given [aabb] are in the same ship, transform them
 * to the world and return the new AABB. Otherwise, leaves it untouched.
 */
@GameTickOnly
fun Level?.toWorld(aabb: AABBdc, dest: AABBd): AABBd {
    val ship1 = getShipManagingBlock(aabb.minX(), aabb.minZ())
        ?: return dest.set(aabb)
    val ship2 = getShipManagingBlock(aabb.maxX(), aabb.maxZ())
        ?: return dest.set(aabb)

    // if both endpoints of the aabb are in the same ship, do the transform
    if (ship1.id == ship2.id) {
        return aabb.transform(ship1.shipToWorld, dest)
    }

    return dest.set(aabb)
}

@GameTickOnly
fun Level?.toWorld(aabb: AABBd) =
    toWorld(aabb, aabb)

@GameTickOnly
fun Level?.toWorld(aabb: AABB): AABB =
    toWorld(aabb.toJOML()).toMinecraft()

@GameTickOnly
fun Level?.positionToWorld(pos: Vec3): Vec3 =
    getShipManagingBlock(pos).positionToWorld(pos)

@GameTickOnly
fun Level?.positionToWorld(pos: Vector3d): Vector3d =
    positionToWorld(pos, pos)

@GameTickOnly
fun Level?.positionToWorld(pos: Vector3dc, dest: Vector3d): Vector3d =
    getShipManagingBlock(pos).positionToWorld(pos, dest)

private inline fun Level?.positionToNearbyShipsAndWorld(
    x: Double,
    y: Double,
    z: Double,
    aabb: AABBdc?,
    cb: (Double, Double, Double) -> Unit
) {
    cb(x, y, z)
    positionToNearbyShips(x, y, z, aabb, cb)
}

/**
 * Gets all ships intersecting [aabb] (or x, y, z if [aabb] is null), then
 * transforms the position x, y, z to their respective ship spaces
 * and calls [cb] with the transformed positions.
 */
private inline fun Level?.positionToNearbyShips(
    x: Double,
    y: Double,
    z: Double,
    aabb: AABBdc?,
    cb: (Double, Double, Double) -> Unit
) {
    val queryAabb = aabb ?: AABBd(x, y, z, x, y, z)
    val localQueryAabb = AABBd()

    for (ship in getShipsIntersecting(queryAabb)) {
        queryAabb.transform(ship.worldToShip, localQueryAabb)
        if (!EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, localQueryAabb)) {
            continue
        }
        ship.worldToShip.transformPositionInline(x, y, z, cb)
    }
}

/**
 * Transforms the given world position into the ship space of all ships whose AABB intersects [aabb]
 */
@JvmOverloads
fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, aabb: AABBdc? = null): Iterable<Vector3d> {
    if (this == null) return emptyList()
    val list = mutableListOf<Vector3d>()
    positionToNearbyShips(x, y, z, aabb) { transformedX, transformedY, transformedZ ->
        list.add(Vector3d(transformedX, transformedY, transformedZ))
    }
    return list
}

@JvmOverloads
fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabb: AABBdc? = null): Iterable<Vector3d> =
    listOf(Vector3d(x, y, z)) + positionToNearbyShips(x, y, z, aabb)


fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShips(x, y, z, null, cb::accept)

fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, aabb: AABBdc?, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShips(x, y, z, aabb, cb::accept)

fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, aabbRadius: Double): Iterable<Vector3d> =
    positionToNearbyShips(x, y, z, newAABBWithRadius(x, y, z, aabbRadius))

fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, aabbRadius: Double, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShips(x, y, z, newAABBWithRadius(x, y, z, aabbRadius), cb)

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShipsAndWorld(x, y, z, null, cb::accept)

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabb: AABBdc?, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShipsAndWorld(x, y, z, aabb, cb::accept)

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabbRadius: Double): Iterable<Vector3d> =
    positionToNearbyShipsAndWorld(x, y, z, newAABBWithRadius(x, y, z, aabbRadius))

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabbRadius: Double, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShipsAndWorld(x, y, z, newAABBWithRadius(x, y, z, aabbRadius), cb)


/**
 * Returns all the ships whose AABBs contain x/y/z
 */
fun Level?.getShipsIntersecting(x: Double, y: Double, z: Double): Iterable<Ship> =
    vsApi.getShipsIntersecting(this, x, y, z)

/**
 * Returns all the ships whose AABBs intersect [aabb]
 */
fun Level?.getShipsIntersecting(aabb: AABBdc?): Iterable<Ship> =
    vsApi.getShipsIntersecting(this, aabb)

@GameTickOnly
fun Level?.distance(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double =
    sqrt(distanceSquared(x1, y1, z1, x2, y2, z2))

@GameTickOnly
fun Level?.distance(v1: Position, v2: Position): Double =
    sqrt(distanceSquared(v1, v2))

/**
 * Calculates squared distance including ships.
 *
 * Transforms the points into world space, then calculates the squared distance
 * between them.
 */
@GameTickOnly
fun Level?.distanceSquared(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double {
    var inWorldX1 = x1
    var inWorldY1 = y1
    var inWorldZ1 = z1
    var inWorldX2 = x2
    var inWorldY2 = y2
    var inWorldZ2 = z2

    val ship1 = this.getShipManagingBlock(x1, z1)
    val ship2 = this.getShipManagingBlock(x2, z2)

    // Do this transform manually to avoid allocation
    if (ship1 != ship2) {
        ship1?.shipToWorld?.transformPositionInline(x1, y1, z1) { x, y, z ->
            inWorldX1 = x
            inWorldY1 = y
            inWorldZ1 = z
        }
        ship2?.shipToWorld?.transformPositionInline(x2, y2, z2) { x, y, z ->
            inWorldX2 = x
            inWorldY2 = y
            inWorldZ2 = z
        }
    }

    val dx = inWorldX2 - inWorldX1
    val dy = inWorldY2 - inWorldY1
    val dz = inWorldZ2 - inWorldZ1

    return dx * dx + dy * dy + dz * dz
}

/**
 * Variant of [Vec3.distanceToSqr] including ships.
 */
@GameTickOnly
fun Level?.distanceSquared(v1: Position, v2: Position): Double =
    distanceSquared(v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z())

/**
 * Variant of [Vec3i.distSqr] including ships.
 */
@GameTickOnly
fun Level?.distanceSquared(v1: Vec3i, v2: Vec3i): Double =
    distanceSquared(v1.x.toDouble(), v1.y.toDouble(), v1.z.toDouble(),
        v2.x.toDouble(), v2.y.toDouble(), v2.z.toDouble())

/**
 * Variant of [Vec3i.distToCenterSqr] including ships.
 */
@GameTickOnly
fun Level?.distanceToCenterSquared(v1: Vec3i, x2: Double, y2: Double, z2: Double): Double =
    distanceSquared(v1.x.toDouble() + 0.5, v1.y.toDouble() + 0.5, v1.z.toDouble() + 0.5, x2, y2, z2)

/**
 * Variant of [Vec3i.distToCenterSqr] including ships.
 */
@GameTickOnly
fun Level?.distanceToCenterSquared(v1: Vec3i, v2: Position): Double =
    distanceToCenterSquared(v1, v2.x(), v2.y(), v2.z())

/**
 * Variant of [Vec3.closerThan] including ships.
 */
@GameTickOnly
fun Level?.closerThan(v1: Position, v2: Position, distance: Double): Boolean =
    distanceSquared(v1, v2) < distance.squared()

/**
 * Variant of [Vec3i.closerThan] including ships
 */
@GameTickOnly
fun Level?.closerThan(v1: Vec3i, v2: Vec3i, distance: Double): Boolean =
    distanceSquared(v1, v2) < distance.squared()

/**
 * Variant of [Vec3i.closerToCenterThan] including ships
 */
@GameTickOnly
fun Level?.closerToCenterThan(v1: Vec3i, x2: Double, y2: Double, z2: Double, distance: Double): Boolean =
    distanceToCenterSquared(v1, x2, y2, z2) < distance.squared()

/**
 * Variant of [Vec3i.closerToCenterThan] including ships
 */
@GameTickOnly
fun Level?.closerToCenterThan(v1: Vec3i, v2: Position, distance: Double): Boolean =
    distanceToCenterSquared(v1, v2) < distance.squared()

@GameTickOnly
fun Level?.getLoadedShipManagingPos(chunkX: Int, chunkZ: Int) = getLoadedShipManagingPosImpl(this, chunkX, chunkZ)

@GameTickOnly
fun Level?.getLoadedShipManagingPos(blockPos: Vec3i) =
    getLoadedShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

@GameTickOnly
fun Level?.getLoadedShipManagingPos(pos: Vector3dc) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun Level?.getLoadedShipManagingPos(pos: Position) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun Level?.getLoadedShipManagingPos(posX: Double, posZ: Double) =
    getLoadedShipManagingPos(posX.toInt(), posZ.toInt())

@GameTickOnly
fun Level?.getLoadedShipManagingPos(chunkPos: ChunkPos) =
    getLoadedShipManagingPos(chunkPos.x, chunkPos.z)
// endregion

// region ClientLevel / Minecraft extensions
@GameTickOnly
val ClientLevel?.shipWorld: ClientShipWorld?
    get() = (this as IShipObjectWorldClientProvider?)?.shipObjectWorld

@GameTickOnly
val Minecraft?.shipWorld: ClientShipWorld?
    get() = (this as IShipObjectWorldClientProvider?)?.shipObjectWorld

@GameTickOnly
fun ClientLevel?.getLoadedShipManagingPos(chunkX: Int, chunkZ: Int) =
    getLoadedShipManagingPosImpl(this, chunkX, chunkZ) as ClientShip?

@GameTickOnly
fun ClientLevel?.getLoadedShipManagingPos(blockPos: Vec3i) =
    getLoadedShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

@GameTickOnly
fun ClientLevel?.getLoadedShipManagingPos(pos: Vector3dc) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun ClientLevel?.getLoadedShipManagingPos(pos: Position) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun ClientLevel?.getLoadedShipManagingPos(posX: Double, posZ: Double) =
    getLoadedShipManagingPos(posX.toInt(), posZ.toInt())

@GameTickOnly
fun ClientLevel?.getLoadedShipManagingPos(chunkPos: ChunkPos) =
    getLoadedShipManagingPos(chunkPos.x, chunkPos.z)

@GameTickOnly
fun ClientLevel?.transformRenderAABBToWorld(pos: Position, aabb: AABB): AABB {
    val ship = getLoadedShipManagingPos(pos)
    if (ship != null) {
        return aabb.toJOML().transform(ship.renderTransform.toWorld).toMinecraft()
    }
    return aabb
}
// endregion

// region ServerLevel / MinecraftServer extensions
@GameTickOnly
val MinecraftServer?.shipWorld: ServerShipWorld?
    get() = (this as IShipObjectWorldServerProvider?)?.shipObjectWorld

@GameTickOnly
val ServerLevel?.shipWorld: ServerShipWorld?
    get() = (this as IShipObjectWorldServerProvider?)?.shipObjectWorld

val MinecraftServer?.vsPipeline
    get() = (this as IShipObjectWorldServerProvider?)?.vsPipeline

@GameTickOnly
fun MinecraftServer.executeIf(condition: () -> Boolean, toExecute: Runnable) {
    val registeredAtTick = this.tickCount
    vsCore.tickEndEvent.on { ev, handler ->
        if (ev.world == this.shipWorld) {
            if (condition()) {
                toExecute.run()
                handler.unregister()
            } else if (this.tickCount - registeredAtTick > 600) {
                LoggerFactory.getLogger("VS2").info("executeIf timed out after 600 ticks, forcing execution")
                toExecute.run()
                handler.unregister()
            }
        }
    }
}

fun MinecraftServer.getLevelFromDimensionId(dimensionId: DimensionId): ServerLevel? =
    getLevel(getResourceKey(dimensionId))

@GameTickOnly
fun ServerLevel?.getLoadedShipManagingPos(chunkX: Int, chunkZ: Int) =
    getLoadedShipManagingPosImpl(this, chunkX, chunkZ) as LoadedServerShip?

@GameTickOnly
fun ServerLevel?.getLoadedShipManagingPos(blockPos: Vec3i) =
    getLoadedShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

@GameTickOnly
fun ServerLevel?.getLoadedShipManagingPos(pos: Vector3dc) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun ServerLevel?.getLoadedShipManagingPos(pos: Position) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun ServerLevel?.getLoadedShipManagingPos(posX: Double, posZ: Double) =
    getLoadedShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

@GameTickOnly
fun ServerLevel?.getLoadedShipManagingPos(chunkPos: ChunkPos) =
    getLoadedShipManagingPos(chunkPos.x, chunkPos.z)
// endregion

// region Ship extensions
fun Ship?.positionToWorld(pos: Vec3): Vec3 =
    this?.transform.positionToWorld(pos)

fun Ship?.positionToWorld(pos: Vector3d): Vector3d =
    positionToWorld(pos, pos)

fun Ship?.positionToWorld(pos: Vector3dc, dest: Vector3d): Vector3d =
    this?.transform.positionToWorld(pos, dest)

fun Ship?.positionToShip(pos: Vec3): Vec3 =
    this?.transform.positionToShip(pos)

fun Ship?.positionToShip(pos: Vector3d): Vector3d =
    positionToShip(pos, pos)

fun Ship?.positionToShip(pos: Vector3dc, dest: Vector3d): Vector3d =
    this?.transform.positionToShip(pos, dest)

fun ShipTransform?.positionToWorld(pos: Vec3): Vec3 =
    this?.shipToWorld?.transformPosition(pos) ?: pos

fun ShipTransform?.positionToWorld(pos: Vector3d): Vector3d =
    positionToWorld(pos, pos)

fun ShipTransform?.positionToWorld(pos: Vector3dc, dest: Vector3d): Vector3d =
    this?.shipToWorld?.transformPosition(pos, dest) ?: dest.set(pos)

fun ShipTransform?.positionToShip(pos: Vec3): Vec3 =
    this?.worldToShip?.transformPosition(pos) ?: pos

fun ShipTransform?.positionToShip(pos: Vector3d): Vector3d =
    positionToShip(pos, pos)

fun ShipTransform?.positionToShip(pos: Vector3dc, dest: Vector3d): Vector3d =
    this?.worldToShip?.transformPosition(pos, dest) ?: dest.set(pos)

/**
 * The vanilla [StructureStart]s stored in this [Ship]'s chunks, i.e. the structures whose
 * metadata was carried onto the ship during assembly. Only loaded chunks are consulted.
 */
fun Ship?.getStructureStarts(level: ServerLevel): List<StructureStart> {
    if (this == null) return emptyList()
    val starts = ArrayList<StructureStart>()
    activeChunksSet.forEach { chunkX, chunkZ ->
        val chunk = level.chunkSource.getChunkNow(chunkX, chunkZ) ?: return@forEach
        starts.addAll(chunk.allStarts.values)
    }
    return starts
}
// endregion


// region conversions

// region mc to JOML

fun Vec3i.toJOML() = Vector3i().set(this)
fun Vec3i.toJOMLd() = Vector3d().set(this)
fun Vec3i.toJOMLf() = Vector3f().set(this)
fun Vec3.toJOML() = Vector3d().set(this)

fun Vec2i.toJOML() = Vector2i().set(this)
fun Vec2i.toJOMLd() = Vector2d().set(this)
fun Vec2i.toJOMLf() = Vector2f().set(this)
fun Vec2.toJOML() = Vector2f().set(this)

fun ChunkPos.toJOML() = Vector2i().set(this)
fun BlockPos.toJOML() = Vector3i().set(this)

/**
 * Converts an [AABB] to an [AABBd].
 *
 * @return a new [AABBd] with minX, minY, minZ, maxX, maxY, and maxZ components
 * matching this.
 */
fun AABB.toJOML() = AABBd().set(this)

fun Position.toJOML() = Vector3d().set(this)

// endregion

// region JOML to mc

/**
 * Converts a [Vector3ic] to a [Vec3i].
 *
 * @return a new [Vec3i] with x, y, and z components matching this.
 */
fun Vector3ic.toMinecraft() = Vec3i(x(), y(), z())

/**
 * Converts a [Vector3dc] to a [Vec3].
 *
 * @return a new [Vec3] with x, y, and z components matching this.
 */
fun Vector3dc.toMinecraft() = Vec3(x(), y(), z())

fun Vector2ic.toMinecraft() = Vec2i(x(), y())

fun Vector2fc.toMinecraft() = Vec2(x(), y())

/**
 * Converts an [AABBdc] to an [AABB].
 *
 * @return a new [AABB] with minX, minY, minZ, maxX, maxY, and maxZ components
 * matching this.
 */
fun AABBdc.toMinecraft() = AABB(minX(), minY(), minZ(), maxX(), maxY(), maxZ())

/**
 * Converts a [Vector3ic] to a [BlockPos].
 *
 * @return a new [BlockPos] with x, y, and z components matching this.
 */
fun Vector3ic.toBlockPos() = BlockPos(x(), y(), z())
fun Vector2ic.toChunkPos() = ChunkPos(x(), y())
// endregion

// endregion

// region JOML extensions

/**
 * Sets the x, y, and z components to match the supplied vector [v].
 *
 * @return this
 */
fun Vector3i.set(v: Vec3i) = also {
    x = v.x
    y = v.y
    z = v.z
}

/**
 * Sets the x, y, and z components to match the supplied vector [v].
 *
 * @return this
 */
fun Vector3d.set(v: Vec3i) = also {
    x = v.x.toDouble()
    y = v.y.toDouble()
    z = v.z.toDouble()
}

/**
 * Sets the x, y, and z components to match the supplied vector [v].
 *
 * @return this
 */
fun Vector3f.set(v: Vec3i) = also {
    x = v.x.toFloat()
    y = v.y.toFloat()
    z = v.z.toFloat()
}

/**
 * Sets the x, y, and z components to match the supplied vector [v].
 *
 * @return this
 */
fun Vector3d.set(v: Position) = also {
    x = v.x()
    y = v.y()
    z = v.z()
}

fun Vector3d.set(v: Vec3) = also {
    x = v.x
    y = v.y
    z = v.z
}

fun Vector2i.set(v: Vec2i) = also {
    x = v.x
    y = v.y
}

fun Vector2f.set(v: Vec2i) = also {
    x = v.x.toFloat()
    y = v.y.toFloat()
}

fun Vector2d.set(v: Vec2i) = also {
    x = v.x.toDouble()
    y = v.y.toDouble()
}

fun Vector2f.set(v: Vec2) = also {
    x = v.x
    y = v.y
}

fun Vector2i.set(pos: ChunkPos) = also {
    x = pos.x
    y = pos.z
}


/**
 * Sets the minX, minY, minZ, maxX, maxY, and maxZ components to match the
 * supplied aabb [v].
 *
 * @return this
 */
fun AABBd.set(v: AABB) = also {
    minX = v.minX
    minY = v.minY
    minZ = v.minZ
    maxX = v.maxX
    maxY = v.maxY
    maxZ = v.maxZ
}

@JvmOverloads
fun Matrix4dc.transformDirection(v: Vec3i, dest: Vector3d = Vector3d()): Vector3d =
    transformDirection(dest.set(v.x.toDouble(), v.y.toDouble(), v.z.toDouble()))

@JvmOverloads
fun Matrix4dc.transformDirection(dir: Direction, dest: Vector3d = Vector3d()) = transformDirection(dir.normal, dest)

fun Matrix4dc.transform(v: Vector4f) = v.also {
    it.set(
        (m00() * v.x() + m01() * v.y() + m02() * v.z() + m03() * v.w()).toFloat(),
        (m10() * v.x() + m11() * v.y() + m12() * v.z() + m13() * v.w()).toFloat(),
        (m20() * v.x() + m21() * v.y() + m22() * v.z() + m23() * v.w()).toFloat(),
        (m30() * v.x() + m31() * v.y() + m32() * v.z() + m33() * v.w()).toFloat()
    )
}

/**
 * Transforms the position [v] by this.
 *
 * @return a new [Vec3] representing the transformed position
 *
 * @see Matrix4dc.transformPosition
 */
fun Matrix4dc.transformPosition(v: Position): Vec3 {
    transformPositionInline(v.x(), v.y(), v.z()) { x, y, z ->
        return Vec3(x, y, z)
    }
}

fun Matrix4d.mul(m: Matrix4fc): Matrix4d = mul(m, this)

fun Quaterniondc.toFloat(): Quaternionfc = Quaternionf(x(), y(), z(), w())

fun Matrix4dc.toFloat(): Matrix4fc = Matrix4f().set(this)

fun Vector3dc.floorToInt() = Vector3i(this.x().toInt(), this.y().toInt(), this.z().toInt())
// endregion

// region mc extensions
fun PoseStack.multiply(modelTransform: Matrix4dc, normalTransform: Quaterniondc) = also {
    val last = last()

    val newPose = Matrix4d().set(last.pose()).mul(modelTransform)
    val newNormal = last.normal().mul(Matrix3f().set(normalTransform))

    last.pose().set(newPose)
    last.normal().set(newNormal)
}

fun PoseStack.multiply(modelTransform: Matrix4dc) = also {
    val last = last()
    val modelTransformF = modelTransform.toFloat()
    val newPose: Matrix4fc = Matrix4f(last.pose()).mul(modelTransformF)
    last.pose().set(newPose)
}

fun Vec3i.toDouble() = Vec3(x.toDouble(), y.toDouble(), z.toDouble())
// endregion

// region private utils
private fun newAABBWithRadius(x: Double, y: Double, z: Double, r: Double) =
    AABBd(x - r, y - r, z - r, x + r, y + r, z + r)

private fun Double.squared() = this * this

/**
 * Transform a position without allocating any intermediate objects
 */
@OptIn(ExperimentalContracts::class)
private inline fun <T> Matrix4dc.transformPositionInline(
    x: Double,
    y: Double,
    z: Double,
    transformed: (Double, Double, Double) -> T
): T {
    contract {
        callsInPlace(transformed, InvocationKind.EXACTLY_ONCE)
    }
    return transformed(
        m00() * x + m10() * y + m20() * z + m30(),
        m01() * x + m11() * y + m21() * z + m31(),
        m02() * x + m12() * y + m22() * z + m32()
    )
}

@GameTickOnly
private fun getLoadedShipManagingPosImpl(level: Level?, chunkX: Int, chunkZ: Int): LoadedShip? {
    level ?: return null
    val sw = level.shipWorld ?: return null
    val dim = level.dimensionId
    return if (sw.isChunkInShipyard(chunkX, chunkZ, dim))
        sw.loadedShips.getByChunkPos(chunkX, chunkZ, dim)
    else null
}

@GameTickOnly
private fun getShipManagingPosImpl(level: Level?, chunkX: Int, chunkZ: Int): Ship? {
    level ?: return null
    val sw = level.shipWorld ?: return null
    val dim = level.dimensionId
    return if (sw.isChunkInShipyard(chunkX, chunkZ, dim))
        sw.allShips.getByChunkPos(chunkX, chunkZ, dim)
    else null
}
// endregion
