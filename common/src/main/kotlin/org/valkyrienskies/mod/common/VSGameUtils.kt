package org.valkyrienskies.mod.common

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.core.SectionPos
import net.minecraft.core.Vec3i
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.thread.BlockableEventLoop
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3ic
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.joml.primitives.AABBic
import org.joml.primitives.Intersectiond
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.util.functions.DoubleTernaryConsumer
import org.valkyrienskies.core.api.world.LevelYRange
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.VsiShipWorld
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.ASSEMBLE_BLACKLIST
import org.valkyrienskies.mod.common.entity.ShipMountedToData
import org.valkyrienskies.mod.common.entity.ShipMountedToDataProvider
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager
import org.valkyrienskies.mod.common.util.DimensionIdProvider
import org.valkyrienskies.mod.common.util.EntityDragger.serversidePosition
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.util.set
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.mixin.accessors.resource.ResourceKeyAccessor
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck
import java.util.function.Consumer

val vsCore get() = ValkyrienSkiesMod.vsCore

val Level?.shipWorldNullable: VsiShipWorld?
    get() = when {
        this == null -> null
        this is ServerLevel -> server.shipObjectWorld
        this.isClientSide && this is ClientLevel -> this.shipObjectWorld
        else -> null
    }

val Level?.shipObjectWorld
    get() = shipWorldNullable ?: vsCore.dummyShipWorldClient

val Level?.allShips get() = this.shipObjectWorld.allShips
val Level?.unloadedShips get() = this.shipObjectWorld.unloadedShips
val Level?.allBodies get() = this.shipObjectWorld.allBodies

val MinecraftServer.shipObjectWorld: VsiServerShipWorld
    get() = (this as IShipObjectWorldServerProvider).shipObjectWorld ?: vsCore.dummyShipWorldServer
val MinecraftServer.vsPipeline get() = (this as IShipObjectWorldServerProvider).vsPipeline!!

val ServerLevel?.shipObjectWorld: VsiServerShipWorld
    get() = this?.server?.shipObjectWorld ?: vsCore.dummyShipWorldServer

val Level.dimensionId: DimensionId
    get() {
        this as DimensionIdProvider
        return dimensionId
    }

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

fun MinecraftServer.executeIf(condition: () -> Boolean, toExecute: Runnable) {
    val registeredAtTick = this.tickCount
    vsCore.tickEndEvent.on { ev, handler ->
        if (ev.world == this.shipObjectWorld) {
            if (condition()) {
                toExecute.run()
                handler.unregister()
            } else if (this.tickCount - registeredAtTick > 600) {
                // Safety timeout: if the condition hasn't been met after 600 ticks (30 seconds),
                // execute anyway and unregister. This prevents executeIf callbacks from accumulating
                // forever and potentially blocking server shutdown.
                org.slf4j.LoggerFactory.getLogger("VS2").info(" executeIf timed out after 600 ticks — forcing execution")
                toExecute.run()
                handler.unregister()
            }
        }
    }
}

val Level.yRange get() = LevelYRange(minBuildHeight, maxBuildHeight - 1)

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

fun MinecraftServer.getLevelFromDimensionId(dimensionId: DimensionId): ServerLevel? {
    return getLevel(getResourceKey(dimensionId))
}

val Minecraft.shipObjectWorld
    get() = (this as IShipObjectWorldClientProvider).shipObjectWorld ?: vsCore.dummyShipWorldClient
val ClientLevel?.shipObjectWorld get() = Minecraft.getInstance().shipObjectWorld

val VsiPlayer.mcPlayer: Player get() = (this as MinecraftPlayer).playerEntityReference.get()!!

val Player.playerWrapper get() = (this as PlayerDuck).vs_getPlayer()

/**
 * Like [Entity.squaredDistanceTo] except the destination is transformed into world coordinates if it is a ship
 */
fun Entity.squaredDistanceToInclShips(x: Double, y: Double, z: Double): Double {
    val pos = if (getShipMountedTo(this) != null) getShipMountedToData(
        this, null
    )!!.mountPosInShip.toMinecraft() else this.serversidePosition()
    return level().squaredDistanceBetweenInclShips(x, y, z, pos.x, pos.y, pos.z)
}

/**
 * Meant to be used with @WrapOperation to replace distance checks in a compatible way
 */
fun Level?.squaredDistanceBetweenInclShips(
    v1: Vec3,
    v2: Vec3,
    originalDistance: Operation<Double>?
): Double {
    if (originalDistance == null) {
        return squaredDistanceBetweenInclShips(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z) // fast path
    }

    val inWorldV1 = toWorldCoordinates(v1)
    val inWorldV2 = toWorldCoordinates(v2)

    return originalDistance.call(inWorldV1, inWorldV2)
}

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
    // Perf: resolve the shipObjectWorld + dimensionId extension getters once. This is one of the
    // hottest VS calls on the server/render threads; the old body invoked `world.shipObjectWorld`
    // three times and `world.dimensionId` twice per call (each is a non-trivial instanceof/cast
    // dispatch). Behavior is identical.
    if (world == null) return null
    val sow = world.shipObjectWorld
    val dim = world.dimensionId
    if (!sow.isChunkInShipyard(chunkX, chunkZ, dim)) return null
    val ship = sow.allShips.getByChunkPos(chunkX, chunkZ, dim) ?: return null
    return sow.loadedShips.getById(ship.id)
}

/**
 * Get all ships intersecting an AABB in world-space, then call [cb] with the AABB itself,
 * followed by the AABB in the ship-space of the intersecting ships.
 */
fun Level.transformFromWorldToNearbyShipsAndWorld(aabb: AABB, cb: Consumer<AABB>) {
    val tmpAABB = AABBd()
    cb.accept(aabb)
    getShipsIntersecting(aabb).forEach { ship ->
        tmpAABB.set(aabb).transform(ship.worldToShip)
        if (EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, tmpAABB)) {
            cb.accept(tmpAABB.toMinecraft())
        }
    }
}

/**
 * Same as [transformFromWorldToNearbyShipsAndWorld] but does not call [cb] with the original [aabb].
 *
 * Not sure if this is actually useful, but our MixinEntity for water-flowing on ships seems to need it.
 */
fun Level.transformFromWorldToNearbyShips(aabb: AABB, cb: Consumer<AABB>) {
    val tmpAABB = AABBd()
    //cb.accept(aabb)
    getShipsIntersecting(aabb).forEach { ship ->
        tmpAABB.set(aabb).transform(ship.worldToShip)
        if (EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, tmpAABB)) {
            cb.accept(tmpAABB.toMinecraft())
        }
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

    for (nearbyShip in shipObjectWorld.allShips.getIntersecting(aabb, this!!.dimensionId)) {
        if (nearbyShip.id == currentShip?.id) continue
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

fun Level?.getLoadedShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ)

fun Level?.getLoadedShipManagingPos(blockPos: Vec3i) =
    getLoadedShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun Level?.getLoadedShipManagingPos(pos: Vector3dc) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun Level?.getLoadedShipManagingPos(posX: Double, posY: Double, posZ: Double) =
    getLoadedShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun Level?.getLoadedShipManagingPos(chunkPos: ChunkPos) =
    getLoadedShipManagingPos(chunkPos.x, chunkPos.z)

@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(chunkX, chunkZ)"))
fun Level?.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(blockPos)"))
fun Level?.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(pos)"))
fun Level?.getShipObjectManagingPos(pos: Vector3dc) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(posX, posY, posZ)"))
fun Level?.getShipObjectManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipObjectManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(chunkPos)"))
fun Level?.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

// ClientLevel
fun ClientLevel?.getLoadedShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as ClientShip?

fun ClientLevel?.getLoadedShipManagingPos(blockPos: Vec3i) =
    getLoadedShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ClientLevel?.getLoadedShipManagingPos(posX: Double, posY: Double, posZ: Double) =
    getLoadedShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun ClientLevel?.getLoadedShipManagingPos(pos: Vector3dc) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun ClientLevel?.getLoadedShipManagingPos(pos: Position) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

fun ClientLevel?.getLoadedShipManagingPos(chunkPos: ChunkPos) =
    getLoadedShipManagingPos(chunkPos.x, chunkPos.z)

@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(chunkX, chunkZ)"))
fun ClientLevel?.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as ClientShip?
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(blockPos)"))
fun ClientLevel?.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(pos)"))
fun ClientLevel?.getShipObjectManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipObjectManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(pos)"))
fun ClientLevel?.getShipObjectManagingPos(pos: Vector3dc) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(chunkPos)"))
fun ClientLevel?.getShipObjectManagingPos(pos: Position) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(chunkPos)"))
fun ClientLevel?.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)

// ServerWorld
fun ServerLevel?.getLoadedShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as LoadedServerShip?

fun ServerLevel?.getLoadedShipManagingPos(blockPos: Vec3i) =
    getLoadedShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

fun ServerLevel?.getLoadedShipManagingPos(chunkPos: ChunkPos) =
    getLoadedShipManagingPos(chunkPos.x, chunkPos.z)

fun ServerLevel?.getLoadedShipManagingPos(posX: Double, posY: Double, posZ: Double) =
    getLoadedShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

fun ServerLevel?.getLoadedShipManagingPos(pos: Vector3dc) =
    getLoadedShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(chunkX, chunkZ)"))
fun ServerLevel?.getShipObjectManagingPos(chunkX: Int, chunkZ: Int) =
    getShipObjectManagingPosImpl(this, chunkX, chunkZ) as LoadedServerShip?
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(blockPos)"))
fun ServerLevel?.getShipObjectManagingPos(blockPos: Vec3i) =
    getShipObjectManagingPos(blockPos.x shr 4, blockPos.z shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(pos)"))
fun ServerLevel?.getShipObjectManagingPos(chunkPos: ChunkPos) =
    getShipObjectManagingPos(chunkPos.x, chunkPos.z)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(posX, posY, posZ)"))
fun ServerLevel?.getShipObjectManagingPos(posX: Double, posY: Double, posZ: Double) =
    getShipObjectManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)
@Deprecated("Use getLoadedShipManagingPos instead", ReplaceWith("getLoadedShipManagingPos(pos)"))
fun ServerLevel?.getShipObjectManagingPos(pos: Vector3dc) =
    getShipObjectManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

private fun getShipManagingPosImpl(world: Level?, x: Int, z: Int): Ship? {
    // Perf: resolve shipObjectWorld + dimensionId once. The old body went through
    // `world.isChunkInShipyard(x, z)` (which itself reads shipObjectWorld + dimensionId) and then
    // read both extension getters AGAIN for getByChunkPos. This is among the most frequently called
    // functions in the whole mod (collision, drag, spawning, rendering all funnel through it), so
    // halving the per-call getter dispatch is worthwhile. Behavior is identical.
    if (world == null) return null
    val sow = world.shipObjectWorld
    val dim = world.dimensionId
    return if (sow.isChunkInShipyard(x, z, dim)) {
        sow.allShips.getByChunkPos(x, z, dim)
    } else {
        null
    }
}

fun ClientLevel?.transformRenderAABBToWorld(pos: Position, aabb: AABB): AABB {
    val ship = getLoadedShipManagingPos(pos)
    if (ship != null) {
        return aabb.toJOML().transform(ship.renderTransform.shipToWorldMatrix).toMinecraft()
    }
    return aabb
}

fun Entity?.getShipManaging(): Ship? = this?.let { this.level().getShipManagingPos(this.position()) }

/**
 * Returns the ship the entity is currently being dragged by (per [EntityDraggingInformation]),
 * i.e. the ship the entity is physically standing on or has stood on within the last
 * [EntityDraggingInformation.TICKS_TO_DRAG_ENTITIES] ticks. Differs from [getShipManaging]
 * (chunk-claim ownership) — this answers "which ship is this mob actually on?" using the
 * dragger attribution rather than worldAABB containment, so a mob in midair below a flying
 * ship correctly returns null instead of being misattributed to the ship overhead.
 */
fun Entity?.getEnclosingShip(): Ship? {
    if (this !is IEntityDraggingInformationProvider) return null
    val info = this.draggingInformation
    if (!info.isEntityBeingDraggedByAShip()) return null
    val shipId = info.lastShipStoodOn ?: return null
    return level().shipObjectWorld?.loadedShips?.getById(shipId)
}

/** Result of [getShipBlockStoodOn]: the ship the entity is standing on plus the shipyard cell of the supporting block. */
data class ShipBlock(@JvmField val ship: Ship, @JvmField val shipLocalBlockPos: BlockPos)

/**
 * Geometric "is the entity standing on a ship, and on which shipyard cell?" check. For each ship whose
 * AABB intersects a 1-cube around the entity's foot, project the foot through `worldToShip` and check
 * whether the resulting shipyard cell has an actual non-air block (or, for fence/slab edge cases, the
 * cell one below). Returns the first matching ship + cell.
 *
 * [probeDepth] controls how far below the entity's foot to probe. Use a tight value (e.g. 0.2) for
 * spawn-time attribution where the entity is firmly on the surface; use a permissive value (e.g. 0.5)
 * for pathfinding start anchoring where physics jitter / mid-jump should still resolve to the ship.
 *
 * Strictly tighter than worldAABB containment because it confirms an actual ship block at the foot
 * rather than just AABB overlap (a sparse ship's worldAABB encloses huge empty volume). Mirrors the
 * algorithm used by `MixinEntity.getPosStandingOnFromShips` for the dragger's per-tick detection.
 */
fun Entity?.getShipBlockStoodOn(probeDepth: Double): ShipBlock? {
    if (this == null) return null
    val level = level()
    val foot = Vector3d(this.x, this.boundingBox.minY - probeDepth, this.z)
    val probe = AABBd(foot.x - 0.5, foot.y - 0.5, foot.z - 0.5, foot.x + 0.5, foot.y + 0.5, foot.z + 0.5)
    for (ship in level.getShipsIntersecting(probe)) {
        val w2s = ship.transform.worldToShip
        val local = w2s.transformPosition(foot, Vector3d())
        val centerPos = BlockPos.containing(local.x, local.y, local.z)
        if (!level.getBlockState(centerPos).isAir) return ShipBlock(ship, centerPos)
        // One cell below for fence/slab edge cases at the foot center.
        val belowLocal = w2s.transformPosition(Vector3d(foot.x, foot.y - 1.0, foot.z))
        val belowPos = BlockPos.containing(belowLocal.x, belowLocal.y, belowLocal.z)
        if (!level.getBlockState(belowPos).isAir) return ShipBlock(ship, belowPos)

        val halfW = this.boundingBox.xsize / 2.0
        val halfD = this.boundingBox.zsize / 2.0
        if (halfW <= 0.5 && halfD <= 0.5) continue

        val centerLong = centerPos.asLong()
        val cornerScratch = Vector3d()
        val cornerXs = doubleArrayOf(this.x - halfW, this.x + halfW, this.x - halfW, this.x + halfW)
        val cornerZs = doubleArrayOf(this.z - halfD, this.z - halfD, this.z + halfD, this.z + halfD)
        var lastSeen = centerLong
        for (i in 0 until 4) {
            cornerScratch.set(cornerXs[i], foot.y, cornerZs[i])
            w2s.transformPosition(cornerScratch)
            val cornerPos = BlockPos.containing(cornerScratch.x, cornerScratch.y, cornerScratch.z)
            val cornerLong = cornerPos.asLong()
            if (cornerLong == centerLong || cornerLong == lastSeen) continue
            lastSeen = cornerLong
            if (!level.getBlockState(cornerPos).isAir) return ShipBlock(ship, cornerPos)
        }
    }
    return null
}

/**
 * [getShipBlockStoodOn] with a tight 0.2-block probe depth, returning just the ship. Use at
 * finalizeSpawn / mob-spawn time, before the dragger system has populated `lastShipStoodOn`.
 */
fun Entity?.getShipStoodOn(): Ship? = getShipBlockStoodOn(0.2)?.ship

/** Entity's `blockPosition()` projected into its enclosing ship, else world `blockPosition()`. */
@JvmStatic
fun shipMountedSpawnSeedPos(entity: Entity): BlockPos {
    val ship = entity.getShipStoodOn() ?: return entity.blockPosition()
    val local = ship.transform.worldToShip.transformPosition(entity.x, entity.y, entity.z, Vector3d())
    return BlockPos.containing(local.x, local.y, local.z)
}

/** A shipyard `BlockPos`'s Y projected to its world-rendered Y, else `original`. */
@JvmStatic
fun shipProjectedWorldY(level: LevelAccessor, pos: BlockPos, original: Int): Int {
    if (level !is ServerLevel) return original
    val ship = level.getShipManagingPos(pos) ?: return original
    val rendered = ship.transform.shipToWorld.transformPosition(
        pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, Vector3d()
    )
    return Math.floor(rendered.y).toInt()
}

/**
 * Vanilla `max(blockLight, skyLight - skyDarken)` re-evaluated with the sky component as
 * `min(shipSky, worldSky)` so a ship under a world ceiling reads dim. `original` returned
 * when not on a ship.
 */
@JvmStatic
fun shipAwareCombinedBrightness(getter: BlockAndTintGetter, pos: BlockPos, skyDarken: Int, original: Int): Int {
    if (getter !is ServerLevel) return original
    val ship = getter.getShipManagingPos(pos) ?: return original
    val shipSky = getter.getBrightness(LightLayer.SKY, pos)
    val rendered = ship.transform.shipToWorld.transformPosition(
        pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, Vector3d()
    )
    val worldSky = getter.getBrightness(LightLayer.SKY,
        BlockPos.containing(rendered.x, rendered.y, rendered.z))
    val adjustedSky = Math.max(0, Math.min(shipSky, worldSky) - skyDarken)
    val shipBlock = getter.getBrightness(LightLayer.BLOCK, pos)
    return Math.max(adjustedSky, shipBlock)
}

/** Sky-only counterpart to [shipAwareCombinedBrightness] — returns `min(original, worldSky)`. */
@JvmStatic
fun shipAwareSkyBrightness(getter: BlockAndTintGetter, pos: BlockPos, original: Int): Int {
    if (getter !is ServerLevel) return original
    val ship = getter.getShipManagingPos(pos) ?: return original
    val rendered = ship.transform.shipToWorld.transformPosition(
        pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, Vector3d()
    )
    val worldSky = getter.getBrightness(LightLayer.SKY,
        BlockPos.containing(rendered.x, rendered.y, rendered.z))
    return Math.min(original, worldSky)
}

/** Vanilla `canSeeSky` projected to the ship's world pos, plus a heightmap-including-ships
 *  check so other ships above also block the sky view. */
@JvmStatic
fun shipAwareCanSeeSky(level: Level, pos: BlockPos): Boolean {
    val ship = level.getShipManagingPos(pos)
    val worldPos = if (ship != null) {
        val world = ship.transform.shipToWorld.transformPosition(
            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, Vector3d()
        )
        BlockPos.containing(world.x, world.y, world.z)
    } else pos
    if (!level.canSeeSky(worldPos)) return false
    val heightInclShips = CompatUtil.getWorldHeightmapPosIncludingShips(
        level, Heightmap.Types.MOTION_BLOCKING, worldPos
    )
    return worldPos.y + 1 >= heightInclShips.y
}

/**
 * `Entity.getLightLevelDependentMagicValue()` re-computed against `min(shipSky, worldSky)`
 * and `max(shipBlock, worldBlock)` so AI gates that read it see the entity's actual local
 * lighting on the ship. Null when not on a ship — caller falls back to vanilla.
 */
@JvmStatic
fun shipAwareEntityLightLevelDependentMagicValue(entity: Entity): Float? {
    val level = entity.level()
    if (level !is ServerLevel) return null
    val ship = entity.getEnclosingShip() ?: return null
    val worldPos = BlockPos.containing(entity.x, entity.eyeY, entity.z)
    val shipyard = ship.transform.worldToShip.transformPosition(
        entity.x, entity.eyeY, entity.z, Vector3d()
    )
    val shipyardPos = BlockPos.containing(shipyard.x, shipyard.y, shipyard.z)
    val shipSky = level.getBrightness(LightLayer.SKY, shipyardPos)
    val worldSky = level.getBrightness(LightLayer.SKY, worldPos)
    val effectiveSky = Math.max(0, Math.min(shipSky, worldSky) - level.skyDarken)
    val shipBlock = level.getBrightness(LightLayer.BLOCK, shipyardPos)
    val worldBlock = level.getBrightness(LightLayer.BLOCK, worldPos)
    val rawBrightness = Math.max(effectiveSky, Math.max(shipBlock, worldBlock))
    val brightness = rawBrightness / 15.0f
    val scaled = brightness / (4.0f - 3.0f * brightness)
    val ambient = level.dimensionType().ambientLight()
    return scaled + ambient * (1.0f - scaled)
}

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

fun Level?.toWorldCoordinates(pos: BlockPos): Vec3 {
    return this?.getShipManagingPos(pos)?.toWorldCoordinates(pos)?.toMinecraft() ?: pos.toJOMLD().toMinecraft()
}

fun Level?.toWorldCoordinates(pos: Vec3): Vec3 {
    return this?.getShipManagingPos(pos)?.toWorldCoordinates(pos) ?: pos
}

fun ClientLevel?.toShipRenderCoordinates(shipPos: Vec3, pos: Vec3): Vec3 =
    this?.getLoadedShipManagingPos(shipPos)
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

@JvmOverloads
fun LevelChunkSection.toDenseVoxelUpdate(chunkPos: Vector3ic, level: Level? = null): VsiTerrainUpdate {
    val update = vsCore.newDenseTerrainUpdateBuilder(chunkPos.x(), chunkPos.y(), chunkPos.z())
    val info = BlockStateInfo.cache
    val mutablePos = if (level == null) null else BlockPos.MutableBlockPos()
    val baseX = SectionPos.sectionToBlockCoord(chunkPos.x())
    val baseY = SectionPos.sectionToBlockCoord(chunkPos.y())
    val baseZ = SectionPos.sectionToBlockCoord(chunkPos.z())
    for (x in 0..15) {
        for (y in 0..15) {
            for (z in 0..15) {
                val blockState = getBlockState(x, y, z)
                val defaultBlockType = info.get(blockState)?.second ?: vsCore.blockTypes.air
                update.addBlock(
                    x, y, z,
                    blockState.resolvePhysicsBlockTypeForAirPocket(
                        level,
                        mutablePos?.set(baseX + x, baseY + y, baseZ + z),
                        defaultBlockType,
                    )
                )
            }
        }
    }
    return update.build()
}

private fun BlockState.resolvePhysicsBlockTypeForAirPocket(
    level: Level?,
    blockPos: BlockPos?,
    defaultBlockType: VsiBlockType,
): VsiBlockType {
    if (level == null || blockPos == null || !isAir) return defaultBlockType
    return if (ShipWaterPocketManager.isShipyardBlockPosInShipAirPocket(level, blockPos)) {
        vsCore.blockTypes.displacementAir
    } else {
        defaultBlockType
    }
}

/**
 * Transforms [pos] from ship space to world space if a ship exists there.
 *
 * Different from [getWorldCoordinates(World, Vector3d)] only in that resolves the ship owning
 * [blockPos] rather than inferring it from [pos], which might be helpful at the boundaries of ships.
 */
fun Level?.getWorldCoordinates(blockPos: BlockPos, pos: Vector3d): Vector3d {
    return this.getLoadedShipManagingPos(blockPos)?.transform?.shipToWorld?.transformPosition(pos) ?: pos
}

fun Level.getShipsIntersecting(aabb: AABB): Iterable<Ship> = getShipsIntersecting(aabb.toJOML())
fun Level.getShipsIntersecting(aabb: AABBdc): Iterable<Ship> = allShips.getIntersecting(aabb, dimensionId)
fun Level?.transformAabbToWorld(aabb: AABB): AABB = transformAabbToWorld(aabb.toJOML()).toMinecraft()
fun Level?.transformAabbToWorld(aabb: AABBd) = this?.transformAabbToWorld(aabb, aabb) ?: aabb
fun Level?.transformAabbToWorld(aabb: AABBdc, dest: AABBd): AABBd {
    val ship1 = getShipManagingPos(aabb.minX(), aabb.minY(), aabb.minZ())
        ?: return dest.set(aabb)
    val ship2 = getShipManagingPos(aabb.maxX(), aabb.maxY(), aabb.maxZ())
        ?: return dest.set(aabb)

    // if both endpoints of the aabb are in the same ship, do the transform
    if (ship1.id == ship2.id) {
        return aabb.transform(ship1.shipToWorld, dest)
    }

    return dest.set(aabb)
}

/**
 * Like [transformAabbToWorld], but yields the actual OBB (center, three unit axes, half-extents)
 * rather than the loose AABB around it. Output layout matches [Intersectiond]'s `testObOb`.
 */
fun Level?.transformAabbToWorldObb(
    aabb: AABBdc,
    centerOut: Vector3d,
    axisXOut: Vector3d,
    axisYOut: Vector3d,
    axisZOut: Vector3d,
    halfExtentsOut: Vector3d,
) {
    val cx = (aabb.minX() + aabb.maxX()) * 0.5
    val cy = (aabb.minY() + aabb.maxY()) * 0.5
    val cz = (aabb.minZ() + aabb.maxZ()) * 0.5
    val hx = (aabb.maxX() - aabb.minX()) * 0.5
    val hy = (aabb.maxY() - aabb.minY()) * 0.5
    val hz = (aabb.maxZ() - aabb.minZ()) * 0.5

    val ship1 = getShipManagingPos(aabb.minX(), aabb.minY(), aabb.minZ())
    val ship2 = getShipManagingPos(aabb.maxX(), aabb.maxY(), aabb.maxZ())
    val ship = if (ship1 != null && ship2 != null && ship1.id == ship2.id) ship1 else null

    if (ship == null) {
        centerOut.set(cx, cy, cz)
        axisXOut.set(1.0, 0.0, 0.0)
        axisYOut.set(0.0, 1.0, 0.0)
        axisZOut.set(0.0, 0.0, 1.0)
        halfExtentsOut.set(hx, hy, hz)
        return
    }

    val m = ship.shipToWorld
    m.transformPosition(centerOut.set(cx, cy, cz))
    m.transformDirection(axisXOut.set(hx, 0.0, 0.0))
    m.transformDirection(axisYOut.set(0.0, hy, 0.0))
    m.transformDirection(axisZOut.set(0.0, 0.0, hz))
    val lx = axisXOut.length()
    val ly = axisYOut.length()
    val lz = axisZOut.length()
    halfExtentsOut.set(lx, ly, lz)
    if (lx > 0.0) axisXOut.div(lx)
    if (ly > 0.0) axisYOut.div(ly)
    if (lz > 0.0) axisZOut.div(lz)
}

/**
 * Execute [runnable] immediately iff the thread invoking this is the same as the game thread.
 * Otherwise, schedule [runnable] to run on the next tick.
 */
fun Level.executeOrSchedule(runnable: Runnable) {
    val blockableEventLoop: BlockableEventLoop<Runnable> = if (!this.isClientSide) {
        this.server!! as BlockableEventLoop<Runnable>
    } else {
        Minecraft.getInstance()
    }
    if (blockableEventLoop.isSameThread) {
        // For some reason MinecraftServer wants to schedule even when it's the same thread, so we need to add our own
        // logic
        runnable.run()
    } else {
        blockableEventLoop.execute(runnable)
    }
}

fun getShipMountedToData(passenger: Entity, partialTicks: Float? = null): ShipMountedToData? {
    // Sleeping in a ship-mounted bed counts as a mount relationship — render/cull/packet
    // paths then treat it like a vehicle ride. Offset matches vanilla setPosToBed.
    if (passenger is LivingEntity && passenger.isSleeping) {
        val sleepingPos = passenger.sleepingPos.orElse(null)
        if (sleepingPos != null) {
            val ship = passenger.level().getLoadedShipManagingPos(sleepingPos)
            if (ship != null) {
                val mountedPosInShip: Vector3dc = Vector3d(
                    sleepingPos.x + 0.5,
                    sleepingPos.y + 0.6875,
                    sleepingPos.z + 0.5
                )
                return ShipMountedToData(ship, mountedPosInShip)
            }
        }
    }
    val vehicle = passenger.vehicle ?: return null
    if (vehicle is ShipMountedToDataProvider) {
        return vehicle.provideShipMountedToData(passenger, partialTicks)
    }
    val shipObjectEntityMountedTo =
        passenger.level().getLoadedShipManagingPos(vehicle.position().toJOML()) ?: return null
    val mountedPosInShip: Vector3dc = vehicle.getPosition(partialTicks ?: 0.0f)
        .add(0.0, vehicle.passengersRidingOffset + passenger.myRidingOffset, 0.0).toJOML()

    return ShipMountedToData(shipObjectEntityMountedTo, mountedPosInShip)
}

fun getShipMountedTo(entity: Entity): LoadedShip? {
    return getShipMountedToData(entity)?.shipMountedTo
}

fun Level.isPositionSealed(pos: BlockPos): Boolean {
    val result = this.shipObjectWorld.isIsolatedAir(pos.x, pos.y, pos.z, this.dimensionId)
    return result == ConnectionStatus.DISCONNECTED || result == ConnectionStatus.UNKNOWN
}

fun Level.isPositionMaybeSealed(pos: BlockPos): Boolean {
    val result = this.shipObjectWorld.isIsolatedAir(pos.x, pos.y, pos.z, this.dimensionId)
    return result == ConnectionStatus.DISCONNECTED || result == ConnectionStatus.UNKNOWN
}

/**
 * Applies the ship velocity, inluding angular velocity, to the entity.
 * Useful for cases like launching something on a ship.
 */
fun Entity?.applyShipVelocity(ship: Ship?) {
    if (this == null || ship == null) return
    val relPos = this.position().toJOML().sub(ship.transform.positionInWorld)
    val shipSpeed = Vector3d(ship.velocity)
        .add(ship.angularVelocity.cross(relPos, Vector3d()))
        .mul(0.05)
    this.push(shipSpeed.x, shipSpeed.y, shipSpeed.z)
}

/**
 * Is the [BlockState] in the `assemble_blacklist`
 * ([ValkyrienSkiesMod.ASSEMBLE_BLACKLIST]) block tag
 */
@Suppress("unused")
fun BlockState?.inAssemblyBlacklist(): Boolean {
    return this?.`is`(ASSEMBLE_BLACKLIST) ?: false
}

/**
 * Calls the consumer [f] for every (integer) position in the AABBic.
 *
 * This function is expensive, don't call it too often.
 */
fun AABBic.forEach(f: (Int, Int, Int) -> Unit) {
    for (x in this.minX()..this.maxX()) {
        for (y in this.minY()..this.maxY()) {
            for (z in this.minZ()..this.maxZ()) {
                f.invoke(x, y, z)
            }
        }
    }
}
