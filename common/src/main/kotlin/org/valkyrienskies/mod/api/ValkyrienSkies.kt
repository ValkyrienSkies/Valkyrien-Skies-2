@file:JvmName("ValkyrienSkies")
package org.valkyrienskies.mod.api

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
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
import org.joml.primitives.AABBic
import org.slf4j.LoggerFactory
import org.valkyrienskies.core.api.bodies.ClientVsBody
import org.valkyrienskies.core.api.bodies.LoadedServerVsBody
import org.valkyrienskies.core.api.bodies.QueryableVsBodyData
import org.valkyrienskies.core.api.bodies.ServerVsBody
import org.valkyrienskies.core.api.bodies.VsBody
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.util.functions.DoubleTernaryConsumer
import org.valkyrienskies.core.api.util.functions.IntTernaryConsumer
import org.valkyrienskies.core.api.world.LevelYRange
import org.valkyrienskies.core.api.world.connectivity.ConnectionStatus
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.collision.VsiEntityPolygonCollider
import org.valkyrienskies.core.internal.ships.VsiQueryableShipData
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.VsiShipWorld
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.CompatUtil
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.VS2ChunkAllocator
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.ASSEMBLE_BLACKLIST
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.entity.ShipMountedToData
import org.valkyrienskies.mod.common.entity.ShipMountedToDataProvider
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources
import org.valkyrienskies.mod.common.util.DimensionIdProvider
import org.valkyrienskies.mod.common.util.EntityDragger.serversidePosition
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.util.ShipShadows
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck
import java.util.function.Consumer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.floor
import kotlin.math.sqrt

// one api to rule them all
// this file is gigantic lmao

@get:JvmName("vsCore")
val vsCore: VsiCore get() = ValkyrienSkiesMod.vsCore
val entityPolygonCollider: VsiEntityPolygonCollider get() = vsCore.entityPolygonCollider

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

fun ResourceKey<Level>.toDimensionId() =
    "${registry()}:${location()}"

fun DimensionId.toResourceKey(): ResourceKey<Level>? {
    try {
        val (registryNamespace, registryName, namespace, name) = split(":")
        return ResourceKey.create(ResourceLocation(registryNamespace, registryName), ResourceLocation(namespace, name))
    } catch (e: IndexOutOfBoundsException) { // not a valid format
        return null
    }
}

/**
 * Is the [BlockState] in the `assemble_blacklist`
 * ([ValkyrienSkiesMod.ASSEMBLE_BLACKLIST]) block tag
 */
fun BlockState?.inAssemblyBlacklist(): Boolean {
    return this?.`is`(ASSEMBLE_BLACKLIST) ?: false
}

fun <T : Ship> QueryableShipData<T>.dimension(level: Level?) =
    getByDimension(level?.dimensionId)

fun <T : VsBody> QueryableVsBodyData<T>.dimension(level: Level?) =
    getByDimension(level?.dimensionId)

@GameTickOnly
fun BlockAndTintGetter.shipAwareSkyBrightness(pos: BlockPos, original: Int): Int {
    if (this !is ServerLevel) return original
    val ship = getShipManagingPos(pos) ?: return ShipShadows.skyBrightness(this, pos, original)

    val rendered = ship.toWorldCoordinates(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)

    val worldSky = getBrightness(LightLayer.SKY, BlockPos.containing(rendered.x, rendered.y, rendered.z))
    return ShipShadows.skyBrightness(this, pos, original.coerceAtMost(worldSky))
}

@GameTickOnly
fun BlockAndTintGetter.shipAwareBlockBrightness(pos: BlockPos, original: Int): Int {
    if (this !is ServerLevel) return original
    val ship = getShipManagingPos(pos) ?: return original

    val world = ship.toWorldCoordinates(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
    return original.coerceAtLeast(getBrightness(LightLayer.BLOCK, BlockPos.containing(world.x, world.y, world.z)))
}

@GameTickOnly
fun BlockAndTintGetter.shipAwareCombinedBrightness(pos: BlockPos, skyDarken: Int, original: Int): Int {
    if (this !is ServerLevel) return original
    val ship = getShipManagingPos(pos)

    val shipSky = getBrightness(LightLayer.SKY, pos)
    val rendered = ship?.toWorldCoordinates(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) ?: Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
    val worldSky = getBrightness(LightLayer.SKY, BlockPos.containing(rendered.x, rendered.y, rendered.z))

    val shadowSky = ShipShadows.skyBrightness(this, pos, shipSky.coerceAtMost(worldSky))
    val adjustedSky = 0.coerceAtLeast(shadowSky - skyDarken)
    val shipBlock = getBrightness(LightLayer.BLOCK, pos)
    val worldBlock = getBrightness(LightLayer.BLOCK, BlockPos.containing(rendered.x, rendered.y, rendered.z))
    return adjustedSky.coerceAtLeast(shipBlock.coerceAtLeast(worldBlock))
}
// endregion

// region Entity extensions
/**
 * Convenience function for
 * `entity.level().getShipManagingBlock(entity.position())`.
 *
 * @see getShipManagingBlock
 */
@GameTickOnly
fun Entity?.getShipManagingEntity(): Ship? =
    this?.level()?.getShipManagingBlock(position())

@GameTickOnly
fun Entity?.getShipManaging(): Ship? = this?.let { this.level().getShipManagingPos(this.position()) }

@GameTickOnly
fun Entity?.getEnclosingShip(): Ship? {
    if (this !is IEntityDraggingInformationProvider) return null
    val info = this.draggingInformation
    if (!info.isEntityBeingDraggedByAShip()) return null
    val shipId = info.lastShipStoodOn ?: return null
    return level().shipWorld?.loadedShips?.getById(shipId)
}

/**
 * Like [Entity.distanceToSqr] except the destination is transformed into world coordinates if it is a ship
 */
@GameTickOnly
fun Entity.shipAwareDistanceToSqr(x: Double, y: Double, z: Double): Double {
    val pos = if (getShipMountedTo() != null) getShipMountedToData()!!.mountPosInShip.toMinecraft() else this.serversidePosition()
    return level().distanceSquared(x, y, z, pos.x, pos.y, pos.z)
}


data class ShipBlock(@JvmField val ship: Ship, @JvmField val shipLocalBlockPos: BlockPos)

@GameTickOnly
fun Entity?.getShipBlockStoodOn(probeDepth: Double): ShipBlock? {
    this ?: return null
    val level = level()
    val foot = Vector3d(this.x, this.boundingBox.minY - probeDepth, this.z)
    val probe = AABBd(foot.x - 0.5, foot.y - 0.5, foot.z - 0.5, foot.x + 0.5, foot.y + 0.5, foot.z + 0.5)
    for (ship in level.getShipsIntersecting(probe)) {
        val local = ship.toWorldCoordinates(foot, Vector3d())
        val centerPos = BlockPos.containing(local.x, local.y, local.z)
        if (!level.getBlockState(centerPos).isAir) return ShipBlock(ship, centerPos)

        val belowLocal = ship.toWorldCoordinates(Vector3d(foot.x, foot.y - 1.0, foot.z))
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
            ship.toWorldCoordinates(cornerScratch)
            val cornerPos = BlockPos.containing(cornerScratch.x, cornerScratch.y, cornerScratch.z)
            val cornerLong = cornerPos.asLong()
            if (cornerLong == centerLong || cornerLong == lastSeen) continue
            lastSeen = cornerLong
            if (!level.getBlockState(cornerPos).isAir) return ShipBlock(ship, cornerPos)
        }
    }
    return null
}

@GameTickOnly
fun Entity?.getShipStoodOn(): Ship? = getShipBlockStoodOn(0.2)?.ship


/**
 * Applies the ship velocity, including angular velocity, to the entity.
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

@GameTickOnly
fun Entity.getShipMountedToData(partialTicks: Float? = null): ShipMountedToData? {
    if (this is LivingEntity && this.isSleeping) {
        val sleepingPos = this.sleepingPos.orElse(null)
        if (sleepingPos != null) {
            val ship = this.level().getLoadedShipManagingPos(sleepingPos)
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

    val vehicle = this.vehicle ?: return null
    if (vehicle is ShipMountedToDataProvider)
        return vehicle.provideShipMountedToData(this, partialTicks)

    val shipObjectEntityMountedTo = this.level().getLoadedShipManagingPos(vehicle.position().toJOML()) ?: return null
    val offset = Vector3d()
    (vehicle as EntityAccessor).callPositionRider(this) { _, x, y, z -> offset.set(x, y, z).sub(vehicle.position().toJOML()) }
    val mountedPosInShip: Vector3dc = vehicle.getPosition(partialTicks ?: 0.0f).toJOML().add(offset)

    return ShipMountedToData(shipObjectEntityMountedTo, mountedPosInShip)
}

@GameTickOnly
fun Entity.getShipMountedTo(): LoadedShip? =
    getShipMountedToData()?.shipMountedTo

@GameTickOnly
fun Entity.shipAwareEntityLightLevelDependentMagicValue(): Float? {
    val level = level()
    if (level !is ServerLevel) return null
    val ship = getEnclosingShip() ?: return null
    val worldPos = BlockPos.containing(x, eyeY, z)
    val shipyard = ship.transform.worldToShip.transformPosition(
        x, eyeY, z, Vector3d()
    )
    val shipyardPos = BlockPos.containing(shipyard.x, shipyard.y, shipyard.z)
    val shipSky = level.getBrightness(LightLayer.SKY, shipyardPos)
    val worldSky = ShipShadows.skyBrightness(level, shipyardPos, level.getBrightness(LightLayer.SKY, worldPos))
    val effectiveSky = 0.coerceAtLeast(shipSky.coerceAtMost(worldSky) - level.skyDarken)
    val shipBlock = level.getBrightness(LightLayer.BLOCK, shipyardPos)
    val worldBlock = level.getBrightness(LightLayer.BLOCK, worldPos)
    val rawBrightness = effectiveSky.coerceAtLeast(shipBlock.coerceAtLeast(worldBlock))
    val brightness = rawBrightness / 15.0f
    val scaled = brightness / (4.0f - 3.0f * brightness)
    val ambient = level.dimensionType().ambientLight()
    return scaled + ambient * (1.0f - scaled)
}

@GameTickOnly
fun Entity.shipMountedSpawnSeedPos(): BlockPos {
    val ship = getShipStoodOn() ?: return blockPosition()
    val local = ship.transform.worldToShip.transformPosition(x, y, z, Vector3d())
    return BlockPos.containing(local.x, local.y, local.z)
}
// endregion

// region Level extensions
/**
 * The String/[DimensionId] used within vs-core for representing this [Level].
 */
val Level.dimensionId: DimensionId
    get() = (this as DimensionIdProvider).dimensionId

/**
 * Nullable shipWorld.
 * @see shipWorld
 */
@GameTickOnly
val Level?.shipWorldNullable: VsiShipWorld?
    get() = when {
        this == null -> null
        this is ServerLevel -> this.server.shipWorld
        this is ClientLevel && this.isClientSide -> this.shipWorld
        else -> null
    }

/**
 * the [VsiShipWorld] for this level, or throws an error if there is no shipWorld for this level.
 * Note that although this takes a level, the given ship world is not specific to this level. If you need ships or bodies filtered
 * by this specific level, see [allShips], [loadedShips], [unloadedShips], [allBodies], [unloadedBodies]
 */
@GameTickOnly
val Level?.shipWorld: VsiShipWorld
    get() = shipWorldNullable ?: error("No shipWorld for this Level!")

/**
 * Returns a [QueryableShipData] which you can use to query all ships in this dimension,
 * loaded and unloaded. This can be slow if the ship is not loaded, and we need to fetch it from disk.
 * If you need all ships regardless of dimension, use [allShipsGlobal]
 */
@GameTickOnly val Level?.allShips get() = this?.shipWorld?.allShips?.dimension(this)
/**
 * Returns a [QueryableShipData] which you can use to query unloaded ships in this dimension.
 * If you need unloaded ships regardless of dimension, use [unloadedShipsGlobal]
 */
@GameTickOnly val Level?.unloadedShips get() = this?.shipWorld?.unloadedShips?.dimension(this)
/**
 * Returns a [QueryableShipData] which you can use to query loaded ships in this dimension.
 * If you need loaded ships regardless of dimension, use [loadedShipsGlobal]
 */
@GameTickOnly val Level?.loadedShips get() = this?.shipWorld?.loadedShips?.dimension(this)

/**
 * Returns a [QueryableVsBodyData] which you can use to query all bodies in this dimension.
 * If you need all bodies regardless of dimension, use [allBodiesGlobal]
 */
@GameTickOnly val Level?.allBodies get() = this?.shipWorld?.allBodies?.dimension(this)
/**
 * Returns a [QueryableVsBodyData] which you can use to query unloaded bodies in this dimension.
 * If you need unloaded bodies regardless of dimension, use [unloadedBodiesGlobal]
 */
@GameTickOnly val Level?.unloadedBodies get() = this?.shipWorld?.unloadedBodies?.dimension(this)

/**
 * Returns a [QueryableShipData] which you can use to query all ships, regardless of dimension.
 * If you need all ships filtered by dimension, use [allShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val Level?.allShipsGlobal get() = this?.shipWorld?.allShips
/**
 * Returns a [QueryableShipData] which you can use to query unloaded ships, regardless of dimension.
 * If you need unloaded ships filtered by dimension, use [unloadedShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val Level?.unloadedShipsGlobal get() = this?.shipWorld?.unloadedShips
/**
 * Returns a [QueryableShipData] which you can use to query loaded ships, regardless of dimension.
 * If you need loaded ships filtered by dimension, use [loadedShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val Level?.loadedShipsGlobal get() = this?.shipWorld?.loadedShips

/**
 * Returns a [QueryableVsBodyData] which you can use to query all bodies, regardless of dimension.
 * If you need all bodies filtered by dimension, use [allBodies]
 *
 * @see QueryableVsBodyData.getByDimension
 */
@GameTickOnly val Level?.allBodiesGlobal get() = this?.shipWorld?.allBodies
/**
 * Returns a [QueryableVsBodyData] which you can use to query unloaded bodies, regardless of dimension.
 * If you need unloaded bodies filtered by dimension, use [unloadedBodies]
 *
 * @see QueryableVsBodyData.getByDimension
 */
@GameTickOnly val Level?.unloadedBodiesGlobal get() = this?.shipWorld?.unloadedBodies

val Level.yRange get() = LevelYRange(minBuildHeight, maxBuildHeight - 1)


private val levelResourceKeyMap: MutableMap<DimensionId, ResourceKey<Level>> = HashMap()

/**
 * Get the resource key corresponding to the given [dimensionId]. If the given [dimensionId]
 * is not a valid format, returns `null`
 */
fun getResourceKey(dimensionId: DimensionId): ResourceKey<Level>? {
    val cached = levelResourceKeyMap[dimensionId]
    if (cached == null) {
        val toReturn = dimensionId.toResourceKey() ?: return null
        levelResourceKeyMap[dimensionId] = toReturn
        return toReturn
    }
    return cached
}

/**
 * Execute [runnable] immediately if the thread invoking this is the same as the game thread.
 * Otherwise, schedule [runnable] to run on the next tick.
 */
fun Level.executeOrSchedule(runnable: Runnable) {
    val blockableEventLoop: BlockableEventLoop<Runnable> = if (!this.isClientSide) {
        this.server!! as BlockableEventLoop<Runnable>
    } else {
        Minecraft.getInstance()
    }

    if (blockableEventLoop.isSameThread) runnable.run()
    else blockableEventLoop.execute(runnable)
}

val Level.gameToPhysicsAdapter get() = ValkyrienSkiesMod.getOrCreateGTPA(dimensionId)

@GameTickOnly
fun Level?.getShipById(id: ShipId): Ship? =
    allShips?.getById(id)

@GameTickOnly
fun Level?.getLoadedShipById(id: ShipId): LoadedShip? =
    loadedShips?.getById(id)

@GameTickOnly
fun Level?.forAllShips(cb: Consumer<Ship>) =
    allShips?.forEach(cb)

@GameTickOnly
fun Level?.forLoadedShips(cb: Consumer<LoadedShip>) =
    loadedShips?.forEach(cb)

@GameTickOnly
fun Level?.forAllBodies(cb: Consumer<VsBody>) =
    allBodies?.forEach(cb)

@GameTickOnly
fun Level?.isBlockInShipyard(blockX: Int, blockZ: Int): Boolean =
    isChunkInShipyard(blockX shr 4, blockZ shr 4)

@GameTickOnly
fun Level?.isBlockInShipyard(pos: Vec3i): Boolean =
    isBlockInShipyard(pos.x, pos.z)

@GameTickOnly
fun Level?.isBlockInShipyard(pos: Vec3): Boolean =
    isBlockInShipyard(pos.x.toInt(), pos.y.toInt())

@GameTickOnly
fun Level?.isChunkInShipyard(chunkX: Int, chunkZ: Int): Boolean =
    this != null && this.shipWorld.isChunkInShipyard(chunkX, chunkZ, dimensionId)

@GameTickOnly
fun Level?.getShipByIdGlobal(id: ShipId): Ship? =
    allShipsGlobal?.getById(id)

@GameTickOnly
fun Level?.getLoadedShipByIdGlobal(id: ShipId): LoadedShip? =
    loadedShipsGlobal?.getById(id)

@GameTickOnly
fun Level?.forAllShipsGlobal(cb: Consumer<Ship>) =
    allShipsGlobal?.forEach(cb)

@GameTickOnly
fun Level?.forLoadedShipsGlobal(cb: Consumer<LoadedShip>) =
    loadedShipsGlobal?.forEach(cb)

@GameTickOnly
fun Level?.forAllBodiesGlobal(cb: Consumer<VsBody>) =
    allBodiesGlobal?.forEach(cb)

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
fun Level.shipAwareCanSeeSky(pos: BlockPos): Boolean {
    val ship = getShipManagingPos(pos)
    val worldPos = if (ship != null) {
        val world = ship.transform.shipToWorld.transformPosition(
            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, Vector3d()
        )
        BlockPos.containing(world.x, world.y, world.z)
    } else pos

    if (!canSeeSky(worldPos)) return false

    val heightInclShips = CompatUtil.getWorldHeightmapPosIncludingShips(
        this, Heightmap.Types.MOTION_BLOCKING, worldPos
    )

    return worldPos.y + 1 >= heightInclShips.y
}

@GameTickOnly
fun LevelAccessor.shipProjectedWorldY(pos: BlockPos, original: Int): Int {
    if (this !is ServerLevel) return original
    val ship = getShipManagingPos(pos) ?: return original
    val rendered = ship.toWorldCoordinates(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
    return floor(rendered.y).toInt()
}

fun LevelChunkSection.toDenseVoxelUpdate(chunkPos: Vector3ic, level: Level? = null): VsiTerrainUpdate {
    val sources = (level as? ServerLevel)?.let(FractionalFluidSources::forLevel)?.takeIf {
        it.size != 0 && it.hasChunk(level.dimensionId, chunkPos.x(), chunkPos.y(), chunkPos.z())
    }

    if (sources == null) return toDenseVoxelUpdateWithoutSourceAmounts(chunkPos)
    val update = vsCore.newDenseTerrainUpdateBuilder(chunkPos.x(), chunkPos.y(), chunkPos.z())
    val info = BlockStateInfo.cache
    for (x in 0..15) {
        for (y in 0..15) {
            for (z in 0..15) {
                val blockState = getBlockState(x, y, z)
                val defaultBlockType = info.get(blockState)?.second ?: vsCore.blockTypes.air
                val fluid = FractionalFluidSources.fluidName(blockState)
                if (fluid == null) update.addBlock(x, y, z, defaultBlockType)
                else update.addBlock(x, y, z, defaultBlockType, sources.sourceFillAmount(level.dimensionId,
                    (chunkPos.x() shl 4) + x, (chunkPos.y() shl 4) + y, (chunkPos.z() shl 4) + z, fluid))
            }
        }
    }
    return update.build()
}

@GameTickOnly // idk why either of these exist and they seem to be the exact same but idk flooding is black magic
fun Level.isPositionSealed(pos: BlockPos): Boolean {
    val result = shipWorld.isIsolatedAir(pos.x, pos.y, pos.z, dimensionId)
    return result == ConnectionStatus.DISCONNECTED || result == ConnectionStatus.UNKNOWN
}

@GameTickOnly
fun Level.isPositionMaybeSealed(pos: BlockPos): Boolean {
    val result = shipWorld.isIsolatedAir(pos.x, pos.y, pos.z, dimensionId)
    return result == ConnectionStatus.DISCONNECTED || result == ConnectionStatus.UNKNOWN
}

private fun LevelChunkSection.toDenseVoxelUpdateWithoutSourceAmounts(chunkPos: Vector3ic): VsiTerrainUpdate {
    val update = vsCore.newDenseTerrainUpdateBuilder(chunkPos.x(), chunkPos.y(), chunkPos.z())
    val info = BlockStateInfo.cache
    for (x in 0..15) {
        for (y in 0..15) {
            for (z in 0..15) {
                val blockState = getBlockState(x, y, z)
                update.addBlock(x, y, z, info.get(blockState)?.second ?: vsCore.blockTypes.air)
            }
        }
    }
    return update.build()
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


@GameTickOnly
fun Level?.getLoadedShipManagingPos(chunkX: Int, chunkZ: Int) =
    getLoadedShipManagingPosImpl(this, chunkX, chunkZ)

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

@GameTickOnly
fun Level?.toWorldCoordinates(blockPos: Vec3i): Vector3d =
    getShipManagingPos(blockPos)?.toWorldCoordinates(blockPos) ?: blockPos.toJOMLd()

@GameTickOnly
fun Level?.toWorldCoordinates(pos: Vec3): Vector3d =
    getShipManagingPos(pos)?.toWorldCoordinates(pos) ?: pos.toJOML()

@GameTickOnly
fun Level?.toWorldCoordinates(pos: Vector3d, dest: Vector3d): Vector3d =
    getShipManagingPos(pos)?.toWorldCoordinates(pos, dest) ?: dest.set(pos)

@GameTickOnly
fun Level?.toWorldCoordinates(pos: Vector3d): Vector3d =
    getShipManagingPos(pos)?.toWorldCoordinates(pos) ?: pos

@GameTickOnly
@JvmOverloads
fun Level?.toWorldCoordinates(x: Double, y: Double, z: Double, dest: Vector3d = Vector3d()): Vector3d =
    getShipManagingPos(x, z)?.toWorldCoordinates(x, y, z, dest) ?: dest.set(x, y, z)

@GameTickOnly
fun Level?.getWorldCoordinates(blockPos: Vec3i, pos: Vector3d) =
    getShipManagingPos(blockPos)?.toWorldCoordinates(pos) ?: pos


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
 * Get all ships intersecting an AABB in world-space, then call [cb] with the AABB itself,
 * followed by the AABB in the ship-space of the intersecting ships.
 */
@GameTickOnly
fun Level.transformFromWorldToNearbyShipsAndWorld(aabb: AABB, cb: Consumer<AABB>) =
    transformFromWorldToNearbyShipsAndWorld(aabb.toJOML()) { cb.accept(it.toMinecraft()) }

/**
 * Get all ships intersecting an AABB in world-space, then call [cb] with the AABB itself,
 * followed by the AABB in the ship-space of the intersecting ships.
 */
@GameTickOnly
fun Level.transformFromWorldToNearbyShipsAndWorld(aabb: AABBd, cb: Consumer<AABBd>) {
    val tmpAABB = AABBd()
    cb.accept(aabb)
    getShipsIntersecting(aabb).forEach { ship ->
        tmpAABB.set(aabb).transform(ship.worldToShip)
        if (EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, tmpAABB)) {
            cb.accept(tmpAABB)
        }
    }
}

/**
 * Same as [transformFromWorldToNearbyShipsAndWorld], but without calling [cb] with the original aabb
 */
@GameTickOnly
fun Level.transformFromWorldToNearbyShips(aabb: AABB, cb: Consumer<AABB>) =
    transformFromWorldToNearbyShips(aabb.toJOML()) { cb.accept(it.toMinecraft()) }


/**
 * Same as [transformFromWorldToNearbyShipsAndWorld], but without calling [cb] with the original aabb
 */
@GameTickOnly
fun Level.transformFromWorldToNearbyShips(aabb: AABBd, cb: Consumer<AABBd>) {
    val tmpAABB = AABBd()
    getShipsIntersecting(aabb).forEach { ship ->
        tmpAABB.set(aabb).transform(ship.worldToShip)
        if (EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, tmpAABB)) {
            cb.accept(tmpAABB)
        }
    }
}

/**
 * Given a position, transforms that position to world if it isn't already and makes
 * an `aabb` at that world position with a radius of [aabbRadius], then calls [cb]
 * for the world position as well as that position in the space of all ships intersecting the `aabb`.
 */
@GameTickOnly
fun Level.transformToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabbRadius: Double, cb: DoubleTernaryConsumer) {
    val currentShip = getShipManagingPos(x, z)
    val pos = Vector3d(x, y, z)

    currentShip?.toWorldCoordinates(pos) // transform to world if not already world pos

    cb.accept(pos.x(), pos.y(), pos.z()) // call for world pos

    val aabb = AABBd(pos, pos).expand(aabbRadius) // aabb in world
    val temp0 = Vector3d()

    for (ship in getShipsIntersecting(aabb)) {
        val posInShip = ship.worldToShip.transformPosition(pos, temp0)
        cb.accept(posInShip.x(), posInShip.y(), posInShip.z())
    }
}


@GameTickOnly
fun Level?.transformAABBToWorld(aabb: AABB) = transformAABBToWorld(aabb.toJOML())

@GameTickOnly
fun Level?.transformAABBToWorld(aabb: AABBd) = transformAABBToWorld(aabb, aabb)

@GameTickOnly
fun Level?.transformAABBToWorld(aabb: AABBdc, dest: AABBd): AABBd {
    val ship0 = getShipManagingPos(aabb.minX(), aabb.minZ()) ?: return dest.set(aabb)
    val ship1 = getShipManagingPos(aabb.maxX(), aabb.maxZ()) ?: return dest.set(aabb)

    return if (ship0.id == ship1.id) aabb.transform(ship1.shipToWorld, dest) else dest.set(aabb)
}

/**
 * Like [org.valkyrienskies.mod.api.transformAABBToWorld], but instead gives
 * the actual OBB (sets the dest vectors)
 *
 * @param aabb the AABB to transform
 */
@GameTickOnly
fun Level?.transformAABBToWorldOBB(
    aabb: AABBdc,
    destCenter: Vector3d,
    destAxisX: Vector3d,
    destAxisY: Vector3d,
    destAxisZ: Vector3d,
    destHalfExtents: Vector3d,
) {
    val cx = (aabb.minX() + aabb.maxX()) * 0.5
    val cy = (aabb.minY() + aabb.maxY()) * 0.5
    val cz = (aabb.minZ() + aabb.maxZ()) * 0.5
    val hx = (aabb.maxX() - aabb.minX()) * 0.5
    val hy = (aabb.maxY() - aabb.minY()) * 0.5
    val hz = (aabb.maxZ() - aabb.minZ()) * 0.5

    val ship1 = getShipManagingPos(aabb.minX(), aabb.minZ())
    val ship2 = getShipManagingPos(aabb.maxX(), aabb.maxZ())
    val ship = if (ship1 != null && ship2 != null && ship1.id == ship2.id) ship1 else null

    if (ship == null) {
        destCenter.set(cx, cy, cz)
        destAxisX.set(1.0, 0.0, 0.0)
        destAxisY.set(0.0, 1.0, 0.0)
        destAxisZ.set(0.0, 0.0, 1.0)
        destHalfExtents.set(hx, hy, hz)
        return
    }

    val m = ship.shipToWorld
    m.transformPosition(destCenter.set(cx, cy, cz))
    m.transformDirection(destAxisX.set(hx, 0.0, 0.0))
    m.transformDirection(destAxisY.set(0.0, hy, 0.0))
    m.transformDirection(destAxisZ.set(0.0, 0.0, hz))
    val lx = destAxisX.length()
    val ly = destAxisY.length()
    val lz = destAxisZ.length()
    destHalfExtents.set(lx, ly, lz)
    if (lx > 0.0) destAxisX.div(lx)
    if (ly > 0.0) destAxisY.div(ly)
    if (lz > 0.0) destAxisZ.div(lz)
}

/**
 * Returns all the ships whose AABBs contain x/y/z
 */
@GameTickOnly
fun Level?.getShipsIntersecting(x: Double, y: Double, z: Double): Iterable<Ship> =
    getShipsIntersecting(AABBd(x, y, z, x, y, z))

@GameTickOnly
fun Level?.getShipsIntersecting(aabb: AABB): Iterable<Ship> =
    getShipsIntersecting(aabb.toJOML())

/**
 * Returns all the ships whose AABBs intersect [aabb]
 */
@GameTickOnly
fun Level?.getShipsIntersecting(aabb: AABBdc?): Iterable<Ship> =
    if (aabb == null) emptyList() else this?.allShips?.getIntersecting(aabb, dimensionId) ?: emptyList()

/**
 * Returns all the ships whose AABBs contain x/y/z
 */
@GameTickOnly
fun Level?.getLoadedShipsIntersecting(x: Double, y: Double, z: Double): Iterable<LoadedShip> =
    getLoadedShipsIntersecting(AABBd(x, y, z, x, y, z))

@GameTickOnly
fun Level?.getLoadedShipsIntersecting(aabb: AABB): Iterable<LoadedShip> =
    getLoadedShipsIntersecting(aabb.toJOML())

/**
 * Returns all the ships whose AABBs intersect [aabb]
 */
@GameTickOnly
fun Level?.getLoadedShipsIntersecting(aabb: AABBdc?): Iterable<LoadedShip> =
    if (aabb == null) emptyList() else this?.loadedShips?.getIntersecting(aabb, dimensionId) ?: emptyList()


/**
 * Returns all the bodies whose AABBs contain x/y/z
 */
@GameTickOnly
fun Level?.getBodiesIntersecting(x: Double, y: Double, z: Double): Iterable<VsBody> =
    getBodiesIntersecting(AABBd(x, y, z, x, y, z))

@GameTickOnly
fun Level?.getBodiesIntersecting(aabb: AABB): Iterable<VsBody> =
    getBodiesIntersecting(aabb.toJOML())

/**
 * Returns all the bodies whose AABBs intersect [aabb]
 */
@GameTickOnly
fun Level?.getBodiesIntersecting(aabb: AABBdc?): Iterable<VsBody> =
    if (aabb == null) emptyList() else this?.allBodies?.getIntersecting(aabb, dimensionId) ?: emptyList()

@GameTickOnly
fun Level?.distance(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double =
    sqrt(distanceSquared(x1, y1, z1, x2, y2, z2))

@GameTickOnly
fun Level?.distance(v1: Position, v2: Position): Double =
    sqrt(distanceSquared(v1, v2))

@GameTickOnly
fun Level?.distanceSquared(v1: Vec3, v2: Vec3, originalDistance: Operation<Double>?): Double {
    originalDistance ?: return distanceSquared(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z)

    val inWorldV1 = toWorldCoordinates(v1)
    val inWorldV2 = toWorldCoordinates(v2)

    return originalDistance.call(inWorldV1, inWorldV2)
}

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


// endregion

// region ClientLevel / Minecraft extensions
@GameTickOnly
val ClientLevel?.shipWorldNullable: VsiClientShipWorld?
    get() = (this as IShipObjectWorldClientProvider?)?.shipObjectWorld

@GameTickOnly
val Minecraft?.shipWorldNullable: VsiClientShipWorld?
    get() = (this as IShipObjectWorldClientProvider?)?.shipObjectWorld

@GameTickOnly
val ClientLevel?.shipWorld: VsiClientShipWorld
    get() = shipWorldNullable ?: error("no shipWorld for this ClientLevel!")

@GameTickOnly
val Minecraft?.shipWorld: VsiClientShipWorld
    get() = shipWorldNullable ?: error("no shipWorld for this Minecraft!")

@GameTickOnly val ClientLevel?.allShips get() = this?.shipWorld?.allShips?.dimension(this)
@GameTickOnly val ClientLevel?.loadedShips get() = this.shipWorld.loadedShips.dimension(this)
@GameTickOnly val ClientLevel?.unloadedShips get() = this.shipWorld.unloadedShips.dimension(this)

@GameTickOnly val ClientLevel?.allBodies get() = this.shipWorld?.allBodies?.dimension(this)
@GameTickOnly val ClientLevel?.unloadedBodies get() = this.shipWorld?.unloadedBodies?.dimension(this)

/**
 * Returns a [QueryableShipData] which you can use to query all ships, regardless of dimension.
 * If you need all ships filtered by dimension, use [allShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val ClientLevel?.allShipsGlobal get() = this.shipWorld?.allShips
/**
 * Returns a [QueryableShipData] which you can use to query unloaded ships, regardless of dimension.
 * If you need unloaded ships filtered by dimension, use [unloadedShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val ClientLevel?.unloadedShipsGlobal get() = this.shipWorld?.unloadedShips
/**
 * Returns a [QueryableShipData] which you can use to query loaded ships, regardless of dimension.
 * If you need loaded ships filtered by dimension, use [loadedShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val ClientLevel?.loadedShipsGlobal get() = this.shipWorld?.loadedShips

/**
 * Returns a [QueryableVsBodyData] which you can use to query all bodies, regardless of dimension.
 * If you need all bodies filtered by dimension, use [allBodies]
 *
 * @see QueryableVsBodyData.getByDimension
 */
@GameTickOnly val ClientLevel?.allBodiesGlobal get() = this.shipWorld?.allBodies
/**
 * Returns a [QueryableVsBodyData] which you can use to query unloaded bodies, regardless of dimension.
 * If you need unloaded bodies filtered by dimension, use [unloadedBodies]
 *
 * @see QueryableVsBodyData.getByDimension
 */
@GameTickOnly val ClientLevel?.unloadedBodiesGlobal get() = this.shipWorld?.unloadedBodies

@GameTickOnly val Minecraft?.allShips get() = this.shipWorld?.allShips
@GameTickOnly val Minecraft?.unloadedShips get() = this.shipWorld?.unloadedShips
@GameTickOnly val Minecraft?.loadedShips get() = this.shipWorld?.loadedShips

@GameTickOnly val Minecraft?.allBodies get() = this.shipWorld?.allBodies
@GameTickOnly val Minecraft?.unloadedBodies get() = this.shipWorld?.unloadedBodies

@GameTickOnly
fun ClientLevel?.getShipById(shipId: ShipId) =
    allShips?.getById(shipId)

@GameTickOnly
fun ClientLevel?.getLoadedShipById(shipId: ShipId) =
    loadedShips?.getById(shipId)

@GameTickOnly
fun ClientLevel?.getShipByIdGlobal(shipId: ShipId) =
    allShipsGlobal?.getById(shipId)

@GameTickOnly
fun ClientLevel?.getLoadedShipByIdGlobal(shipId: ShipId) =
    loadedShipsGlobal?.getById(shipId)

@GameTickOnly
fun Minecraft?.getShipById(shipId: ShipId) =
    allShips?.getById(shipId)

@GameTickOnly
fun Minecraft?.getLoadedShipById(shipId: ShipId) =
    loadedShips?.getById(shipId)

@GameTickOnly
fun ClientLevel?.forAllShips(cb: Consumer<ClientShip>) =
    allShips?.forEach(cb)

@GameTickOnly
fun ClientLevel?.forLoadedShips(cb: Consumer<ClientShip>) =
    loadedShips?.forEach(cb)

@GameTickOnly
fun ClientLevel?.forAllBodies(cb: Consumer<ClientVsBody>) =
    allBodies?.forEach(cb)

@GameTickOnly
fun ClientLevel?.forAllShipsGlobal(cb: Consumer<ClientShip>) =
    allShipsGlobal?.forEach(cb)

@GameTickOnly
fun ClientLevel?.forLoadedShipsGlobal(cb: Consumer<ClientShip>) =
    loadedShipsGlobal?.forEach(cb)

@GameTickOnly
fun ClientLevel?.forAllBodiesGlobal(cb: Consumer<ClientVsBody>) =
    allBodiesGlobal?.forEach(cb)

@GameTickOnly
fun Minecraft?.forAllShips(cb: Consumer<ClientShip>) =
    allShips?.forEach(cb)

@GameTickOnly
fun Minecraft?.forLoadedShips(cb: Consumer<ClientShip>) =
    loadedShips?.forEach(cb)

@GameTickOnly
fun Minecraft?.forAllBodies(cb: Consumer<ClientVsBody>) =
    allBodies?.forEach(cb)

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
    getLoadedShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

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

@GameTickOnly
fun ClientLevel?.toShipRenderCoordinates(shipPos: Vec3, pos: Vec3): Vector3d =
    this?.getLoadedShipManagingPos(shipPos)
        ?.renderTransform
        ?.worldToShip
        ?.transformPosition(pos.toJOML()) ?: pos.toJOML()

@GameTickOnly
fun Minecraft?.getLoadedShipManagingPos(chunkX: Int, chunkZ: Int) =
    this?.level.getLoadedShipManagingPos(chunkX, chunkZ)

@GameTickOnly
fun Minecraft?.getLoadedShipManagingPos(blockPos: Vec3i) =
    this?.level.getLoadedShipManagingPos(blockPos)

@GameTickOnly
fun Minecraft?.getLoadedShipManagingPos(pos: Vector3dc) =
    this?.level.getLoadedShipManagingPos(pos)

@GameTickOnly
fun Minecraft?.getLoadedShipManagingPos(pos: Position) =
    this?.level.getLoadedShipManagingPos(pos)

@GameTickOnly
fun Minecraft?.getLoadedShipManagingPos(posX: Double, posZ: Double) =
    this?.level.getLoadedShipManagingPos(posX, posZ)

@GameTickOnly
fun Minecraft?.getLoadedShipManagingPos(chunkPos: ChunkPos) =
    this?.level.getLoadedShipManagingPos(chunkPos.x, chunkPos.z)

@GameTickOnly
fun Minecraft?.transformRenderAABBToWorld(pos: Position, aabb: AABB) =
    this?.level.transformRenderAABBToWorld(pos, aabb)

@GameTickOnly
fun Minecraft?.toShipRenderCoordinates(shipPos: Vec3, pos: Vec3): Vector3d =
    this?.level.toShipRenderCoordinates(shipPos, pos)
// endregion

// region ServerLevel / MinecraftServer extensions
@GameTickOnly
val MinecraftServer?.shipWorld: VsiServerShipWorld?
    get() = (this as IShipObjectWorldServerProvider?)?.shipObjectWorld

@GameTickOnly
val ServerLevel?.shipWorld: VsiServerShipWorld?
    get() = (this as IShipObjectWorldServerProvider?)?.shipObjectWorld

@GameTickOnly
val ServerLevel?.shipWorldOrException: VsiServerShipWorld
    get() = shipWorld ?: error("no shipWorld for this ServerLevel!")

@GameTickOnly
val MinecraftServer?.shipWorldOrException: VsiServerShipWorld
    get() = shipWorld ?: error("no shipWorld for this MinecraftServer!")

val MinecraftServer?.vsPipeline
    get() = (this as IShipObjectWorldServerProvider?)?.vsPipeline

val ServerLevel?.vsPipeline
    get() = (this as IShipObjectWorldServerProvider?)?.vsPipeline

@GameTickOnly val ServerLevel?.allShips get() = this.shipWorld?.allShips?.dimension(this)
@GameTickOnly val ServerLevel?.loadedShips get() = this.shipWorld?.loadedShips?.dimension(this)
@GameTickOnly val ServerLevel?.unloadedShips get() = this.shipWorld?.unloadedShips?.dimension(this)

@GameTickOnly val ServerLevel?.allBodies get() = this.shipWorld?.allBodies?.dimension(this)
@GameTickOnly val ServerLevel?.loadedBodies get() = this.shipWorld?.loadedBodies?.dimension(this)
@GameTickOnly val ServerLevel?.unloadedBodies get() = this.shipWorld?.unloadedBodies?.dimension(this)

/**
 * Returns a [QueryableShipData] which you can use to query all ships, regardless of dimension.
 * If you need all ships filtered by dimension, use [allShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val ServerLevel?.allShipsGlobal get() = this.shipWorld?.allShips
/**
 * Returns a [QueryableShipData] which you can use to query unloaded ships, regardless of dimension.
 * If you need unloaded ships filtered by dimension, use [unloadedShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val ServerLevel?.unloadedShipsGlobal get() = this.shipWorld?.unloadedShips
/**
 * Returns a [QueryableShipData] which you can use to query loaded ships, regardless of dimension.
 * If you need loaded ships filtered by dimension, use [loadedShips].
 *
 * @see QueryableShipData.getByDimension
 */
@GameTickOnly val ServerLevel?.loadedShipsGlobal get() = this.shipWorld?.loadedShips

/**
 * Returns a [QueryableVsBodyData] which you can use to query all bodies, regardless of dimension.
 * If you need all bodies filtered by dimension, use [allBodies]
 *
 * @see QueryableVsBodyData.getByDimension
 */
@GameTickOnly val ServerLevel?.allBodiesGlobal get() = this.shipWorld?.allBodies
/**
 * Returns a [QueryableVsBodyData] which you can use to query unloaded bodies, regardless of dimension.
 * If you need unloaded bodies filtered by dimension, use [unloadedBodies]
 *
 * @see QueryableVsBodyData.getByDimension
 */
@GameTickOnly val ServerLevel?.unloadedBodiesGlobal get() = this.shipWorld?.unloadedBodies

/**
 * Returns a [QueryableVsBodyData] which you can use to query loaded bodies, regardless of dimension.
 * If you need loaded bodies filtered by dimension, use [loadedBodies]
 */
@GameTickOnly val ServerLevel?.loadedBodiesGlobal get() = this.shipWorld?.loadedBodies

@GameTickOnly val MinecraftServer?.allShips get() = this.shipWorld?.allShips
@GameTickOnly val MinecraftServer?.loadedShips get() = this.shipWorld?.loadedShips
@GameTickOnly val MinecraftServer?.unloadedShips get() = this.shipWorld?.unloadedShips

@GameTickOnly val MinecraftServer?.allBodies get() = this.shipWorld?.allBodies
@GameTickOnly val MinecraftServer?.loadedBodies get() = this.shipWorld?.loadedBodies
@GameTickOnly val MinecraftServer?.unloadedBodies get() = this.shipWorld?.unloadedBodies

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

fun MinecraftServer.getLevel(dimensionId: DimensionId): ServerLevel? =
    getResourceKey(dimensionId)?.let { getLevel(it) }


@GameTickOnly
fun ServerLevel?.getShipById(shipId: ShipId) =
    allShips?.getById(shipId)

@GameTickOnly
fun ServerLevel?.getLoadedShipById(shipId: ShipId) =
    loadedShips?.getById(shipId)


@GameTickOnly
fun ServerLevel?.getShipByIdGlobal(shipId: ShipId) =
    allShipsGlobal?.getById(shipId)

@GameTickOnly
fun ServerLevel?.getLoadedShipByIdGlobal(shipId: ShipId) =
    loadedShipsGlobal?.getById(shipId)

@GameTickOnly
fun MinecraftServer?.getShipByIdGlobal(shipId: ShipId) =
    allShips?.getById(shipId)

@GameTickOnly
fun MinecraftServer?.getLoadedShipByIdGlobal(shipId: ShipId) =
    loadedShips?.getById(shipId)

@GameTickOnly
fun ServerLevel?.forAllShips(cb: Consumer<ServerShip>) =
    allShips?.forEach(cb)

@GameTickOnly
fun ServerLevel?.forLoadedShips(cb: Consumer<LoadedServerShip>) =
    loadedShips?.forEach(cb)

@GameTickOnly
fun ServerLevel?.forAllBodies(cb: Consumer<ServerVsBody>) =
    allBodies?.forEach(cb)

@GameTickOnly
fun ServerLevel?.forLoadedBodies(cb: Consumer<LoadedServerVsBody>) =
    loadedBodies?.forEach(cb)

@GameTickOnly
fun ServerLevel?.forAllShipsGlobal(cb: Consumer<ServerShip>) =
    allShipsGlobal?.forEach(cb)

@GameTickOnly
fun ServerLevel?.forLoadedShipsGlobal(cb: Consumer<LoadedServerShip>) =
    loadedShipsGlobal?.forEach(cb)

@GameTickOnly
fun ServerLevel?.forAllBodiesGlobal(cb: Consumer<ServerVsBody>) =
    allBodiesGlobal?.forEach(cb)

@GameTickOnly
fun ServerLevel?.forLoadedBodiesGlobal(cb: Consumer<LoadedServerVsBody>) =
    loadedBodiesGlobal?.forEach(cb)

@GameTickOnly
fun MinecraftServer?.forAllShips(cb: Consumer<ServerShip>) =
    allShips?.forEach(cb)

@GameTickOnly
fun MinecraftServer?.forLoadedShips(cb: Consumer<LoadedServerShip>) =
    loadedShips?.forEach(cb)

@GameTickOnly
fun MinecraftServer?.forAllBodies(cb: Consumer<ServerVsBody>) =
    allBodies?.forEach(cb)

@GameTickOnly
fun MinecraftServer?.forLoadedBodies(cb: Consumer<LoadedServerVsBody>) =
    loadedBodies?.forEach(cb)


@GameTickOnly
fun ServerLevel?.getShipManagingPos(chunkX: Int, chunkZ: Int) =
    getShipManagingPosImpl(this, chunkX, chunkZ) as ServerShip?

@GameTickOnly
fun ServerLevel?.getShipManagingPos(blockPos: Vec3i) =
    getShipManagingPos(blockPos.x shr 4, blockPos.z shr 4)

@GameTickOnly
fun ServerLevel?.getShipManagingPos(pos: Vector3dc) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun ServerLevel?.getShipManagingPos(pos: Position) =
    getShipManagingPos(pos.x().toInt() shr 4, pos.z().toInt() shr 4)

@GameTickOnly
fun ServerLevel?.getShipManagingPos(posX: Double, posZ: Double) =
    getShipManagingPos(posX.toInt() shr 4, posZ.toInt() shr 4)

@GameTickOnly
fun ServerLevel?.getShipManagingPos(chunkPos: ChunkPos) =
    getShipManagingPos(chunkPos.x, chunkPos.z)

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

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(dimensionId: DimensionId, chunkX: Int, chunkZ: Int) =
    this?.getLevel(dimensionId).getShipManagingPos(chunkX, chunkZ)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(dimensionId: DimensionId, blockPos: Vec3i) =
    this?.getLevel(dimensionId).getShipManagingPos(blockPos)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(dimensionId: DimensionId, pos: Vector3dc) =
    this?.getLevel(dimensionId).getShipManagingPos(pos)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(dimensionId: DimensionId, pos: Position) =
    this?.getLevel(dimensionId).getShipManagingPos(pos)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(dimensionId: DimensionId, posX: Double, posZ: Double) =
    this?.getLevel(dimensionId).getShipManagingPos(posX, posZ)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(dimensionId: DimensionId, chunkPos: ChunkPos) =
    this?.getLevel(dimensionId).getShipManagingPos(chunkPos)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(dimensionId: DimensionId, chunkX: Int, chunkZ: Int) =
    this?.getLevel(dimensionId).getLoadedShipManagingPos(chunkX, chunkZ)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(dimensionId: DimensionId, blockPos: Vec3i) =
    this?.getLevel(dimensionId).getLoadedShipManagingPos(blockPos)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(dimensionId: DimensionId, pos: Vector3dc) =
    this?.getLevel(dimensionId).getLoadedShipManagingPos(pos)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(dimensionId: DimensionId, pos: Position) =
    this?.getLevel(dimensionId).getLoadedShipManagingPos(pos)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(dimensionId: DimensionId, posX: Double, posZ: Double) =
    this?.getLevel(dimensionId).getLoadedShipManagingPos(posX, posZ)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(dimensionId: DimensionId, chunkPos: ChunkPos) =
    this?.getLevel(dimensionId).getLoadedShipManagingPos(chunkPos)



@GameTickOnly
fun MinecraftServer?.getShipManagingPos(levelKey: ResourceKey<Level>, chunkX: Int, chunkZ: Int) =
    this?.getLevel(levelKey).getShipManagingPos(chunkX, chunkZ)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(levelKey: ResourceKey<Level>, blockPos: Vec3i) =
    this?.getLevel(levelKey).getShipManagingPos(blockPos)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(levelKey: ResourceKey<Level>, pos: Vector3dc) =
    this?.getLevel(levelKey).getShipManagingPos(pos)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(levelKey: ResourceKey<Level>, pos: Position) =
    this?.getLevel(levelKey).getShipManagingPos(pos)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(levelKey: ResourceKey<Level>, posX: Double, posZ: Double) =
    this?.getLevel(levelKey).getShipManagingPos(posX, posZ)

@GameTickOnly
fun MinecraftServer?.getShipManagingPos(levelKey: ResourceKey<Level>, chunkPos: ChunkPos) =
    this?.getLevel(levelKey).getShipManagingPos(chunkPos)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(levelKey: ResourceKey<Level>, chunkX: Int, chunkZ: Int) =
    this?.getLevel(levelKey).getLoadedShipManagingPos(chunkX, chunkZ)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(levelKey: ResourceKey<Level>, blockPos: Vec3i) =
    this?.getLevel(levelKey).getLoadedShipManagingPos(blockPos)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(levelKey: ResourceKey<Level>, pos: Vector3dc) =
    this?.getLevel(levelKey).getLoadedShipManagingPos(pos)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(levelKey: ResourceKey<Level>, pos: Position) =
    this?.getLevel(levelKey).getLoadedShipManagingPos(pos)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(levelKey: ResourceKey<Level>, posX: Double, posZ: Double) =
    this?.getLevel(levelKey).getLoadedShipManagingPos(posX, posZ)

@GameTickOnly
fun MinecraftServer?.getLoadedShipManagingPos(levelKey: ResourceKey<Level>, chunkPos: ChunkPos) =
    this?.getLevel(levelKey).getLoadedShipManagingPos(chunkPos)

// endregion

// region Ship extensions
// region position transforming stuff
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

@JvmOverloads
fun Ship.toWorldCoordinates(x: Double, y: Double, z: Double, dest: Vector3d = Vector3d()): Vector3d =
    transform.shipToWorld.transformPosition(dest.set(x, y, z))

fun Ship.toWorldCoordinates(pos: Vector3d, dest: Vector3d): Vector3d =
    shipToWorld.transformPosition(pos, dest)

fun Ship.toWorldCoordinates(pos: Vector3d): Vector3d =
    shipToWorld.transformPosition(pos)

fun Ship.toWorldCoordinates(blockPos: Vec3i): Vector3d =
    shipToWorld.transformPosition(blockPos.toJOMLd())

fun Ship.toWorldCoordinates(pos: Vec3): Vector3d =
    shipToWorld.transformPosition(pos.toJOML())

// endregion

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

/**
 * Will attempt to rename the ServerShip to the newSlug,
 * but if an existing ship is found with that slug it will
 * append a number like `-1` to the slug to make them unique.
 * Not technically required, slugs _can_ be duplicated, but
 * highly recommended.
 */
@GameTickOnly
fun ServerShip.safeRenameTo(level: ServerLevel, newSlug: String) {
    var newSlug = newSlug
    var count = 0
    level.allShips?.forEach {
        if (it.slug?.startsWith(newSlug) ?: false) {
            val newCount: Int = if (it.slug!!.length < newSlug.length + 1)
                1
            else {
                val suffix = it.slug!!.substring(newSlug.length + 1)
                if (suffix.toIntOrNull() != null) suffix.toInt() + 1 else 0
            }

            if (newCount > count)
                count = newCount
        }
    }

    if (count != 0)
        newSlug += "-$count"

    this.slug = newSlug
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

/**
 * Calls the consumer [f] for every (integer) position in the AABBic.
 *
 * This function is expensive, don't call it too often.
 */
fun AABBic.forEach(f: IntTernaryConsumer) {
    for (x in this.minX()..this.maxX()) {
        for (y in this.minY()..this.maxY()) {
            for (z in this.minZ()..this.maxZ()) {
                f.accept(x, y, z)
            }
        }
    }
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

// region client utils
@GameTickOnly
fun multiplyWithShipToWorld(poseStack: PoseStack, ship: ClientShip) {
    poseStack.multiply(ship.renderTransform.shipToWorld, ship.renderTransform.shipToWorldRotation)
}

@GameTickOnly
fun transformRenderIfInShipyard(poseStack: PoseStack, offsetX: Double, offsetY: Double, offsetZ: Double) {
    val ship = Minecraft.getInstance().getLoadedShipManagingPos(offsetX, offsetZ)

    if (ship != null) {
        val transform = ship.renderTransform
        val renderMatrix = Matrix4d()
            .mul(transform.shipToWorld)
            .translate(offsetX, offsetY, offsetZ)

        poseStack.multiply(renderMatrix)
    } else {
        poseStack.translate(offsetX, offsetY, offsetZ)
    }
}
/**
 * Modify the last transform of [poseStack] to be the following:
 *
 * Last = Last * translate(-[camX], -[camY], -[camZ]) * [renderTransform] * translate([offsetX], [offsetY], [offsetZ])
 */
fun transformRenderWithShip(
    renderTransform: ShipTransform,
    poseStack: PoseStack,
    offsetX: Double,
    offsetY: Double,
    offsetZ: Double,
    camX: Double,
    camY: Double,
    camZ: Double
) {
    val shipToWorldMatrix = renderTransform.shipToWorld

    // Create the render matrix from the render transform and player position
    val renderMatrix = Matrix4d()
    renderMatrix.translate(-camX, -camY, -camZ)
    renderMatrix.mul(shipToWorldMatrix)
    renderMatrix.translate(offsetX, offsetY, offsetZ)

    // Multiply the last transform of [poseStack] by [shipToWorldMatrix]
    poseStack.multiply(renderMatrix, renderTransform.shipToWorldRotation)
}


fun transformRenderWithShip(
    renderTransform: ShipTransform,
    matrix: Matrix4f,
    offsetX: Double,
    offsetY: Double,
    offsetZ: Double,
    camX: Double,
    camY: Double,
    camZ: Double
) {
    val shipToWorldMatrix = renderTransform.shipToWorld

    // Create the render matrix from the render transform and player position
    val renderMatrix = Matrix4d()
    renderMatrix.translate(-camX, -camY, -camZ)
    renderMatrix.mul(shipToWorldMatrix)
    renderMatrix.translate(offsetX, offsetY, offsetZ)

    // Multiply the last transform of [poseStack] by [shipToWorldMatrix]
    matrix.mul(Matrix4f(renderMatrix))
}

/**
 * @param renderTransform The ship's render transform
 * @param matrix          The {@link PoseStack} we are multiplying
 * @param blockPos        The position of the block in question
 * @param camX            Player camera X
 * @param camY            Player camera Y
 * @param camZ            Player camera Z
 */
fun transformRenderWithShip(
    renderTransform: ShipTransform,
    matrix: PoseStack,
    blockPos: BlockPos,
    camX: Double,
    camY: Double,
    camZ: Double
) {
    transformRenderWithShip(
        renderTransform,
        matrix,
        blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble(),
        camX, camY, camZ
    )
}
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
