package org.valkyrienskies.mod.common.air_pockets

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import java.util.Arrays
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import org.apache.logging.log4j.LogManager
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.isBlockInShipyard
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.BuoyancyHandlerAttachment
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils
import org.valkyrienskies.mod.util.FluidStateManager

object ShipWaterPocketManager {
    private val log = LogManager.getLogger("[Valkyrien Skies] Air-Pockets")

    private const val FLOOD_UPDATE_INTERVAL_TICKS = 1L
    private const val MAX_SIM_VOLUME = 2_000_000
    private const val POCKET_BOUNDS_PADDING = 1
    private const val AIR_PRESSURE_Y_EPS = 1e-7
    private const val AIR_PRESSURE_ATM = 1.0
    // Minecraft-ish hydrostatic pressure: ~1 atm per 10 blocks of water depth for "water-density" fluids.
    // Pressure increase per block is `density * AIR_PRESSURE_PER_BLOCK_PER_DENSITY`.
    private const val AIR_PRESSURE_PER_BLOCK_PER_DENSITY = 1e-4
    private const val AIR_PRESSURE_SOLVER_ITERS = 4
    private const val AIR_PRESSURE_MIN_EFFECTIVE_AIR_VOLUME = 0.25
    private const val GRAVITY_RESETTLE_MAX_SCHEDULED_TICKS_PER_SHIP_PER_TICK = 4096
    // Flooding speed: this is an abstract "water plane rise" rate. Bigger/more holes increase the rise rate.
    private const val FLOOD_RISE_PER_TICK_BASE = 0.01
    private const val FLOOD_RISE_PER_TICK_PER_HOLE_FACE = 0.00125
    private const val FLOOD_RISE_MAX_PER_TICK = 0.35
    private const val FLOOD_EXIT_PLANE_EPS = 3e-4
    private const val FLOOD_OPENING_LEVEL_EPS = 1e-5
    private const val SUBMERGED_INGRESS_MIN_COVERAGE = 0.34
    // Hard cap for virtual multi-front ingresses used per ship flood update tick.
    private const val MAX_VIRTUAL_INGRESS_FRONTS = 10
    private const val VIRTUAL_INGRESS_MIN_SEPARATION = 4
    private const val VIRTUAL_FRONT_PRELUDE_LINE_CAP = 16
    private const val VIRTUAL_FRONT_PRELUDE_LAYER_CAP = 8
    private const val VIRTUAL_FRONT_PRELUDE_TOTAL_CAP = 40
    private const val GEOMETRY_ASYNC_SUBMISSIONS_PER_LEVEL_PER_TICK = 2
    private const val WATER_SOLVER_ASYNC_SUBMISSIONS_PER_LEVEL_PER_TICK = 2
    private const val MAX_SYNC_WATER_SOLVE_PER_LEVEL_PER_TICK = 2
    private const val WATER_SOLVE_STARVATION_SYNC_FALLBACK_TICKS = 3L
    private const val MAX_WATER_SOLVE_RESULT_AGE_TICKS = 4L
    private const val WATER_SOLVE_PENDING_CANCEL_EXTRA_AGE_TICKS = 2L
    private const val SERVER_WATER_SOLVE_MIN_RESUBMIT_TICKS = 2L
    private const val ASYNC_DIAG_SUMMARY_INTERVAL_TICKS = 200L
    private const val MATERIALIZED_RESYNC_INTERVAL_TICKS = 2L
    private const val PERSIST_FLUSH_INTERVAL_TICKS = 20L
    private const val CLIENT_WATER_SOLVE_DEMAND_WINDOW_TICKS = 10L
    private const val CLIENT_WATER_SOLVE_FORCED_REFRESH_STALE_TICKS = 40L
    private const val CLIENT_WATER_SOLVE_TRANSFORM_KEY_QUANTIZATION = 16.0
    private const val CLIENT_WATER_SOLVE_LARGE_QUERY_RADIUS_CHUNKS = 12
    private const val CLIENT_WATER_SOLVE_HUGE_QUERY_RADIUS_CHUNKS = 8
    private const val INTERSECTING_SHIPS_CACHE_SIZE = 64
    @Volatile
    private var applyingInternalUpdates: Boolean = false

    private val bypassFluidOverridesDepth: ThreadLocal<IntArray> = ThreadLocal.withInitial { intArrayOf(0) }

    private val serverStates: ConcurrentHashMap<DimensionId, ConcurrentHashMap<Long, ShipPocketState>> =
        ConcurrentHashMap()
    private val clientStates: ConcurrentHashMap<DimensionId, ConcurrentHashMap<Long, ShipPocketState>> =
        ConcurrentHashMap()

    private val tmpQueryAabb: ThreadLocal<AABBd> = ThreadLocal.withInitial { AABBd() }
    private val tmpWorldPos: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val tmpShipPos: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val tmpWorldPos2: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val tmpShipPos2: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val tmpWorldPos3: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val tmpShipPos3: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val tmpShipBlockPos: ThreadLocal<BlockPos.MutableBlockPos> =
        ThreadLocal.withInitial { BlockPos.MutableBlockPos() }
    private val tmpShipFlowDir: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val tmpShipGravityVec: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val tmpChunkQueryCache: ThreadLocal<FluidStateManager.QueryCache> =
        ThreadLocal.withInitial { FluidStateManager.QueryCache() }

    private data class ShipFluidSampleCache(
        var lastLevel: Level? = null,
        var lastTick: Long = Long.MIN_VALUE,
        var lastWorldPosLong: Long = Long.MIN_VALUE,
        var lastConfigEnabled: Boolean = false,
        var computed: Boolean = false,
        var flow: Vec3? = null,
        var height: Float? = null,
    )

    private val tmpShipFluidSampleCache: ThreadLocal<ShipFluidSampleCache> =
        ThreadLocal.withInitial { ShipFluidSampleCache() }

    private data class DisplacementAirPhysicsSnapshot(
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val sizeX: Int,
        val sizeY: Int,
        val sizeZ: Int,
        val mask: BitSet,
    )

    internal enum class ClientWaterSolveSkipReason {
        UNCHANGED_TRANSFORM,
        CADENCE,
        NOT_DEMANDED_RECENTLY,
    }

    internal data class ClientWaterSolveDecision(
        val shouldSubmit: Boolean,
        val forcedRefresh: Boolean = false,
        val skipReason: ClientWaterSolveSkipReason? = null,
        val cadenceTicks: Long,
    )

    private data class ClientCameraChunkCenter(
        val chunkX: Int,
        val chunkZ: Int,
    )

    private data class ClientWorldChunkQueryBounds(
        val minChunkX: Int,
        val maxChunkX: Int,
        val minChunkZ: Int,
        val maxChunkZ: Int,
    ) {
        fun containsBlock(blockX: Int, blockZ: Int): Boolean {
            val chunkX = SectionPos.blockToSectionCoord(blockX)
            val chunkZ = SectionPos.blockToSectionCoord(blockZ)
            return chunkX in minChunkX..maxChunkX && chunkZ in minChunkZ..maxChunkZ
        }
    }

    private data class ClientWaterSolveQueryWindow(
        val bounds: ClientWorldChunkQueryBounds,
        val key: Long,
    )

    private data class IntersectingShipsCache(
        val levels: Array<Level?> = arrayOfNulls(INTERSECTING_SHIPS_CACHE_SIZE),
        val ticks: LongArray = LongArray(INTERSECTING_SHIPS_CACHE_SIZE) { Long.MIN_VALUE },
        val worldPosLongs: LongArray = LongArray(INTERSECTING_SHIPS_CACHE_SIZE) { Long.MIN_VALUE },
        val shipsBySlot: Array<List<Ship>> = Array(INTERSECTING_SHIPS_CACHE_SIZE) { emptyList() },
        val occupied: BooleanArray = BooleanArray(INTERSECTING_SHIPS_CACHE_SIZE),
    )

    private val tmpIntersectingShipsCache: ThreadLocal<IntersectingShipsCache> =
        ThreadLocal.withInitial { IntersectingShipsCache() }

    private val tmpFloodQueue: ThreadLocal<IntArray> = ThreadLocal.withInitial { IntArray(0) }
    private val tmpFloodComponentVisited: ThreadLocal<BitSet> = ThreadLocal.withInitial { BitSet() }
    private val tmpDrainTraversalVisited: ThreadLocal<BitSet> = ThreadLocal.withInitial { BitSet() }
    private val tmpPressureComponentVisited: ThreadLocal<BitSet> = ThreadLocal.withInitial { BitSet() }
    private val tmpPressureSubmerged: ThreadLocal<BitSet> = ThreadLocal.withInitial { BitSet() }
    private val tmpLeakedWaterToRemove: ThreadLocal<BitSet> = ThreadLocal.withInitial { BitSet() }
    private val tmpPressureEscapeHeight: ThreadLocal<DoubleArray> = ThreadLocal.withInitial { DoubleArray(0) }
    private val tmpPressureHeapIdx: ThreadLocal<IntArray> = ThreadLocal.withInitial { IntArray(0) }
    private val tmpPressureHeapPos: ThreadLocal<IntArray> = ThreadLocal.withInitial { IntArray(0) }
    private val tmpSubmergedCoverage: ThreadLocal<DoubleArray> = ThreadLocal.withInitial { DoubleArray(0) }
    private val tmpPrecomputedSurfaceY: ThreadLocal<DoubleArray> = ThreadLocal.withInitial { DoubleArray(0) }
    private val coverageFallbackDiagCount = AtomicLong(0)
    private val geometryJobsSubmitted = AtomicLong(0)
    private val geometryJobsCompleted = AtomicLong(0)
    private val geometryJobsDiscarded = AtomicLong(0)
    private val geometryJobsFailed = AtomicLong(0)
    private val geometryComputeNanosTotal = AtomicLong(0)
    private val waterSolveJobsSubmitted = AtomicLong(0)
    private val waterSolveJobsCompleted = AtomicLong(0)
    private val waterSolveJobsDiscarded = AtomicLong(0)
    private val waterSolveJobsFailed = AtomicLong(0)
    private val waterSolveComputeNanosTotal = AtomicLong(0)
    private val waterSolveAgeDiscardCount = AtomicLong(0)
    private val waterSolveStarvationFallbackCount = AtomicLong(0)
    private val waterSolveCancelledOverAgeCount = AtomicLong(0)
    private val waterSolveTransformMismatchDiscardCount = AtomicLong(0)
    private val waterSolveApplyAgeBuckets = Array(6) { AtomicLong(0) }
    private val asyncQueueFullSkips = AtomicLong(0)
    private val waterSolveSyncFallbacks = AtomicLong(0)
    private val clientWaterSolveSkippedUnchangedTransformCount = AtomicLong(0)
    private val clientWaterSolveSkippedCadenceCount = AtomicLong(0)
    private val clientWaterSolveSkippedNotDemandedCount = AtomicLong(0)
    private val clientWaterSolveForcedRefreshCount = AtomicLong(0)
    private val worldSuppressionHits = AtomicLong(0)
    private val floodQueueBacklogHighWater = AtomicLong(0)
    private val microOpeningFilteredCount = AtomicLong(0)
    private val blockedExteriorWaterlogAttempts = AtomicLong(0)
    private val blockedExteriorPlacementAttempts = AtomicLong(0)
    private val rejectedFloodQueueAdds = AtomicLong(0)
    private val asyncOpeningFaceFallbackCount = AtomicLong(0)
    private val persistedStatesLoaded = AtomicLong(0)
    private val persistedStatesSaved = AtomicLong(0)
    private val persistedSignatureMismatches = AtomicLong(0)
    private val componentTraversalOverflowCount = AtomicLong(0)
    private val buoyancyUpdatedCount = AtomicLong(0)
    private val buoyancyRefreshSkippedCount = AtomicLong(0)

    private fun mixHash64(acc: Long, value: Long): Long {
        var h = acc xor value
        h *= -7046029254386353131L
        h = h xor (h ushr 32)
        h *= -7046029254386353131L
        return h xor (h ushr 29)
    }

    private fun intersectingShipsCacheSlot(level: Level, tick: Long, worldPosLong: Long): Int {
        var h = mixHash64(worldPosLong, tick)
        h = mixHash64(h, System.identityHashCode(level).toLong())
        return (h.toInt() and Int.MAX_VALUE) % INTERSECTING_SHIPS_CACHE_SIZE
    }

    internal fun clientWaterSolveCadenceTicksForVolume(volume: Long): Long {
        return when {
            volume <= 32_768L -> 4L
            volume <= 131_072L -> 6L
            volume <= 524_288L -> 10L
            else -> 16L
        }
    }

    internal fun clientWaterSolveNearbyQueryChunkRadiusForVolume(volume: Long): Int? {
        return when {
            volume <= 131_072L -> null
            volume <= 524_288L -> CLIENT_WATER_SOLVE_LARGE_QUERY_RADIUS_CHUNKS
            else -> CLIENT_WATER_SOLVE_HUGE_QUERY_RADIUS_CHUNKS
        }
    }

    internal fun decideClientWaterSolveSubmission(
        state: ShipPocketState,
        volume: Long,
        geometryApplied: Boolean,
        currentTransformKey: Long,
        nowTick: Long,
    ): ClientWaterSolveDecision {
        val cadenceTicks = clientWaterSolveCadenceTicksForVolume(volume)
        if (geometryApplied) {
            return ClientWaterSolveDecision(
                shouldSubmit = true,
                cadenceTicks = cadenceTicks,
            )
        }
        if (state.lastClientWaterSolveApplyTick == Long.MIN_VALUE) {
            return ClientWaterSolveDecision(
                shouldSubmit = true,
                cadenceTicks = cadenceTicks,
            )
        }
        if (currentTransformKey == state.lastClientWaterSolveSubmittedTransformKey) {
            return ClientWaterSolveDecision(
                shouldSubmit = false,
                skipReason = ClientWaterSolveSkipReason.UNCHANGED_TRANSFORM,
                cadenceTicks = cadenceTicks,
            )
        }

        val demandAge = if (state.lastClientDemandTick == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            nowTick - state.lastClientDemandTick
        }
        if (demandAge > CLIENT_WATER_SOLVE_DEMAND_WINDOW_TICKS) {
            val resultAge = nowTick - state.lastClientWaterSolveApplyTick
            val transformChangedSinceApply =
                currentTransformKey != state.lastClientWaterSolveAppliedTransformKey
            if (transformChangedSinceApply && resultAge >= CLIENT_WATER_SOLVE_FORCED_REFRESH_STALE_TICKS) {
                return ClientWaterSolveDecision(
                    shouldSubmit = true,
                    forcedRefresh = true,
                    cadenceTicks = cadenceTicks,
                )
            }
            return ClientWaterSolveDecision(
                shouldSubmit = false,
                skipReason = ClientWaterSolveSkipReason.NOT_DEMANDED_RECENTLY,
                cadenceTicks = cadenceTicks,
            )
        }

        val lastSubmitTick = state.lastWaterSolveSubmitTick
        if (lastSubmitTick != Long.MIN_VALUE && nowTick - lastSubmitTick < cadenceTicks) {
            return ClientWaterSolveDecision(
                shouldSubmit = false,
                skipReason = ClientWaterSolveSkipReason.CADENCE,
                cadenceTicks = cadenceTicks,
            )
        }

        return ClientWaterSolveDecision(
            shouldSubmit = true,
            cadenceTicks = cadenceTicks,
        )
    }

    private fun markClientStateDemanded(level: Level, state: ShipPocketState) {
        if (!level.isClientSide) return
        state.lastClientDemandTick = level.gameTime
    }

    private fun getClientCameraChunkCenter(level: Level): ClientCameraChunkCenter? {
        if (!level.isClientSide) return null
        return try {
            val mcClass = Class.forName("net.minecraft.client.Minecraft")
            val mc = mcClass.getMethod("getInstance").invoke(null) ?: return null
            val currentLevel = mcClass.getField("level").get(mc)
            if (currentLevel !== level) return null

            val gameRenderer = mcClass.getField("gameRenderer").get(mc)
            val camera = gameRenderer?.javaClass?.getMethod("getMainCamera")?.invoke(gameRenderer)
            val cameraPos = camera?.javaClass?.getMethod("getPosition")?.invoke(camera) as? Vec3
            val player = mcClass.getField("player").get(mc)
            val playerPos = player?.javaClass?.getMethod("position")?.invoke(player) as? Vec3
            val origin = cameraPos ?: playerPos ?: return null

            ClientCameraChunkCenter(
                chunkX = SectionPos.blockToSectionCoord(Mth.floor(origin.x)),
                chunkZ = SectionPos.blockToSectionCoord(Mth.floor(origin.z)),
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildClientWaterSolveQueryWindow(
        volume: Long,
        cameraCenter: ClientCameraChunkCenter?,
    ): ClientWaterSolveQueryWindow? {
        val radius = clientWaterSolveNearbyQueryChunkRadiusForVolume(volume) ?: return null
        val center = cameraCenter ?: return null
        val bounds = ClientWorldChunkQueryBounds(
            minChunkX = center.chunkX - radius,
            maxChunkX = center.chunkX + radius,
            minChunkZ = center.chunkZ - radius,
            maxChunkZ = center.chunkZ + radius,
        )
        val centerKey = net.minecraft.world.level.ChunkPos.asLong(center.chunkX, center.chunkZ)
        return ClientWaterSolveQueryWindow(bounds, mixHash64(centerKey, radius.toLong()))
    }

    private fun combineClientWaterSolveTransformKey(
        baseTransformKey: Long,
        queryWindow: ClientWaterSolveQueryWindow?,
    ): Long {
        return if (queryWindow == null) baseTransformKey else mixHash64(baseTransformKey, queryWindow.key)
    }

    private fun transformKeyQuantized(
        minX: Int,
        minY: Int,
        minZ: Int,
        shipTransform: ShipTransform,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        quantization: Double,
    ): Long {
        fun q(v: Double): Long {
            // Quantize to reduce jitter while still catching meaningful motion/rotation changes.
            return kotlin.math.round(v * quantization).toLong()
        }

        var h = 0x1234_5678_9ABCL

        fun sample(sx: Double, sy: Double, sz: Double) {
            shipPosTmp.set(sx, sy, sz)
            shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
            h = mixHash64(h, q(worldPosTmp.x))
            h = mixHash64(h, q(worldPosTmp.y))
            h = mixHash64(h, q(worldPosTmp.z))
        }

        val x0 = minX.toDouble()
        val y0 = minY.toDouble()
        val z0 = minZ.toDouble()
        sample(x0, y0, z0)
        sample(x0 + 1.0, y0, z0)
        sample(x0, y0 + 1.0, z0)
        sample(x0, y0, z0 + 1.0)
        return h
    }

    private fun transformKey(
        minX: Int,
        minY: Int,
        minZ: Int,
        shipTransform: ShipTransform,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
    ): Long = transformKeyQuantized(
        minX = minX,
        minY = minY,
        minZ = minZ,
        shipTransform = shipTransform,
        shipPosTmp = shipPosTmp,
        worldPosTmp = worldPosTmp,
        quantization = 1024.0,
    )

    private fun clientTransformKey(
        minX: Int,
        minY: Int,
        minZ: Int,
        shipTransform: ShipTransform,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
    ): Long = transformKeyQuantized(
        minX = minX,
        minY = minY,
        minZ = minZ,
        shipTransform = shipTransform,
        shipPosTmp = shipPosTmp,
        worldPosTmp = worldPosTmp,
        quantization = CLIENT_WATER_SOLVE_TRANSFORM_KEY_QUANTIZATION,
    )

    private data class BuoyancyFluidProps(
        val density: Double,
        val viscosity: Double,
    )

    private data class FluidCoverageSample(
        val canonicalFluid: Fluid?,
        val coverageRatio: Double,
        val centerSubmerged: Boolean,
    ) {
        fun isIngressQualified(): Boolean {
            val fluid = canonicalFluid ?: return false
            return centerSubmerged || coverageRatio >= SUBMERGED_INGRESS_MIN_COVERAGE
        }

        fun isSubmergedAny(): Boolean {
            return canonicalFluid != null && coverageRatio > 0.0
        }
    }

    private data class OpeningFaceFluidCoverageSample(
        val canonicalFluid: Fluid?,
        val coverageRatio: Double,
        val centerSubmerged: Boolean,
        val faceTopWorldY: Double,
        val estimatedSurfaceY: Double?,
    ) {
        fun isIngressQualified(): Boolean {
            val fluid = canonicalFluid ?: return false
            return centerSubmerged || coverageRatio >= 0.5
        }

        fun isSubmergedAny(): Boolean {
            return canonicalFluid != null && coverageRatio > 0.0
        }
    }

    private data class WorldYAffine(
        val baseWorldY: Double,
        val incX: Double,
        val incY: Double,
        val incZ: Double,
    )

    private val buoyancyFluidPropsCache: ConcurrentHashMap<Fluid, BuoyancyFluidProps> = ConcurrentHashMap()

    private fun getBuoyancyFluidProps(fluid: Fluid): BuoyancyFluidProps {
        return buoyancyFluidPropsCache.computeIfAbsent(fluid) { f ->
            var density = if (f == Fluids.LAVA) 3000.0 else 1000.0
            var viscosity = if (f == Fluids.LAVA) 6000.0 else 1000.0

            // Forge: prefer FluidType density/viscosity when available (supports modded liquids).
            try {
                val getFluidType = f.javaClass.getMethod("getFluidType")
                val fluidType = getFluidType.invoke(f) ?: return@computeIfAbsent BuoyancyFluidProps(density, viscosity)

                val getDensity = fluidType.javaClass.getMethod("getDensity")
                val getViscosity = fluidType.javaClass.getMethod("getViscosity")

                val d = (getDensity.invoke(fluidType) as? Int)?.toDouble()
                val v = (getViscosity.invoke(fluidType) as? Int)?.toDouble()
                if (d != null && d.isFinite() && d > 0.0) density = d
                if (v != null && v.isFinite() && v > 0.0) viscosity = v
            } catch (_: Throwable) {
                // Non-Forge environment or fluid type missing; keep vanilla-ish defaults.
            }

            density = density.coerceIn(100.0, 20_000.0)
            viscosity = viscosity.coerceIn(100.0, 200_000.0)
            BuoyancyFluidProps(density, viscosity)
        }
    }

    @JvmStatic
    fun isApplyingInternalUpdates(): Boolean = applyingInternalUpdates

    @JvmStatic
    fun isBypassingFluidOverrides(): Boolean = bypassFluidOverridesDepth.get()[0] > 0

    @JvmStatic
    fun shouldMarkShipGeometryDirtyForBlockChange(
        level: Level,
        pos: BlockPos,
        previousState: BlockState?,
        newState: BlockState,
    ): Boolean {
        val oldState = previousState ?: return false
        if (oldState == newState) return false

        val oldFluid = oldState.fluidState
        val newFluid = newState.fluidState
        if (
            oldState.block is LiquidBlock &&
            newState.block is LiquidBlock &&
            !oldFluid.isEmpty &&
            !newFluid.isEmpty &&
            canonicalFloodSource(oldFluid.type) == canonicalFloodSource(newFluid.type)
        ) {
            // Ignore liquid level churn for the same fluid type.
            return false
        }

        val oldShape = oldState.getCollisionShape(level, pos)
        val newShape = newState.getCollisionShape(level, pos)
        val collisionChanged = Shapes.joinIsNotEmpty(oldShape, newShape, BooleanOp.NOT_SAME)

        val oldOcclusion = oldState.getOcclusionShape(level, pos)
        val newOcclusion = newState.getOcclusionShape(level, pos)
        val occlusionChanged = Shapes.joinIsNotEmpty(oldOcclusion, newOcclusion, BooleanOp.NOT_SAME)

        // Water/waterlog spread updates in shipyard can be very noisy and should not invalidate pocket geometry
        // unless they actually changed the blocking shape.
        if (
            oldState.block is LiquidBlock ||
            newState.block is LiquidBlock ||
            oldState.hasProperty(BlockStateProperties.WATERLOGGED) ||
            newState.hasProperty(BlockStateProperties.WATERLOGGED)
        ) {
            if (!collisionChanged && !occlusionChanged) return false
        }

        return collisionChanged || occlusionChanged
    }

    private inline fun <T> withBypassedFluidOverrides(block: () -> T): T {
        val depth = bypassFluidOverridesDepth.get()
        depth[0]++
        try {
            return block()
        } finally {
            depth[0]--
        }
    }

    @JvmStatic
    fun markShipDirty(level: Level, shipId: Long) {

        if (!VSGameConfig.COMMON.enableAirPockets) return
        val map = (if (level.isClientSide) clientStates else serverStates)
            .computeIfAbsent(level.dimensionId) { ConcurrentHashMap() }
        val state = map[shipId] ?: run {
            val created = if (!level.isClientSide && level is ServerLevel) {
                loadPersistedServerState(level, shipId) ?: ShipPocketState()
            } else {
                ShipPocketState()
            }
            map[shipId] = created
            created
        }
        val wasAlreadyDirty = state.dirty
        val geometryInFlight = (state.pendingGeometryFuture?.isDone == false) || state.geometryJobInFlight
        state.dirty = true
        state.persistDirty = true
        if (level.isClientSide) {
            state.pendingWaterSolveFuture?.cancel(true)
            state.pendingWaterSolveFuture = null
            state.waterSolveJobInFlight = false
            state.lastClientWaterSolveSubmittedTransformKey = Long.MIN_VALUE
        }
        if (!wasAlreadyDirty || geometryInFlight) {
            state.geometryInvalidationStamp++
        }
    }

    private fun clampBitSetToVolume(bits: BitSet, volume: Int): Boolean {
        val firstOutOfRange = bits.nextSetBit(volume)
        if (firstOutOfRange >= 0) {
            bits.clear(volume, bits.length())
            return true
        }
        return false
    }

    private fun isBitSetSubset(subset: BitSet, superset: BitSet): Boolean {
        var idx = subset.nextSetBit(0)
        while (idx >= 0) {
            if (!superset.get(idx)) return false
            idx = subset.nextSetBit(idx + 1)
        }
        return true
    }

    private fun isRestoredStateStructurallyUsableForBounds(
        state: ShipPocketState,
        minX: Int,
        minY: Int,
        minZ: Int,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
    ): Boolean {
        if (boundsMismatch(state, minX, minY, minZ, sizeX, sizeY, sizeZ)) return false
        val volumeLong = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
        if (volumeLong <= 0L || volumeLong > MAX_SIM_VOLUME.toLong()) return false
        val volume = volumeLong.toInt()

        if (state.faceCondXP.isNotEmpty() && state.faceCondXP.size != volume) return false
        if (state.faceCondYP.isNotEmpty() && state.faceCondYP.size != volume) return false
        if (state.faceCondZP.isNotEmpty() && state.faceCondZP.size != volume) return false

        if (state.open.nextSetBit(volume) >= 0) return false
        if (state.exterior.nextSetBit(volume) >= 0) return false
        if (state.strictInterior.nextSetBit(volume) >= 0) return false
        if (state.simulationDomain.nextSetBit(volume) >= 0) return false
        if (state.outsideVoid.nextSetBit(volume) >= 0) return false
        if (state.flooded.nextSetBit(volume) >= 0) return false
        if (state.materializedWater.nextSetBit(volume) >= 0) return false
        if (state.drainSuppressed.nextSetBit(volume) >= 0) return false
        if (state.waterReachable.nextSetBit(volume) >= 0) return false
        if (state.unreachableVoid.nextSetBit(volume) >= 0) return false

        if (!isBitSetSubset(state.strictInterior, state.open)) return false
        if (!isBitSetSubset(state.simulationDomain, state.open)) return false
        if (!isBitSetSubset(state.outsideVoid, state.open)) return false
        if (state.outsideVoid.intersects(state.simulationDomain)) return false
        if (!isBitSetSubset(state.flooded, state.open)) return false
        if (!isBitSetSubset(state.materializedWater, state.open)) return false
        if (!isBitSetSubset(state.drainSuppressed, state.open)) return false
        if (!isBitSetSubset(state.waterReachable, state.open)) return false
        if (!isBitSetSubset(state.unreachableVoid, state.open)) return false
        if (!isBitSetSubset(state.flooded, state.simulationDomain)) return false
        if (!isBitSetSubset(state.materializedWater, state.simulationDomain)) return false
        if (!isBitSetSubset(state.drainSuppressed, state.simulationDomain)) return false

        return true
    }

    private fun ensureOutsideVoidMask(state: ShipPocketState): Boolean {
        val volumeLong = state.sizeX.toLong() * state.sizeY.toLong() * state.sizeZ.toLong()
        if (volumeLong <= 0L || volumeLong > MAX_SIM_VOLUME.toLong()) return false
        val volume = volumeLong.toInt()
        var changed = false

        if (state.faceCondXP.isNotEmpty() && state.faceCondXP.size != volume) {
            state.faceCondXP = ShortArray(0)
            changed = true
        }
        if (state.faceCondYP.isNotEmpty() && state.faceCondYP.size != volume) {
            state.faceCondYP = ShortArray(0)
            changed = true
        }
        if (state.faceCondZP.isNotEmpty() && state.faceCondZP.size != volume) {
            state.faceCondZP = ShortArray(0)
            changed = true
        }

        changed = clampBitSetToVolume(state.open, volume) || changed
        changed = clampBitSetToVolume(state.exterior, volume) || changed
        changed = clampBitSetToVolume(state.strictInterior, volume) || changed
        changed = clampBitSetToVolume(state.simulationDomain, volume) || changed
        changed = clampBitSetToVolume(state.outsideVoid, volume) || changed
        changed = clampBitSetToVolume(state.flooded, volume) || changed
        changed = clampBitSetToVolume(state.materializedWater, volume) || changed
        changed = clampBitSetToVolume(state.brokenByFlood, volume) || changed
        changed = clampBitSetToVolume(state.drainSuppressed, volume) || changed
        changed = clampBitSetToVolume(state.waterReachable, volume) || changed
        changed = clampBitSetToVolume(state.unreachableVoid, volume) || changed

        val strictInteriorBefore = state.strictInterior.cardinality()
        state.strictInterior.and(state.open)
        if (state.strictInterior.cardinality() != strictInteriorBefore) changed = true

        val simulationBefore = state.simulationDomain.cardinality()
        state.simulationDomain.and(state.open)
        if (state.simulationDomain.cardinality() != simulationBefore) changed = true

        val floodedBefore = state.flooded.cardinality()
        state.flooded.and(state.open)
        state.flooded.and(state.simulationDomain)
        if (state.flooded.cardinality() != floodedBefore) changed = true

        val materializedBefore = state.materializedWater.cardinality()
        state.materializedWater.and(state.open)
        state.materializedWater.and(state.simulationDomain)
        if (state.materializedWater.cardinality() != materializedBefore) changed = true

        val brokenBefore = state.brokenByFlood.cardinality()
        state.brokenByFlood.and(state.open)
        state.brokenByFlood.and(state.simulationDomain)
        if (state.brokenByFlood.cardinality() != brokenBefore) changed = true

        val drainSuppressedBefore = state.drainSuppressed.cardinality()
        state.drainSuppressed.and(state.open)
        state.drainSuppressed.and(state.simulationDomain)
        if (state.drainSuppressed.cardinality() != drainSuppressedBefore) changed = true

        val reachableBefore = state.waterReachable.cardinality()
        state.waterReachable.and(state.open)
        if (state.waterReachable.cardinality() != reachableBefore) changed = true

        val unreachableBefore = state.unreachableVoid.cardinality()
        state.unreachableVoid.and(state.open)
        if (state.unreachableVoid.cardinality() != unreachableBefore) changed = true

        val outsideBefore = state.outsideVoid.cardinality()
        state.outsideVoid.and(state.open)
        state.outsideVoid.andNot(state.simulationDomain)
        if (state.outsideVoid.cardinality() != outsideBefore) changed = true

        if (state.outsideVoid.isEmpty) {
            val outsideCandidates = state.open.clone() as BitSet
            outsideCandidates.andNot(state.simulationDomain)
            if (!outsideCandidates.isEmpty) {
                val hasFaceConductance =
                    state.faceCondXP.size == volume &&
                        state.faceCondYP.size == volume &&
                        state.faceCondZP.size == volume
                state.outsideVoid = if (hasFaceConductance) {
                    computeOutsideVoidFromGeometry(
                        open = state.open,
                        simulationDomain = state.simulationDomain,
                        sizeX = state.sizeX,
                        sizeY = state.sizeY,
                        sizeZ = state.sizeZ,
                        faceCondXP = state.faceCondXP,
                        faceCondYP = state.faceCondYP,
                        faceCondZP = state.faceCondZP,
                    )
                } else {
                    outsideCandidates
                }
                changed = true
            }
        }

        if (changed) {
            state.persistDirty = true
        }
        return true
    }

    private fun loadPersistedServerState(level: ServerLevel, shipId: Long): ShipPocketState? {
        val persisted = ShipWaterPocketPersistence.get(level).getState(shipId) ?: return null
        val restored = ShipPocketState()
        applyPersistedState(restored, persisted)
        val count = persistedStatesLoaded.incrementAndGet()
        logThrottledDiag(count, "Loaded persisted ship pocket state shipId={} bounds=({}, {}, {} ; {}x{}x{})",
            shipId, restored.minX, restored.minY, restored.minZ, restored.sizeX, restored.sizeY, restored.sizeZ)
        return restored
    }

    private fun flushPersistedServerState(level: ServerLevel, shipId: Long, state: ShipPocketState, force: Boolean, nowTick: Long) {
        if (state.restoredFromPersistence && state.awaitingGeometryValidation) return
        if (!force && !state.persistDirty) return
        if (!force && nowTick - state.lastPersistFlushTick < PERSIST_FLUSH_INTERVAL_TICKS) return
        val persisted = snapshotStateForPersistence(state)
        ShipWaterPocketPersistence.get(level).putState(shipId, persisted)
        state.persistDirty = false
        state.lastPersistFlushTick = nowTick
        val count = persistedStatesSaved.incrementAndGet()
        logThrottledDiag(
            count,
            "Saved persisted ship pocket state shipId={} force={} geometryRev={} geometrySig={}",
            shipId,
            force,
            state.geometryRevision,
            state.geometrySignature,
        )
    }

    /**
     * Returns true if a fluid block placement into [shipPos] (in shipyard coordinates) should be blocked because the
     * target cell is outside the simulated flood/suppression domain.
     *
     * This prevents shipyard fluids from "leaking" into the exterior shipyard volume (and therefore rendering in
     * places where the real world should be visible), while still allowing fluids to exist inside simulated pockets.
     */
    @JvmStatic
    fun shouldBlockShipyardWaterPlacement(
        level: Level,
        shipId: Long,
        shipPos: BlockPos,
    ): Boolean {
        if (!VSGameConfig.COMMON.enableAirPockets) return false
        if (level.isClientSide) return false

        val state = serverStates[level.dimensionId]?.get(shipId) ?: return false
        if (state.sizeX <= 0 || state.sizeY <= 0 || state.sizeZ <= 0) return false
        if (state.open.isEmpty) return false
        if (state.dirty) return false

        val lx = shipPos.x - state.minX
        val ly = shipPos.y - state.minY
        val lz = shipPos.z - state.minZ
        val inBounds = lx in 0 until state.sizeX && ly in 0 until state.sizeY && lz in 0 until state.sizeZ
        if (inBounds) {
            val idx = indexOf(state, lx, ly, lz)
            if (!state.open.get(idx)) return true
            if (state.queuedFloodRemoves.get(idx)) {
                val count = blockedExteriorPlacementAttempts.incrementAndGet()
                logThrottledDiag(count, "Blocked shipyard fluid placement into queued draining cell idx={}", idx)
                return true
            }
            if (state.drainSuppressed.get(idx) && !state.materializedWater.get(idx)) {
                val count = blockedExteriorPlacementAttempts.incrementAndGet()
                logThrottledDiag(count, "Blocked shipyard fluid placement into drain-suppressed cell idx={}", idx)
                return true
            }
            if (shouldPreventExteriorWaterlogging(state, idx)) {
                val count = blockedExteriorPlacementAttempts.incrementAndGet()
                logThrottledDiag(count, "Blocked shipyard fluid placement into exterior-connected cell idx={}", idx)
                return true
            }

            val pointClass = classifyShipPoint(
                state = state,
                x = shipPos.x + 0.5,
                y = shipPos.y + 0.5,
                z = shipPos.z + 0.5,
            )

            if (pointClass.kind == PointVoidClass.SOLID || pointClass.kind == PointVoidClass.OUT_OF_BOUNDS) {
                val count = blockedExteriorPlacementAttempts.incrementAndGet()
                logThrottledDiag(count, "Blocked shipyard fluid placement into solid/out-of-bounds cell")
                return true
            }
            if (!isClassificationInSimulationDomain(state, pointClass)) {
                val count = blockedExteriorPlacementAttempts.incrementAndGet()
                logThrottledDiag(count, "Blocked shipyard fluid placement outside simulation-domain")
                return true
            }
            return false
        }

        // Outside the sim bounds, always block; it is never part of the ship interior pocket volume.
        return true
    }

    /**
     * Bucket placement and other direct source writes should stay as player/mod-owned shipyard fluid. The flood system
     * tracks these separately via [onExternalShipFluidPlacement] so drain jobs do not remove them.
     */
    @JvmStatic
    fun shouldAllowDirectExternalShipyardFluidPlacement(
        level: Level,
        shipId: Long,
        shipPos: BlockPos,
        fluidState: FluidState,
    ): Boolean {
        if (!VSGameConfig.COMMON.enableAirPockets) return false
        if (level.isClientSide) return false
        if (fluidState.isEmpty || !fluidState.isSource) return false

        val state = serverStates[level.dimensionId]?.get(shipId) ?: return true
        if (state.sizeX <= 0 || state.sizeY <= 0 || state.sizeZ <= 0) return true

        val lx = shipPos.x - state.minX
        val ly = shipPos.y - state.minY
        val lz = shipPos.z - state.minZ
        if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return true

        val idx = indexOf(state, lx, ly, lz)
        return !state.queuedFloodRemoves.get(idx) && !state.drainSuppressed.get(idx)
    }

    @JvmStatic
    fun shouldAllowImmediateFragileShipyardFloodPlacement(
        level: Level,
        shipId: Long,
        shipPos: BlockPos,
        floodFluid: Fluid,
        currentState: BlockState? = null,
    ): Boolean {
        if (!VSGameConfig.COMMON.enableAirPockets) return false
        if (level.isClientSide) return false

        val state = serverStates[level.dimensionId]?.get(shipId) ?: return false
        if (state.sizeX <= 0 || state.sizeY <= 0 || state.sizeZ <= 0) return false
        if (state.open.isEmpty || state.dirty) return false

        val lx = shipPos.x - state.minX
        val ly = shipPos.y - state.minY
        val lz = shipPos.z - state.minZ
        if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false

        val idx = indexOf(state, lx, ly, lz)
        if (!state.open.get(idx) || !state.simulationDomain.get(idx)) return false
        if (shouldPreventExteriorWaterlogging(state, idx)) return false

        val current = currentState ?: level.getBlockState(shipPos)
        return shouldBreakOnFloodState(level, shipPos, current, floodCanonicalSource(floodFluid))
    }

    @JvmStatic
    fun onExternalShipFluidPlacement(
        level: Level,
        shipId: Long,
        shipPos: BlockPos,
        fluid: Fluid,
    ) {
        if (!VSGameConfig.COMMON.enableAirPockets) return
        if (level.isClientSide) return
        if (fluid == Fluids.EMPTY) return

        val state = serverStates[level.dimensionId]?.get(shipId) ?: return
        if (state.sizeX <= 0 || state.sizeY <= 0 || state.sizeZ <= 0) return

        val lx = shipPos.x - state.minX
        val ly = shipPos.y - state.minY
        val lz = shipPos.z - state.minZ
        if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return

        val idx = indexOf(state, lx, ly, lz)
        if (!state.open.get(idx)) return
        if (shouldPreventExteriorWaterlogging(state, idx)) return
        val pointClass = classifyShipPoint(
            state = state,
            x = shipPos.x + 0.5,
            y = shipPos.y + 0.5,
            z = shipPos.z + 0.5,
        )
        if (pointClass.kind == PointVoidClass.SOLID ||
            pointClass.kind == PointVoidClass.OUT_OF_BOUNDS ||
            !isClassificationInSimulationDomain(state, pointClass)
        ) {
            return
        }

        val canonical = canonicalFloodSource(fluid)
        if (canonical != state.floodFluid) {
            state.floodFluid = canonical
            state.dirty = true
        }

        state.externalFluid.set(idx)
        state.flooded.clear(idx)
        state.materializedWater.clear(idx)
        state.brokenByFlood.clear(idx)
        state.queuedFloodAdds.clear(idx)
        state.queuedFloodRemoves.clear(idx)
        state.persistDirty = true
    }

    private fun logThrottledDiag(counter: Long, message: String, vararg args: Any?) {
        if (counter <= 3L || counter % 512L == 0L) {
            log.debug(message, *args)
        }
    }

    private fun boundsMismatch(
        state: ShipPocketState,
        minX: Int,
        minY: Int,
        minZ: Int,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
    ): Boolean {
        return state.sizeX != sizeX ||
            state.sizeY != sizeY ||
            state.sizeZ != sizeZ ||
            state.minX != minX ||
            state.minY != minY ||
            state.minZ != minZ
    }

    private fun remapStateIndex(
        oldIdx: Int,
        oldMinX: Int,
        oldMinY: Int,
        oldMinZ: Int,
        oldSizeX: Int,
        oldSizeY: Int,
        oldSizeZ: Int,
        newMinX: Int,
        newMinY: Int,
        newMinZ: Int,
        newSizeX: Int,
        newSizeY: Int,
        newSizeZ: Int,
    ): Int {
        val oldVolumeLong = oldSizeX.toLong() * oldSizeY.toLong() * oldSizeZ.toLong()
        if (oldVolumeLong <= 0L || oldIdx < 0 || oldIdx.toLong() >= oldVolumeLong) return -1

        val oldLX = oldIdx % oldSizeX
        val oldT = oldIdx / oldSizeX
        val oldLY = oldT % oldSizeY
        val oldLZ = oldT / oldSizeY

        val worldX = oldMinX + oldLX
        val worldY = oldMinY + oldLY
        val worldZ = oldMinZ + oldLZ

        val newLX = worldX - newMinX
        val newLY = worldY - newMinY
        val newLZ = worldZ - newMinZ
        if (newLX !in 0 until newSizeX || newLY !in 0 until newSizeY || newLZ !in 0 until newSizeZ) {
            return -1
        }
        return newLX + newSizeX * (newLY + newSizeY * newLZ)
    }

    private fun remapStateMask(
        mask: BitSet,
        oldMinX: Int,
        oldMinY: Int,
        oldMinZ: Int,
        oldSizeX: Int,
        oldSizeY: Int,
        oldSizeZ: Int,
        newMinX: Int,
        newMinY: Int,
        newMinZ: Int,
        newSizeX: Int,
        newSizeY: Int,
        newSizeZ: Int,
    ): BitSet {
        val newVolumeLong = newSizeX.toLong() * newSizeY.toLong() * newSizeZ.toLong()
        if (newVolumeLong <= 0L) return BitSet()
        val out = BitSet(newVolumeLong.toInt())
        val oldVolumeLong = oldSizeX.toLong() * oldSizeY.toLong() * oldSizeZ.toLong()
        if (oldVolumeLong <= 0L || mask.isEmpty) return out
        val oldVolume = oldVolumeLong.toInt()

        var oldIdx = mask.nextSetBit(0)
        while (oldIdx >= 0 && oldIdx < oldVolume) {
            val newIdx = remapStateIndex(
                oldIdx = oldIdx,
                oldMinX = oldMinX,
                oldMinY = oldMinY,
                oldMinZ = oldMinZ,
                oldSizeX = oldSizeX,
                oldSizeY = oldSizeY,
                oldSizeZ = oldSizeZ,
                newMinX = newMinX,
                newMinY = newMinY,
                newMinZ = newMinZ,
                newSizeX = newSizeX,
                newSizeY = newSizeY,
                newSizeZ = newSizeZ,
            )
            if (newIdx >= 0) out.set(newIdx)
            oldIdx = mask.nextSetBit(oldIdx + 1)
        }
        return out
    }

    private fun restoreQueuedFloodAddsAfterGeometry(
        state: ShipPocketState,
        oldQueuedAdds: BitSet,
        oldQueuedAddOrder: IntArrayList,
        oldMinX: Int,
        oldMinY: Int,
        oldMinZ: Int,
        oldSizeX: Int,
        oldSizeY: Int,
        oldSizeZ: Int,
        newMinX: Int,
        newMinY: Int,
        newMinZ: Int,
    ) {
        if (oldQueuedAdds.isEmpty) return
        val restored = remapStateMask(
            mask = oldQueuedAdds,
            oldMinX = oldMinX,
            oldMinY = oldMinY,
            oldMinZ = oldMinZ,
            oldSizeX = oldSizeX,
            oldSizeY = oldSizeY,
            oldSizeZ = oldSizeZ,
            newMinX = newMinX,
            newMinY = newMinY,
            newMinZ = newMinZ,
            newSizeX = state.sizeX,
            newSizeY = state.sizeY,
            newSizeZ = state.sizeZ,
        )
        restored.and(state.open)
        restored.and(state.simulationDomain)
        restored.andNot(state.materializedWater)
        if (restored.isEmpty) return

        state.queuedFloodAdds.or(restored)
        val emitted = BitSet(state.sizeX * state.sizeY * state.sizeZ)
        for (i in 0 until oldQueuedAddOrder.size) {
            val oldIdx = oldQueuedAddOrder.getInt(i)
            if (!oldQueuedAdds.get(oldIdx)) continue
            val newIdx = remapStateIndex(
                oldIdx = oldIdx,
                oldMinX = oldMinX,
                oldMinY = oldMinY,
                oldMinZ = oldMinZ,
                oldSizeX = oldSizeX,
                oldSizeY = oldSizeY,
                oldSizeZ = oldSizeZ,
                newMinX = newMinX,
                newMinY = newMinY,
                newMinZ = newMinZ,
                newSizeX = state.sizeX,
                newSizeY = state.sizeY,
                newSizeZ = state.sizeZ,
            )
            if (newIdx < 0 || !restored.get(newIdx) || emitted.get(newIdx)) continue
            emitted.set(newIdx)
            state.queuedFloodAddOrder.add(newIdx)
        }

        var idx = restored.nextSetBit(0)
        while (idx >= 0) {
            if (!emitted.get(idx)) {
                state.queuedFloodAddOrder.add(idx)
            }
            idx = restored.nextSetBit(idx + 1)
        }
        state.nextQueuedAddOrderIdx = 0
        state.nextQueuedAddIdx = 0
    }

    private fun trySubmitGeometryJob(
        level: Level,
        state: ShipPocketState,
        minX: Int,
        minY: Int,
        minZ: Int,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
    ): Boolean {
        val pending = state.pendingGeometryFuture
        if (pending != null && !pending.isDone) {
            state.geometryJobInFlight = true
            return false
        }

        val generation = state.requestedGeometryGeneration + 1L
        state.requestedGeometryGeneration = generation
        val invalidationStamp = state.geometryInvalidationStamp

        val snapshot = try {
            captureGeometryAsyncSnapshot(
                level = level,
                generation = generation,
                invalidationStamp = invalidationStamp,
                minX = minX,
                minY = minY,
                minZ = minZ,
                sizeX = sizeX,
                sizeY = sizeY,
                sizeZ = sizeZ,
                prevMinX = state.minX,
                prevMinY = state.minY,
                prevMinZ = state.minZ,
                prevSizeX = state.sizeX,
                prevSizeY = state.sizeY,
                prevSizeZ = state.sizeZ,
                prevSimulationDomain = state.simulationDomain.clone() as BitSet,
                floodFluid = state.floodFluid,
            )
        } catch (t: Throwable) {
            val count = geometryJobsFailed.incrementAndGet()
            logThrottledDiag(count, "Failed to capture ship pocket geometry snapshot", t)
            state.dirty = true
            return false
        }

        val submittedFuture = ShipPocketAsyncRuntime.trySubmit(
            subsystem = ShipPocketAsyncSubsystem.GEOMETRY,
            task = { computeGeometryAsync(snapshot) },
        )
        if (submittedFuture == null) {
            val count = asyncQueueFullSkips.incrementAndGet()
            logThrottledDiag(
                count,
                "Skipped geometry job submit: async queue full pending={} max={}",
                ShipPocketAsyncRuntime.pendingJobCount(),
                ShipPocketAsyncRuntime.maxPendingJobs(),
            )
            state.dirty = true
            return false
        }
        state.pendingGeometryFuture = submittedFuture
        state.geometryJobInFlight = true

        val count = geometryJobsSubmitted.incrementAndGet()
        logThrottledDiag(
            count,
            "Submitted ship pocket geometry job gen={} invalidation={} bounds=({}, {}, {} ; {}x{}x{})",
            generation,
            invalidationStamp,
            minX,
            minY,
            minZ,
            sizeX,
            sizeY,
            sizeZ,
        )
        return true
    }

    private fun applyGeometryResult(
        state: ShipPocketState,
        result: GeometryAsyncResult,
        preserveLiveFloodState: Boolean,
    ): DisplacementAirPhysicsSnapshot {
        val previousDisplacementAir = captureDisplacementAirPhysicsSnapshot(state)
        val wasRestored = state.restoredFromPersistence
        val previousSignature = state.geometrySignature
        val persistedMaterialized =
            if (wasRestored) state.materializedWater.clone() as BitSet else BitSet()
        val prevMinX = state.minX
        val prevMinY = state.minY
        val prevMinZ = state.minZ
        val prevSizeX = state.sizeX
        val prevSizeY = state.sizeY
        val prevSizeZ = state.sizeZ
        val liveFlooded = state.flooded.clone() as BitSet
        val liveMaterialized = state.materializedWater.clone() as BitSet
        val liveBrokenByFlood = state.brokenByFlood.clone() as BitSet
        val liveDrainSuppressed = state.drainSuppressed.clone() as BitSet
        val liveWaterReachable = state.waterReachable.clone() as BitSet
        val liveQueuedAdds = state.queuedFloodAdds.clone() as BitSet
        val liveQueuedAddOrder = IntArrayList(state.queuedFloodAddOrder)
        val boundsChanged = boundsMismatch(
            state = state,
            minX = result.minX,
            minY = result.minY,
            minZ = result.minZ,
            sizeX = result.sizeX,
            sizeY = result.sizeY,
            sizeZ = result.sizeZ,
        )

        val prevOpen = state.open
        val prevFaceCondXP = state.faceCondXP
        val prevFaceCondYP = state.faceCondYP
        val prevFaceCondZP = state.faceCondZP

        state.minX = result.minX
        state.minY = result.minY
        state.minZ = result.minZ
        state.sizeX = result.sizeX
        state.sizeY = result.sizeY
        state.sizeZ = result.sizeZ

        state.open = result.open
        state.exterior = result.exterior
        state.outsideVoid = result.outsideVoid
        state.strictInterior = result.strictInterior
        state.simulationDomain = result.simulationDomain
        state.interior = result.interior
        state.flooded = result.flooded
        state.materializedWater = result.materializedWater
        state.brokenByFlood = BitSet(result.sizeX * result.sizeY * result.sizeZ)
        state.drainSuppressed = BitSet(result.sizeX * result.sizeY * result.sizeZ)
        state.faceCondXP = result.faceCondXP
        state.faceCondYP = result.faceCondYP
        state.faceCondZP = result.faceCondZP
        state.shapeTemplatePalette = result.templatePalette
        state.templateIndexByVoxel = result.templateIndexByVoxel
        state.voxelExteriorComponentMask = result.voxelExteriorComponentMask
        state.voxelInteriorComponentMask = result.voxelInteriorComponentMask
        state.voxelSimulationComponentMask = result.voxelSimulationComponentMask
        state.componentGraphDegraded = result.componentGraphDegraded
        state.geometrySignature = result.geometrySignature
        state.waterReachable = remapStateMask(
            mask = liveWaterReachable,
            oldMinX = prevMinX,
            oldMinY = prevMinY,
            oldMinZ = prevMinZ,
            oldSizeX = prevSizeX,
            oldSizeY = prevSizeY,
            oldSizeZ = prevSizeZ,
            newMinX = result.minX,
            newMinY = result.minY,
            newMinZ = result.minZ,
            newSizeX = result.sizeX,
            newSizeY = result.sizeY,
            newSizeZ = result.sizeZ,
        )
        state.waterReachable.and(state.open)
        state.waterReachable.and(state.simulationDomain)
        state.unreachableVoid = state.open.clone() as BitSet
        state.unreachableVoid.andNot(state.waterReachable)
        state.floodPlaneByComponent.clear()
        clearFloodWriteQueues(state)

        if (preserveLiveFloodState) {
            val preservedFlooded = remapStateMask(
                mask = liveFlooded,
                oldMinX = prevMinX,
                oldMinY = prevMinY,
                oldMinZ = prevMinZ,
                oldSizeX = prevSizeX,
                oldSizeY = prevSizeY,
                oldSizeZ = prevSizeZ,
                newMinX = result.minX,
                newMinY = result.minY,
                newMinZ = result.minZ,
                newSizeX = result.sizeX,
                newSizeY = result.sizeY,
                newSizeZ = result.sizeZ,
            )
            preservedFlooded.and(state.open)
            preservedFlooded.and(state.simulationDomain)
            state.flooded.or(preservedFlooded)

            val preservedMaterialized = remapStateMask(
                mask = liveMaterialized,
                oldMinX = prevMinX,
                oldMinY = prevMinY,
                oldMinZ = prevMinZ,
                oldSizeX = prevSizeX,
                oldSizeY = prevSizeY,
                oldSizeZ = prevSizeZ,
                newMinX = result.minX,
                newMinY = result.minY,
                newMinZ = result.minZ,
                newSizeX = result.sizeX,
                newSizeY = result.sizeY,
                newSizeZ = result.sizeZ,
            )
            preservedMaterialized.and(state.open)
            preservedMaterialized.and(state.simulationDomain)
            state.materializedWater.or(preservedMaterialized)
            state.flooded.or(state.materializedWater)
        }

        state.brokenByFlood = remapStateMask(
            mask = liveBrokenByFlood,
            oldMinX = prevMinX,
            oldMinY = prevMinY,
            oldMinZ = prevMinZ,
            oldSizeX = prevSizeX,
            oldSizeY = prevSizeY,
            oldSizeZ = prevSizeZ,
            newMinX = result.minX,
            newMinY = result.minY,
            newMinZ = result.minZ,
            newSizeX = result.sizeX,
            newSizeY = result.sizeY,
            newSizeZ = result.sizeZ,
        )
        state.brokenByFlood.and(state.open)
        state.brokenByFlood.and(state.simulationDomain)

        state.drainSuppressed = remapStateMask(
            mask = liveDrainSuppressed,
            oldMinX = prevMinX,
            oldMinY = prevMinY,
            oldMinZ = prevMinZ,
            oldSizeX = prevSizeX,
            oldSizeY = prevSizeY,
            oldSizeZ = prevSizeZ,
            newMinX = result.minX,
            newMinY = result.minY,
            newMinZ = result.minZ,
            newSizeX = result.sizeX,
            newSizeY = result.sizeY,
            newSizeZ = result.sizeZ,
        )
        state.drainSuppressed.and(state.open)
        state.drainSuppressed.and(state.simulationDomain)

        restoreQueuedFloodAddsAfterGeometry(
            state = state,
            oldQueuedAdds = liveQueuedAdds,
            oldQueuedAddOrder = liveQueuedAddOrder,
            oldMinX = prevMinX,
            oldMinY = prevMinY,
            oldMinZ = prevMinZ,
            oldSizeX = prevSizeX,
            oldSizeY = prevSizeY,
            oldSizeZ = prevSizeZ,
            newMinX = result.minX,
            newMinY = result.minY,
            newMinZ = result.minZ,
        )

        if (
            boundsChanged ||
            prevOpen != state.open ||
            !prevFaceCondXP.contentEquals(state.faceCondXP) ||
            !prevFaceCondYP.contentEquals(state.faceCondYP) ||
            !prevFaceCondZP.contentEquals(state.faceCondZP)
        ) {
            state.geometryRevision++
        }

        val signatureMismatch = wasRestored &&
            previousSignature != 0L &&
            previousSignature != result.geometrySignature
        if (signatureMismatch) {
            val count = persistedSignatureMismatches.incrementAndGet()
            logThrottledDiag(
                count,
                "Persisted geometry signature mismatch old={} new={}",
                previousSignature,
                result.geometrySignature,
            )
            // Preserve persisted materialized fluid cells only where geometry still has simulated open volume.
            persistedMaterialized.and(state.open)
            persistedMaterialized.and(state.simulationDomain)
            state.materializedWater.or(persistedMaterialized)
            state.waterReachable.clear()
            state.unreachableVoid = state.open.clone() as BitSet
            state.unreachableVoid.andNot(state.waterReachable)
            state.floodPlaneByComponent.clear()
            state.dirty = true
        }

        state.appliedGeometryGeneration = result.generation
        state.geometryLastComputeNanos = result.computeNanos
        state.geometryComputeCount++
        if (!signatureMismatch) {
            state.dirty = false
        }
        state.restoredFromPersistence = false
        state.awaitingGeometryValidation = false
        state.persistDirty = true
        return previousDisplacementAir
    }

    private fun tryApplyCompletedGeometryJob(
        level: ServerLevel? = null,
        state: ShipPocketState,
        minX: Int,
        minY: Int,
        minZ: Int,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
        preserveLiveFloodState: Boolean,
    ): Boolean {
        val future = state.pendingGeometryFuture ?: return false
        if (!future.isDone) return false

        state.pendingGeometryFuture = null
        state.geometryJobInFlight = false

        val result = try {
            future.join()
        } catch (t: Throwable) {
            val root = t.cause ?: t
            val count = geometryJobsFailed.incrementAndGet()
            logThrottledDiag(count, "Ship pocket geometry job failed", root)
            state.dirty = true
            return false
        }

        if (result.generation != state.requestedGeometryGeneration ||
            result.invalidationStamp != state.geometryInvalidationStamp ||
            result.minX != minX ||
            result.minY != minY ||
            result.minZ != minZ ||
            result.sizeX != sizeX ||
            result.sizeY != sizeY ||
            result.sizeZ != sizeZ
        ) {
            val count = geometryJobsDiscarded.incrementAndGet()
            logThrottledDiag(
                count,
                "Discarded stale ship pocket geometry result gen={} currentGen={} invalidation={} currentInvalidation={}",
                result.generation,
                state.requestedGeometryGeneration,
                result.invalidationStamp,
                state.geometryInvalidationStamp,
            )
            state.dirty = true
            return false
        }

        val previousDisplacementAir = applyGeometryResult(
            state = state,
            result = result,
            preserveLiveFloodState = preserveLiveFloodState,
        )
        if (level != null) {
            syncDisplacementAirPhysics(level, previousDisplacementAir, state)
        }
        geometryComputeNanosTotal.addAndGet(result.computeNanos)
        val completed = geometryJobsCompleted.incrementAndGet()
        val avgMs = geometryComputeNanosTotal.get().toDouble() / completed.toDouble() / 1_000_000.0
        logThrottledDiag(
            completed,
            "Applied ship pocket geometry gen={} computeMs={} avgComputeMs={}",
            result.generation,
            result.computeNanos.toDouble() / 1_000_000.0,
            avgMs,
        )
        return true
    }

    private fun computeWorldYAffine(
        minX: Int,
        minY: Int,
        minZ: Int,
        shipTransform: ShipTransform,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
    ): WorldYAffine {
        val baseShipX = minX.toDouble()
        val baseShipY = minY.toDouble()
        val baseShipZ = minZ.toDouble()

        shipPosTmp.set(baseShipX, baseShipY, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val baseWorldY = worldPosTmp.y

        shipPosTmp.set(baseShipX + 1.0, baseShipY, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incX = worldPosTmp.y - baseWorldY

        shipPosTmp.set(baseShipX, baseShipY + 1.0, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incY = worldPosTmp.y - baseWorldY

        shipPosTmp.set(baseShipX, baseShipY, baseShipZ + 1.0)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incZ = worldPosTmp.y - baseWorldY

        return WorldYAffine(
            baseWorldY = baseWorldY,
            incX = incX,
            incY = incY,
            incZ = incZ,
        )
    }

    private fun isCurrentShipSampleInAirPocket(
        state: ShipPocketState,
        shipX: Double,
        shipY: Double,
        shipZ: Double,
        shipPosTmp: Vector3d,
        shipBlockPosTmp: BlockPos.MutableBlockPos,
    ): Boolean {
        shipPosTmp.set(shipX, shipY, shipZ)
        val classification = classifyShipPointWithEpsilon(
            state = state,
            x = shipX,
            y = shipY,
            z = shipZ,
            out = shipBlockPosTmp,
        )
        return isAirPocketClassification(state, classification) ||
            (classification.kind == PointVoidClass.SOLID &&
                findNearbyAirPocket(state, shipPosTmp, shipBlockPosTmp, radius = 0) != null)
    }

    private fun sampleCanonicalWorldFluidAtShipPoint(
        level: Level,
        shipTransform: ShipTransform,
        shipX: Double,
        shipY: Double,
        shipZ: Double,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        worldBlockPos: BlockPos.MutableBlockPos,
        queryCache: FluidStateManager.QueryCache? = null,
        currentShipState: ShipPocketState? = null,
        clientWorldQueryBounds: ClientWorldChunkQueryBounds? = null,
    ): Fluid? {
        val epsY = 1e-5
        if (currentShipState != null &&
            isCurrentShipSampleInAirPocket(
                state = currentShipState,
                shipX = shipX,
                shipY = shipY,
                shipZ = shipZ,
                shipPosTmp = shipPosTmp,
                shipBlockPosTmp = worldBlockPos,
            )
        ) {
            return null
        }

        shipPosTmp.set(shipX, shipY, shipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)

        val wx = Mth.floor(worldPosTmp.x)
        val wy = Mth.floor(worldPosTmp.y)
        val wz = Mth.floor(worldPosTmp.z)
        worldBlockPos.set(wx, wy, wz)
        if (clientWorldQueryBounds != null && !clientWorldQueryBounds.containsBlock(wx, wz)) {
            return null
        }

        val worldFluid = FluidStateManager.getFluidData(level, worldBlockPos, queryCache)
        if (worldFluid == null) return null
        if (!worldFluid.isSurface || worldFluid.surface.isSource) return worldFluid.sourceFluid()

        val height = worldFluid.height().toDouble()
        val localY = worldPosTmp.y - wy.toDouble()
        return if (localY <= height + epsY) worldFluid.sourceFluid() else null
    }

    private fun estimateExteriorFluidSurfaceYAtShipPoint(
        level: Level,
        shipTransform: ShipTransform,
        shipX: Double,
        shipY: Double,
        shipZ: Double,
        sampleFluid: Fluid,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        worldBlockPos: BlockPos.MutableBlockPos,
        queryCache: FluidStateManager.QueryCache? = null,
        currentShipState: ShipPocketState? = null,
        clientWorldQueryBounds: ClientWorldChunkQueryBounds? = null,
    ): Double? {
        return withBypassedFluidOverrides {
            val canonical = canonicalFloodSource(sampleFluid)
            if (currentShipState != null &&
                isCurrentShipSampleInAirPocket(
                    state = currentShipState,
                    shipX = shipX,
                    shipY = shipY,
                    shipZ = shipZ,
                    shipPosTmp = shipPosTmp,
                    shipBlockPosTmp = worldBlockPos,
                )
            ) {
                return@withBypassedFluidOverrides null
            }

            shipPosTmp.set(shipX, shipY, shipZ)
            shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)

            worldBlockPos.set(
                Mth.floor(worldPosTmp.x),
                Mth.floor(worldPosTmp.y),
                Mth.floor(worldPosTmp.z),
            )
            if (clientWorldQueryBounds != null &&
                !clientWorldQueryBounds.containsBlock(worldBlockPos.x, worldBlockPos.z)
            ) {
                return@withBypassedFluidOverrides null
            }

            val fs = FluidStateManager.getFluidData(level, worldBlockPos, queryCache)
            if (fs == null || fs.sourceFluid() != canonical) {
                null
            } else {
                val surfaceHeight = if (fs.surface.isSource) 1.0 else fs.surface.ownHeight.toDouble()
                fs.topY.toDouble() + surfaceHeight
            }
        }
    }

    private fun estimateExteriorFluidSurfaceY(
        level: Level,
        shipTransform: ShipTransform,
        shipBlockPos: BlockPos,
        sampleFluid: Fluid,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        worldBlockPos: BlockPos.MutableBlockPos,
        queryCache: FluidStateManager.QueryCache? = null,
        currentShipState: ShipPocketState? = null,
        clientWorldQueryBounds: ClientWorldChunkQueryBounds? = null,
    ): Double? {
        return estimateExteriorFluidSurfaceYAtShipPoint(
            level = level,
            shipTransform = shipTransform,
            shipX = shipBlockPos.x + 0.5,
            shipY = shipBlockPos.y + 0.5,
            shipZ = shipBlockPos.z + 0.5,
            sampleFluid = sampleFluid,
            shipPosTmp = shipPosTmp,
            worldPosTmp = worldPosTmp,
            worldBlockPos = worldBlockPos,
            queryCache = queryCache,
            currentShipState = currentShipState,
            clientWorldQueryBounds = clientWorldQueryBounds,
        )
    }

    private fun captureWaterSolveSnapshot(
        level: Level,
        state: ShipPocketState,
        shipTransform: ShipTransform,
        generation: Long,
        captureTick: Long,
        transformKeyOverride: Long? = null,
        clientWorldQueryBounds: ClientWorldChunkQueryBounds? = null,
    ): WaterSolveSnapshot? {
        val sizeX = state.sizeX
        val sizeY = state.sizeY
        val sizeZ = state.sizeZ
        val volumeLong = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
        if (volumeLong <= 0 || volumeLong > MAX_SIM_VOLUME.toLong()) return null
        val volume = volumeLong.toInt()
        val shipPosTmp = tmpShipPos2.get()
        val worldPosTmp = tmpWorldPos2.get()
        val queryCache = tmpChunkQueryCache.get().apply { reset() }
        val shipBlockPos = tmpShipBlockPos.get()
        val worldBlockPos = BlockPos.MutableBlockPos()

        val transformKeyValue = transformKeyOverride ?: transformKey(
            minX = state.minX,
            minY = state.minY,
            minZ = state.minZ,
            shipTransform = shipTransform,
            shipPosTmp = shipPosTmp,
            worldPosTmp = worldPosTmp,
        )

        val affine = computeWorldYAffine(
            minX = state.minX,
            minY = state.minY,
            minZ = state.minZ,
            shipTransform = shipTransform,
            shipPosTmp = shipPosTmp,
            worldPosTmp = worldPosTmp,
        )

        val submerged = BitSet(volume)
        var submergedCoverage = tmpSubmergedCoverage.get()
        if (submergedCoverage.size < volume) {
            submergedCoverage = DoubleArray(volume)
            tmpSubmergedCoverage.set(submergedCoverage)
        } else {
            java.util.Arrays.fill(submergedCoverage, 0, volume, 0.0)
        }
        val buoyancySubmergedCoverage = DoubleArray(volume)

        var surfaceYByCell = tmpPrecomputedSurfaceY.get()
        if (surfaceYByCell.size < volume) {
            surfaceYByCell = DoubleArray(volume)
            tmpPrecomputedSurfaceY.set(surfaceYByCell)
        }
        java.util.Arrays.fill(surfaceYByCell, 0, volume, Double.NaN)

        val floodFluidScores = HashMap<Fluid, Double>()
        val open = state.open
        var idx = open.nextSetBit(0)
        while (idx >= 0 && idx < volume) {
            posFromIndex(state, idx, shipBlockPos)
            val coverage = getShipCellFluidCoverage(
                level = level,
                shipTransform = shipTransform,
                currentShipState = state,
                shipBlockPos = shipBlockPos,
                shipPosTmp = shipPosTmp,
                worldPosTmp = worldPosTmp,
                worldBlockPos = worldBlockPos,
                queryCache = queryCache,
                clientWorldQueryBounds = clientWorldQueryBounds,
            )
            val fluid = coverage.canonicalFluid
            if (coverage.isSubmergedAny() && fluid != null) {
                submergedCoverage[idx] = coverage.coverageRatio
                val score = if (coverage.isIngressQualified()) {
                    coverage.coverageRatio.coerceAtLeast(SUBMERGED_INGRESS_MIN_COVERAGE)
                } else {
                    coverage.coverageRatio * 0.25
                }
                floodFluidScores[fluid] = (floodFluidScores[fluid] ?: 0.0) + score

                if (coverage.isIngressQualified()) {
                    submerged.set(idx)
                    val surface = estimateExteriorFluidSurfaceY(
                        level = level,
                        shipTransform = shipTransform,
                        currentShipState = state,
                        shipBlockPos = shipBlockPos,
                        sampleFluid = fluid,
                        shipPosTmp = shipPosTmp,
                        worldPosTmp = worldPosTmp,
                        worldBlockPos = worldBlockPos,
                        queryCache = queryCache,
                        clientWorldQueryBounds = clientWorldQueryBounds,
                    )
                    if (surface != null && surface.isFinite()) {
                        surfaceYByCell[idx] = surface
                    }
                }
            }
            if (state.simulationDomain.get(idx) && !state.materializedWater.get(idx)) {
                val buoyancyCoverage = if (coverage.isSubmergedAny()) {
                    coverage
                } else {
                    getShipCellFluidCoverage(
                        level = level,
                        shipTransform = shipTransform,
                        currentShipState = null,
                        shipBlockPos = shipBlockPos,
                        shipPosTmp = shipPosTmp,
                        worldPosTmp = worldPosTmp,
                        worldBlockPos = worldBlockPos,
                        queryCache = queryCache,
                        clientWorldQueryBounds = clientWorldQueryBounds,
                    )
                }
                if (buoyancyCoverage.isSubmergedAny()) {
                    buoyancySubmergedCoverage[idx] = buoyancyCoverage.coverageRatio
                }
            }
            idx = open.nextSetBit(idx + 1)
        }

        var dominantFloodFluid: Fluid? = null
        var dominantScore = Double.NEGATIVE_INFINITY
        for ((fluid, score) in floodFluidScores) {
            if (score > dominantScore) {
                dominantScore = score
                dominantFloodFluid = fluid
            }
        }

        fun faceKey(idx: Int, dirCode: Int): Long {
            return (idx.toLong() shl 3) or (dirCode.toLong() and 7L)
        }

        fun worldYAtLocal(x: Double, y: Double, z: Double): Double {
            return affine.baseWorldY + affine.incX * x + affine.incY * y + affine.incZ * z
        }

        fun openingFaceTopWorldYFromCorners(lx: Int, ly: Int, lz: Int, outDirCode: Int): Double {
            val x0 = lx.toDouble()
            val y0 = ly.toDouble()
            val z0 = lz.toDouble()
            val x1 = x0 + 1.0
            val y1 = y0 + 1.0
            val z1 = z0 + 1.0
            return when (outDirCode) {
                0 -> maxOf(
                    worldYAtLocal(x0, y0, z0),
                    worldYAtLocal(x0, y1, z0),
                    worldYAtLocal(x0, y0, z1),
                    worldYAtLocal(x0, y1, z1),
                )
                1 -> maxOf(
                    worldYAtLocal(x1, y0, z0),
                    worldYAtLocal(x1, y1, z0),
                    worldYAtLocal(x1, y0, z1),
                    worldYAtLocal(x1, y1, z1),
                )
                2 -> maxOf(
                    worldYAtLocal(x0, y0, z0),
                    worldYAtLocal(x1, y0, z0),
                    worldYAtLocal(x0, y0, z1),
                    worldYAtLocal(x1, y0, z1),
                )
                3 -> maxOf(
                    worldYAtLocal(x0, y1, z0),
                    worldYAtLocal(x1, y1, z0),
                    worldYAtLocal(x0, y1, z1),
                    worldYAtLocal(x1, y1, z1),
                )
                4 -> maxOf(
                    worldYAtLocal(x0, y0, z0),
                    worldYAtLocal(x1, y0, z0),
                    worldYAtLocal(x0, y1, z0),
                    worldYAtLocal(x1, y1, z0),
                )
                else -> maxOf(
                    worldYAtLocal(x0, y0, z1),
                    worldYAtLocal(x1, y0, z1),
                    worldYAtLocal(x0, y1, z1),
                    worldYAtLocal(x1, y1, z1),
                )
            }
        }

        fun openingFaceTopWorldY(
            curIdx: Int,
            lx: Int,
            ly: Int,
            lz: Int,
            nIdx: Int,
            outDirCode: Int,
            componentMaskCur: Long = -1L,
            componentMaskNeighbor: Long = -1L,
        ): Double {
            val fallback = openingFaceTopWorldYFromCorners(lx, ly, lz, outDirCode)
            if (curIdx !in 0 until volume || nIdx !in 0 until volume) return fallback
            if (state.templateIndexByVoxel.size != volume || state.shapeTemplatePalette.isEmpty()) return fallback

            val templateCurIdx = state.templateIndexByVoxel[curIdx]
            val templateNeighborIdx = state.templateIndexByVoxel[nIdx]
            if (templateCurIdx !in state.shapeTemplatePalette.indices ||
                templateNeighborIdx !in state.shapeTemplatePalette.indices
            ) {
                return fallback
            }

            val templateCur = state.shapeTemplatePalette[templateCurIdx]
            val templateNeighbor = state.shapeTemplatePalette[templateNeighborIdx]
            val faceCur = when (outDirCode) {
                0 -> SHAPE_FACE_NEG_X
                1 -> SHAPE_FACE_POS_X
                2 -> SHAPE_FACE_NEG_Y
                3 -> SHAPE_FACE_POS_Y
                4 -> SHAPE_FACE_NEG_Z
                else -> SHAPE_FACE_POS_Z
            }
            val faceNeighbor = when (faceCur) {
                SHAPE_FACE_NEG_X -> SHAPE_FACE_POS_X
                SHAPE_FACE_POS_X -> SHAPE_FACE_NEG_X
                SHAPE_FACE_NEG_Y -> SHAPE_FACE_POS_Y
                SHAPE_FACE_POS_Y -> SHAPE_FACE_NEG_Y
                SHAPE_FACE_NEG_Z -> SHAPE_FACE_POS_Z
                else -> SHAPE_FACE_NEG_Z
            }
            val faceOffsetCur = faceCur * SHAPE_FACE_SAMPLE_COUNT
            val faceOffsetNeighbor = faceNeighbor * SHAPE_FACE_SAMPLE_COUNT

            var bestY = Double.NEGATIVE_INFINITY
            for (sampleIdx in 0 until SHAPE_FACE_SAMPLE_COUNT) {
                val componentCur = templateCur.faceSampleComponent[faceOffsetCur + sampleIdx].toInt()
                if (componentCur < 0) continue
                if (componentMaskCur != -1L && ((componentMaskCur ushr componentCur) and 1L) == 0L) continue

                val componentNeighbor = templateNeighbor.faceSampleComponent[faceOffsetNeighbor + sampleIdx].toInt()
                if (componentNeighbor < 0) continue
                if (componentMaskNeighbor != -1L &&
                    ((componentMaskNeighbor ushr componentNeighbor) and 1L) == 0L
                ) {
                    continue
                }

                val u = sampleIdx and (SHAPE_FACE_SAMPLE_RES - 1)
                val v = sampleIdx ushr 3
                val du = (u + 0.5) / SHAPE_FACE_SAMPLE_RES.toDouble()
                val dv = (v + 0.5) / SHAPE_FACE_SAMPLE_RES.toDouble()

                val sampleX: Double
                val sampleY: Double
                val sampleZ: Double
                when (outDirCode) {
                    0 -> {
                        sampleX = lx.toDouble()
                        sampleY = ly + du
                        sampleZ = lz + dv
                    }
                    1 -> {
                        sampleX = lx + 1.0
                        sampleY = ly + du
                        sampleZ = lz + dv
                    }
                    2 -> {
                        sampleX = lx + du
                        sampleY = ly.toDouble()
                        sampleZ = lz + dv
                    }
                    3 -> {
                        sampleX = lx + du
                        sampleY = ly + 1.0
                        sampleZ = lz + dv
                    }
                    4 -> {
                        sampleX = lx + du
                        sampleY = ly + dv
                        sampleZ = lz.toDouble()
                    }
                    else -> {
                        sampleX = lx + du
                        sampleY = ly + dv
                        sampleZ = lz + 1.0
                    }
                }

                val sampleWorldY = worldYAtLocal(sampleX, sampleY, sampleZ)
                if (sampleWorldY > bestY) bestY = sampleWorldY
            }

            return if (bestY.isFinite()) bestY else fallback
        }

        fun sampleOpeningFaceCoverageSnapshot(
            curIdx: Int,
            lx: Int,
            ly: Int,
            lz: Int,
            nIdx: Int,
            outDirCode: Int,
            componentMaskCur: Long = -1L,
            componentMaskNeighbor: Long = -1L,
        ): OpeningFaceCoverageSnapshot {
            val faceTopY = openingFaceTopWorldY(
                curIdx = curIdx,
                lx = lx,
                ly = ly,
                lz = lz,
                nIdx = nIdx,
                outDirCode = outDirCode,
                componentMaskCur = componentMaskCur,
                componentMaskNeighbor = componentMaskNeighbor,
            )

            return withBypassedFluidOverrides {
                val sampledFluids = arrayOfNulls<Fluid>(5)
                val sampledCounts = IntArray(5)
                var sampledFluidCount = 0
                var submergedSamples = 0
                var centerFluid: Fluid? = null

                val faceOffset = 1.0e-4
                val lo = 1.0e-4
                val hi = 1.0 - lo

                fun sampleAt(u: Double, v: Double, isCenter: Boolean) {
                    val localX: Double
                    val localY: Double
                    val localZ: Double
                    when (outDirCode) {
                        0 -> {
                            localX = lx - faceOffset
                            localY = ly + u
                            localZ = lz + v
                        }
                        1 -> {
                            localX = lx + 1.0 + faceOffset
                            localY = ly + u
                            localZ = lz + v
                        }
                        2 -> {
                            localX = lx + u
                            localY = ly - faceOffset
                            localZ = lz + v
                        }
                        3 -> {
                            localX = lx + u
                            localY = ly + 1.0 + faceOffset
                            localZ = lz + v
                        }
                        4 -> {
                            localX = lx + u
                            localY = ly + v
                            localZ = lz - faceOffset
                        }
                        else -> {
                            localX = lx + u
                            localY = ly + v
                            localZ = lz + 1.0 + faceOffset
                        }
                    }

                    val fluid = sampleCanonicalWorldFluidAtShipPoint(
                        level = level,
                        shipTransform = shipTransform,
                        shipX = state.minX + localX,
                        shipY = state.minY + localY,
                        shipZ = state.minZ + localZ,
                        currentShipState = state,
                        shipPosTmp = shipPosTmp,
                        worldPosTmp = worldPosTmp,
                        worldBlockPos = worldBlockPos,
                        queryCache = queryCache,
                        clientWorldQueryBounds = clientWorldQueryBounds,
                    )
                    if (isCenter) centerFluid = fluid
                    if (fluid == null) return

                    submergedSamples++
                    for (i in 0 until sampledFluidCount) {
                        if (sampledFluids[i] == fluid) {
                            sampledCounts[i]++
                            return
                        }
                    }
                    if (sampledFluidCount < sampledFluids.size) {
                        sampledFluids[sampledFluidCount] = fluid
                        sampledCounts[sampledFluidCount] = 1
                        sampledFluidCount++
                    } else {
                        sampledCounts[0]++
                    }
                }

                sampleAt(0.5, 0.5, isCenter = true)
                sampleAt(lo, lo, isCenter = false)
                sampleAt(hi, lo, isCenter = false)
                sampleAt(lo, hi, isCenter = false)
                sampleAt(hi, hi, isCenter = false)

                var bestFluid: Fluid? = null
                var bestCount = 0
                for (i in 0 until sampledFluidCount) {
                    val fluid = sampledFluids[i] ?: continue
                    val count = sampledCounts[i]
                    if (count > bestCount || (count == bestCount && centerFluid != null && fluid == centerFluid)) {
                        bestCount = count
                        bestFluid = fluid
                    }
                }

                val ratio = if (submergedSamples <= 0 || bestCount <= 0) 0.0 else (bestCount / 5.0).coerceIn(0.0, 1.0)
                val centerSubmerged = centerFluid != null && bestFluid != null && centerFluid == bestFluid
                val centerLocalX: Double
                val centerLocalY: Double
                val centerLocalZ: Double
                when (outDirCode) {
                    0 -> {
                        centerLocalX = lx - faceOffset
                        centerLocalY = ly + 0.5
                        centerLocalZ = lz + 0.5
                    }
                    1 -> {
                        centerLocalX = lx + 1.0 + faceOffset
                        centerLocalY = ly + 0.5
                        centerLocalZ = lz + 0.5
                    }
                    2 -> {
                        centerLocalX = lx + 0.5
                        centerLocalY = ly - faceOffset
                        centerLocalZ = lz + 0.5
                    }
                    3 -> {
                        centerLocalX = lx + 0.5
                        centerLocalY = ly + 1.0 + faceOffset
                        centerLocalZ = lz + 0.5
                    }
                    4 -> {
                        centerLocalX = lx + 0.5
                        centerLocalY = ly + 0.5
                        centerLocalZ = lz - faceOffset
                    }
                    else -> {
                        centerLocalX = lx + 0.5
                        centerLocalY = ly + 0.5
                        centerLocalZ = lz + 1.0 + faceOffset
                    }
                }

                val estimatedSurfaceY = if (bestFluid != null) {
                    estimateExteriorFluidSurfaceYAtShipPoint(
                        level = level,
                        shipTransform = shipTransform,
                        shipX = state.minX + centerLocalX,
                        shipY = state.minY + centerLocalY,
                        shipZ = state.minZ + centerLocalZ,
                        sampleFluid = bestFluid,
                        currentShipState = state,
                        shipPosTmp = shipPosTmp,
                        worldPosTmp = worldPosTmp,
                        worldBlockPos = worldBlockPos,
                        queryCache = queryCache,
                        clientWorldQueryBounds = clientWorldQueryBounds,
                    )
                } else {
                    null
                }

                OpeningFaceCoverageSnapshot(
                    canonicalFluid = bestFluid,
                    coverageRatio = ratio,
                    centerSubmerged = centerSubmerged,
                    faceTopWorldY = faceTopY,
                    estimatedSurfaceY = estimatedSurfaceY,
                )
            }
        }

        val openingFaceSamples = Long2ObjectOpenHashMap<OpeningFaceCoverageSnapshot>()
        val simulationDomain = state.simulationDomain
        val hasComponentConnectivity = hasComponentTraversalSupport(state)
        val strideY = sizeX
        val strideZ = sizeX * sizeY

        fun tryRegisterOpening(curIdx: Int, nIdx: Int, dirCode: Int, lx: Int, ly: Int, lz: Int) {
            if (!open.get(nIdx)) return
            // Only capture samples for *true* outside openings (simulationDomain -> outsideVoid).
            if (simulationDomain.get(nIdx)) return
            if (!state.outsideVoid.get(nIdx)) return
            var curMaskForSample = -1L
            var nMaskForSample = -1L
            val conductance = if (hasComponentConnectivity) {
                val curMask = simulationComponentMaskAt(state, curIdx)
                val nMask = exteriorComponentMaskAt(state, nIdx)
                curMaskForSample = curMask
                nMaskForSample = nMask
                computeFilteredFaceConductance(
                    state = state,
                    idxA = curIdx,
                    idxB = nIdx,
                    dirCode = dirCode,
                    componentMaskA = curMask,
                    componentMaskB = nMask,
                )
            } else {
                edgeConductance(state, curIdx, lx, ly, lz, dirCode)
            }
            if (conductance <= 0) return
            if (conductance < MIN_OPENING_CONDUCTANCE) {
                microOpeningFilteredCount.incrementAndGet()
                return
            }
            val key = faceKey(curIdx, dirCode)
            if (!openingFaceSamples.containsKey(key)) {
                openingFaceSamples.put(
                    key,
                    sampleOpeningFaceCoverageSnapshot(
                        curIdx = curIdx,
                        lx = lx,
                        ly = ly,
                        lz = lz,
                        nIdx = nIdx,
                        outDirCode = dirCode,
                        componentMaskCur = curMaskForSample,
                        componentMaskNeighbor = nMaskForSample,
                    )
                )
            }
        }

        var cur = simulationDomain.nextSetBit(0)
        while (cur >= 0 && cur < volume) {
            if (open.get(cur)) {
                val lx = cur % sizeX
                val t = cur / sizeX
                val ly = t % sizeY
                val lz = t / sizeY

                if (lx > 0) tryRegisterOpening(cur, cur - 1, 0, lx, ly, lz)
                if (lx + 1 < sizeX) tryRegisterOpening(cur, cur + 1, 1, lx, ly, lz)
                if (ly > 0) tryRegisterOpening(cur, cur - strideY, 2, lx, ly, lz)
                if (ly + 1 < sizeY) tryRegisterOpening(cur, cur + strideY, 3, lx, ly, lz)
                if (lz > 0) tryRegisterOpening(cur, cur - strideZ, 4, lx, ly, lz)
                if (lz + 1 < sizeZ) tryRegisterOpening(cur, cur + strideZ, 5, lx, ly, lz)
            }
            cur = simulationDomain.nextSetBit(cur + 1)
        }

        return WaterSolveSnapshot(
            generation = generation,
            geometryRevision = state.geometryRevision,
            captureTick = captureTick,
            transformKey = transformKeyValue,
            minX = state.minX,
            minY = state.minY,
            minZ = state.minZ,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            open = state.open,
            interior = state.simulationDomain,
            exterior = state.exterior,
            outsideVoid = state.outsideVoid.clone() as BitSet,
            materializedWater = state.materializedWater.clone() as BitSet,
            floodFluid = state.floodFluid,
            faceCondXP = state.faceCondXP,
            faceCondYP = state.faceCondYP,
            faceCondZP = state.faceCondZP,
            templatePalette = state.shapeTemplatePalette,
            templateIndexByVoxel = state.templateIndexByVoxel,
            voxelExteriorComponentMask = state.voxelExteriorComponentMask,
            voxelInteriorComponentMask = state.voxelSimulationComponentMask,
            submerged = submerged,
            submergedCoverage = submergedCoverage.copyOf(volume),
            buoyancySubmergedCoverage = buoyancySubmergedCoverage,
            dominantFloodFluid = dominantFloodFluid,
            surfaceYByCell = surfaceYByCell.copyOf(volume),
            openingFaceSamples = openingFaceSamples,
            baseWorldY = affine.baseWorldY,
            incX = affine.incX,
            incY = affine.incY,
            incZ = affine.incZ,
        )
    }

    private fun trySubmitWaterSolveJob(
        level: Level,
        state: ShipPocketState,
        shipTransform: ShipTransform,
        captureTick: Long,
        transformKeyOverride: Long? = null,
        clientWorldQueryBounds: ClientWorldChunkQueryBounds? = null,
    ): Boolean {
        val pending = state.pendingWaterSolveFuture
        if (pending != null && !pending.isDone) {
            state.waterSolveJobInFlight = true
            return false
        }

        val currentTransformKey = transformKeyOverride ?: transformKey(
            minX = state.minX,
            minY = state.minY,
            minZ = state.minZ,
            shipTransform = shipTransform,
            shipPosTmp = tmpShipPos2.get(),
            worldPosTmp = tmpWorldPos2.get(),
        )
        if (!level.isClientSide &&
            state.lastWaterSolveSubmitTick != Long.MIN_VALUE &&
            captureTick - state.lastWaterSolveSubmitTick < SERVER_WATER_SOLVE_MIN_RESUBMIT_TICKS &&
            currentTransformKey == state.lastWaterSolveSubmittedTransformKey
        ) {
            return false
        }

        val generation = state.requestedWaterSolveGeneration + 1L
        state.requestedWaterSolveGeneration = generation

        val snapshot = try {
            captureWaterSolveSnapshot(
                level = level,
                state = state,
                shipTransform = shipTransform,
                generation = generation,
                captureTick = captureTick,
                transformKeyOverride = currentTransformKey,
                clientWorldQueryBounds = clientWorldQueryBounds,
            )
        } catch (t: Throwable) {
            val count = waterSolveJobsFailed.incrementAndGet()
            logThrottledDiag(count, "Failed to capture water solve snapshot", t)
            return false
        } ?: return false

        val submittedFuture = ShipPocketAsyncRuntime.trySubmit(
            subsystem = ShipPocketAsyncSubsystem.WATER_SOLVER,
            task = { computeWaterSolveAsync(snapshot) },
        )
        if (submittedFuture == null) {
            val count = asyncQueueFullSkips.incrementAndGet()
            logThrottledDiag(
                count,
                "Skipped water solver submit: async queue full pending={} max={}",
                ShipPocketAsyncRuntime.pendingJobCount(),
                ShipPocketAsyncRuntime.maxPendingJobs(),
            )
            return false
        }

        state.pendingWaterSolveFuture = submittedFuture
        state.waterSolveJobInFlight = true
        state.lastWaterSolveSubmitTick = captureTick
        state.lastWaterSolveSubmittedTransformKey = snapshot.transformKey
        if (level.isClientSide) {
            state.lastClientWaterSolveSubmittedTransformKey = snapshot.transformKey
        }

        val count = waterSolveJobsSubmitted.incrementAndGet()
        logThrottledDiag(
            count,
            "Submitted water solve job gen={} geomRev={} tick={}",
            generation,
            snapshot.geometryRevision,
            snapshot.captureTick,
        )
        return true
    }

    private fun applyWaterSolveResult(
        state: ShipPocketState,
        result: WaterSolveResult,
        appliedTick: Long,
        isClientSide: Boolean = false,
    ) {
        state.waterReachable = result.waterReachable
        state.unreachableVoid = result.unreachableVoid
        state.buoyancy.submergedAirVolume = result.buoyancy.submergedAirVolume
        state.buoyancy.submergedAirSumX = result.buoyancy.submergedAirSumX
        state.buoyancy.submergedAirSumY = result.buoyancy.submergedAirSumY
        state.buoyancy.submergedAirSumZ = result.buoyancy.submergedAirSumZ

        val floodFluid = result.floodFluid
        if (floodFluid != null) {
            val canonical = canonicalFloodSource(floodFluid)
            if (canonical != state.floodFluid) {
                state.floodFluid = canonical
                state.dirty = true
            }
        }

        state.appliedWaterSolveGeneration = result.generation
        state.waterSolveLastComputeNanos = result.computeNanos
        state.waterSolveComputeCount++
        state.lastWaterReachableUpdateTick = appliedTick
        state.lastWaterSolveApplyTick = appliedTick
        state.lastWaterSolveAppliedTransformKey = result.transformKey
        if (isClientSide) {
            state.lastClientWaterSolveApplyTick = appliedTick
            state.lastClientWaterSolveAppliedTransformKey = result.transformKey
        }
        state.consecutiveWaterSolveDiscards = 0
        state.persistDirty = true
    }

    private fun tryApplyCompletedWaterSolveJob(
        state: ShipPocketState,
        nowTick: Long,
        shipTransform: ShipTransform,
        isClientSide: Boolean = false,
        currentTransformKeyOverride: Long? = null,
    ): Boolean {
        val future = state.pendingWaterSolveFuture ?: return false
        if (!future.isDone) return false

        state.pendingWaterSolveFuture = null
        state.waterSolveJobInFlight = false

        val result = try {
            future.join()
        } catch (t: Throwable) {
            val root = t.cause ?: t
            val count = waterSolveJobsFailed.incrementAndGet()
            state.consecutiveWaterSolveDiscards++
            logThrottledDiag(count, "Ship pocket water solve job failed", root)
            return false
        }

        if (result.generation != state.requestedWaterSolveGeneration ||
            result.geometryRevision != state.geometryRevision
        ) {
            ShipPocketAsyncRuntime.noteDiscard(ShipPocketAsyncSubsystem.WATER_SOLVER)
            val count = waterSolveJobsDiscarded.incrementAndGet()
            state.consecutiveWaterSolveDiscards++
            logThrottledDiag(
                count,
                "Discarded stale water solve result gen={} currentGen={} resultGeomRev={} currentGeomRev={}",
                result.generation,
                state.requestedWaterSolveGeneration,
                result.geometryRevision,
                state.geometryRevision,
            )
            return false
        }
        val age = nowTick - result.captureTick
        if (age > MAX_WATER_SOLVE_RESULT_AGE_TICKS) {
            ShipPocketAsyncRuntime.noteDiscard(ShipPocketAsyncSubsystem.WATER_SOLVER)
            waterSolveJobsDiscarded.incrementAndGet()
            val ageCount = waterSolveAgeDiscardCount.incrementAndGet()
            state.consecutiveWaterSolveDiscards++
            logThrottledDiag(
                ageCount,
                "Discarded stale-aged water solve result gen={} nowTick={} captureTick={}",
                result.generation,
                nowTick,
                result.captureTick,
            )
            return false
        }

        run {
            val shipPosTmp = tmpShipPos2.get()
            val worldPosTmp = tmpWorldPos2.get()
            val currentKey = currentTransformKeyOverride ?: transformKey(
                minX = state.minX,
                minY = state.minY,
                minZ = state.minZ,
                shipTransform = shipTransform,
                shipPosTmp = shipPosTmp,
                worldPosTmp = worldPosTmp,
            )
            if (result.transformKey != currentKey) {
                ShipPocketAsyncRuntime.noteDiscard(ShipPocketAsyncSubsystem.WATER_SOLVER)
                val mismatchCount = waterSolveTransformMismatchDiscardCount.incrementAndGet()
                waterSolveJobsDiscarded.incrementAndGet()
                state.consecutiveWaterSolveDiscards++
                logThrottledDiag(
                    mismatchCount,
                    "Discarded water solve due to transform mismatch gen={} resultKey={} currentKey={}",
                    result.generation,
                    result.transformKey,
                    currentKey,
                )
                return false
            }
        }

        applyWaterSolveResult(state, result, appliedTick = nowTick, isClientSide = isClientSide)
        val ageBucket = when {
            age < 0L -> 0
            age >= 5L -> 5
            else -> age.toInt()
        }
        waterSolveApplyAgeBuckets[ageBucket].incrementAndGet()
        waterSolveComputeNanosTotal.addAndGet(result.computeNanos)
        val completed = waterSolveJobsCompleted.incrementAndGet()
        val avgMs = waterSolveComputeNanosTotal.get().toDouble() / completed.toDouble() / 1_000_000.0
        logThrottledDiag(
            completed,
            "Applied water solve gen={} computeMs={} avgComputeMs={}",
            result.generation,
            result.computeNanos.toDouble() / 1_000_000.0,
            avgMs,
        )
        return true
    }

    @JvmStatic
    fun tickServerLevel(level: ServerLevel) {
        if (!VSGameConfig.COMMON.enableAirPockets) return

        val states = serverStates.computeIfAbsent(level.dimensionId) { ConcurrentHashMap() }
        val loadedShipIds = LongOpenHashSet()
        var remainingGeometrySubmissions = GEOMETRY_ASYNC_SUBMISSIONS_PER_LEVEL_PER_TICK
        var remainingWaterSolveSubmissions = WATER_SOLVER_ASYNC_SUBMISSIONS_PER_LEVEL_PER_TICK
        var remainingWaterSolveSyncFallbacks = MAX_SYNC_WATER_SOLVE_PER_LEVEL_PER_TICK

        level.shipObjectWorld.loadedShips.forEach { ship ->
            loadedShipIds.add(ship.id)
            val state = states[ship.id] ?: run {
                val restored = loadPersistedServerState(level, ship.id) ?: ShipPocketState()
                states[ship.id] = restored
                restored
            }

            val aabb = ship.shipAABB ?: return@forEach
            val baseMinX = aabb.minX()
            val baseMinY = aabb.minY()
            val baseMinZ = aabb.minZ()
            val baseSizeX = aabb.maxX() - baseMinX
            val baseSizeY = aabb.maxY() - baseMinY
            val baseSizeZ = aabb.maxZ() - baseMinZ

            val minX = baseMinX - POCKET_BOUNDS_PADDING
            val minY = baseMinY - POCKET_BOUNDS_PADDING
            val minZ = baseMinZ - POCKET_BOUNDS_PADDING
            val sizeX = baseSizeX + POCKET_BOUNDS_PADDING * 2
            val sizeY = baseSizeY + POCKET_BOUNDS_PADDING * 2
            val sizeZ = baseSizeZ + POCKET_BOUNDS_PADDING * 2
            val volume = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
            if (volume <= 0 || volume > MAX_SIM_VOLUME.toLong()) {
                if (state.dirty) {
                    log.warn("Skipping ship water pockets for ship {} (volume={}, max={})", ship.id, volume, MAX_SIM_VOLUME)
                    state.dirty = false
                }
                state.pendingGeometryFuture?.cancel(true)
                state.pendingGeometryFuture = null
                state.geometryJobInFlight = false
                state.pendingWaterSolveFuture?.cancel(true)
                state.pendingWaterSolveFuture = null
                state.waterSolveJobInFlight = false
                clearFloodWriteQueues(state)
                flushPersistedServerState(
                    level = level,
                    shipId = ship.id,
                    state = state,
                    force = true,
                    nowTick = level.gameTime,
                )
                return@forEach
            }

            val geometryApplied = tryApplyCompletedGeometryJob(
                level = level,
                state = state,
                minX = minX,
                minY = minY,
                minZ = minZ,
                sizeX = sizeX,
                sizeY = sizeY,
                sizeZ = sizeZ,
                preserveLiveFloodState = true,
            )
            val shipChunksLoaded = areShipyardChunksLoaded(level, baseMinX, baseMinY, baseMinZ, baseSizeX, baseSizeY, baseSizeZ)
            if (state.componentGraphDegraded) {
                val count = componentTraversalOverflowCount.incrementAndGet()
                logThrottledDiag(count, "Component graph degraded for shipId={} (running coarse fallback paths)", ship.id)
            }

            var needsRecompute = state.dirty || boundsMismatch(state, minX, minY, minZ, sizeX, sizeY, sizeZ)
            if (needsRecompute) {
                // When (re)loading a ship, the shipyard chunks can arrive a few ticks after the ship object itself.
                // If we recompute while those chunks are still unloaded, `getBlockState` returns air everywhere, which
                // makes the ship appear entirely "open" and disables all air pockets until another shipyard block
                // update marks the ship dirty again.
                if (!shipChunksLoaded) {
                    state.dirty = true
                } else if (remainingGeometrySubmissions > 0 &&
                    trySubmitGeometryJob(level, state, minX, minY, minZ, sizeX, sizeY, sizeZ)
                ) {
                    remainingGeometrySubmissions--
                }
            }

            if (state.awaitingGeometryValidation && !shipChunksLoaded) {
                clearFloodWriteQueues(state)
                return@forEach
            }

            val restoredStateUsable = isRestoredStateStructurallyUsableForBounds(
                state = state,
                minX = minX,
                minY = minY,
                minZ = minZ,
                sizeX = sizeX,
                sizeY = sizeY,
                sizeZ = sizeZ,
            )

            if (state.awaitingGeometryValidation && !geometryApplied) {
                return@forEach
            }

            if (restoredStateUsable) {
                ensureOutsideVoidMask(state)
            }

            val now = level.gameTime
            val shipTransform = getQueryTransform(ship)
            val worldChunksLoaded = areWorldChunksLoadedForShipBounds(level, shipTransform, minX, minY, minZ, sizeX, sizeY, sizeZ)
            val floodingChunksReady = shipChunksLoaded && worldChunksLoaded
            if (!shipChunksLoaded) {
                clearFloodWriteQueues(state)
            } else if (!worldChunksLoaded) {
                clearQueuedFloodAdds(state)
            }

            val pendingWaterSolve = state.pendingWaterSolveFuture
            if (pendingWaterSolve != null &&
                !pendingWaterSolve.isDone &&
                state.lastWaterSolveSubmitTick != Long.MIN_VALUE
            ) {
                val pendingAge = now - state.lastWaterSolveSubmitTick
                if (pendingAge > MAX_WATER_SOLVE_RESULT_AGE_TICKS + WATER_SOLVE_PENDING_CANCEL_EXTRA_AGE_TICKS) {
                    pendingWaterSolve.cancel(true)
                    state.pendingWaterSolveFuture = null
                    state.waterSolveJobInFlight = false
                    state.consecutiveWaterSolveDiscards++
                    val cancelCount = waterSolveCancelledOverAgeCount.incrementAndGet()
                    logThrottledDiag(
                        cancelCount,
                        "Cancelled over-age pending water solve shipId={} pendingAge={} submitTick={} nowTick={}",
                        ship.id,
                        pendingAge,
                        state.lastWaterSolveSubmitTick,
                        now,
                    )
                }
            }

            // If ship rotation changes the discrete shipyard "down" direction, wake up any fluids that are
            // sitting still in the shipyard so they can reflow under the new gravity.
            val gravityDown = computeShipGravityDownDir(shipTransform)
            val lastGravity = state.lastGravityDownDir
            var gravityDirectionChanged = false
            if (lastGravity == null) {
                state.lastGravityDownDir = gravityDown
            } else if (lastGravity != gravityDown) {
                state.lastGravityDownDir = gravityDown
                state.pendingGravityResettleNextIdx = 0
                gravityDirectionChanged = true
            }
            if (shipChunksLoaded) {
                tickGravityResettle(level, state)
            }

            var waterSolveUpdated = false
            if (floodingChunksReady) {
                waterSolveUpdated = tryApplyCompletedWaterSolveJob(state, now, shipTransform, isClientSide = false)

                if ((geometryApplied || now != state.lastWaterReachableUpdateTick) &&
                    state.sizeX > 0 &&
                    state.sizeY > 0 &&
                    state.sizeZ > 0 &&
                    state.sizeX.toLong() * state.sizeY.toLong() * state.sizeZ.toLong() <= MAX_SIM_VOLUME.toLong()
                ) {
                    if (remainingWaterSolveSubmissions > 0 &&
                        trySubmitWaterSolveJob(level, state, shipTransform, now)
                    ) {
                        remainingWaterSolveSubmissions--
                    }

                    val staleTicks = if (state.lastWaterSolveApplyTick != Long.MIN_VALUE) {
                        now - state.lastWaterSolveApplyTick
                    } else if (state.lastWaterSolveSubmitTick != Long.MIN_VALUE) {
                        now - state.lastWaterSolveSubmitTick
                    } else {
                        Long.MAX_VALUE
                    }
                    if (!waterSolveUpdated &&
                        staleTicks >= WATER_SOLVE_STARVATION_SYNC_FALLBACK_TICKS &&
                        remainingWaterSolveSyncFallbacks > 0
                    ) {
                        val generation = state.requestedWaterSolveGeneration + 1L
                        val snapshot = captureWaterSolveSnapshot(
                            level = level,
                            state = state,
                            shipTransform = shipTransform,
                            generation = generation,
                            captureTick = now,
                        )
                        if (snapshot != null) {
                            state.pendingWaterSolveFuture?.cancel(true)
                            state.pendingWaterSolveFuture = null
                            state.waterSolveJobInFlight = false
                            state.requestedWaterSolveGeneration = generation
                            state.lastWaterSolveSubmitTick = now
                            val result = computeWaterSolveAsync(snapshot)
                            applyWaterSolveResult(state, result, appliedTick = now, isClientSide = false)
                            remainingWaterSolveSyncFallbacks--
                            waterSolveUpdated = true

                            val fallbackCount = waterSolveSyncFallbacks.incrementAndGet()
                            waterSolveStarvationFallbackCount.incrementAndGet()
                            logThrottledDiag(
                                fallbackCount,
                                "Ran synchronous water solve fallback generation={} shipId={}",
                                generation,
                                ship.id,
                            )
                        }
                    }
                }
            }
            if (waterSolveUpdated) {
                updateVsBuoyancyFromPockets(ship, state)
                if (state.buoyancy.submergedAirVolume > 1.0e-6) {
                    val count = buoyancyUpdatedCount.incrementAndGet()
                    logThrottledDiag(
                        count,
                        "Updated pocket buoyancy shipId={} displaced={} floodFluid={} applyTick={}",
                        ship.id,
                        state.buoyancy.submergedAirVolume,
                        state.floodFluid,
                        state.lastWaterSolveApplyTick,
                    )
                }
            } else if (state.buoyancy.submergedAirVolume > 1.0e-6 && !floodingChunksReady) {
                val count = buoyancyRefreshSkippedCount.incrementAndGet()
                logThrottledDiag(
                    count,
                    "Pocket buoyancy refresh skipped shipId={} displaced={} shipChunksLoaded={} worldChunksLoaded={} dirty={} waterSolveInFlight={}",
                    ship.id,
                    state.buoyancy.submergedAirVolume,
                    shipChunksLoaded,
                    worldChunksLoaded,
                    state.dirty,
                    state.waterSolveJobInFlight,
                )
            }
            if (shipChunksLoaded &&
                state.sizeX > 0 && state.sizeY > 0 && state.sizeZ > 0 &&
                (geometryApplied || now - state.lastMaterializedResyncTick >= MATERIALIZED_RESYNC_INTERVAL_TICKS)
            ) {
                syncMaterializedFloodFluidFromWorld(level, state)
                state.lastMaterializedResyncTick = now
            }
            if (shipChunksLoaded) {
                cleanupLeakedShipyardWater(level, state)
            }
            needsRecompute = state.dirty || boundsMismatch(state, minX, minY, minZ, sizeX, sizeY, sizeZ)
            if (floodingChunksReady &&
                (geometryApplied || needsRecompute || now - state.lastFloodUpdateTick >= FLOOD_UPDATE_INTERVAL_TICKS) &&
                state.sizeX > 0 &&
                state.sizeY > 0 &&
                state.sizeZ > 0
            ) {
                if (gravityDirectionChanged) {
                    redistributeMaterializedWaterForGravity(level, state, shipTransform)
                }
                updateFlooding(level, state, shipTransform)
                state.lastFloodUpdateTick = now
            }

            if (shipChunksLoaded) {
                val floodAddCap = if (floodingChunksReady) {
                    val progressRate = computeFloodProgressRateModel(
                        level = level,
                        floodFluid = state.floodFluid,
                        openingConductanceUnits = state.activeFloodIngressConductanceUnits,
                        openingCount = state.activeFloodIngressPoints.coerceIn(1, MAX_VIRTUAL_INGRESS_FRONTS),
                    )
                    if (now % progressRate.fluidTickDelay.toLong() == 0L) {
                        progressRate.frontierBudget
                    } else {
                        0
                    }
                } else {
                    0
                }
                val flushQueryCache = tmpChunkQueryCache.get().apply { reset() }

                val flushResult = flushFloodWriteQueue(
                    level = level,
                    state = state,
                    shipTransform = shipTransform,
                    addCap = floodAddCap,
                    setApplyingInternalUpdates = { applyingInternalUpdates = it },
                    isFloodFluidType = { fluid -> canonicalFloodSource(fluid) == state.floodFluid },
                    isExteriorFloodSeedReady = { pos, transform, shipPosTmp, worldPosTmp, worldBlockPos ->
                        val submergedSample = getShipCellFluidCoverage(
                            level = level,
                            shipTransform = transform,
                            shipBlockPos = pos,
                            shipPosTmp = shipPosTmp,
                            worldPosTmp = worldPosTmp,
                            worldBlockPos = worldBlockPos,
                            queryCache = flushQueryCache,
                        )
                        val submergedFluid = submergedSample.canonicalFluid
                        submergedSample.isIngressQualified() &&
                            submergedFluid != null &&
                            canonicalFloodSource(submergedFluid) == state.floodFluid
                    },
                    queryCache = flushQueryCache,
                )
                if (flushResult.rejectedAdds > 0) {
                    val count = rejectedFloodQueueAdds.addAndGet(flushResult.rejectedAdds.toLong())
                    logThrottledDiag(count, "Rejected flood-queue adds lacking ingress/simulation-domain validation")
                }
                if (flushResult.blockedExteriorWaterlogs > 0) {
                    val count = blockedExteriorWaterlogAttempts.addAndGet(flushResult.blockedExteriorWaterlogs.toLong())
                    logThrottledDiag(count, "Blocked exterior waterlogging attempts during flood queue flush")
                }
                if (flushResult.addedSampleIndices.isNotEmpty()) {
                    spawnIngressParticlesServer(level, state, shipTransform, flushResult.addedSampleIndices)
                }
                if (flushResult.added > 0 || flushResult.removed > 0) {
                    state.persistDirty = true
                }
                if (flushResult.removed > 0 && !state.materializedWater.isEmpty) {
                    state.pendingGravityResettleNextIdx = 0
                }
                if (flushResult.remainingQueued > 0) {
                    while (true) {
                        val prev = floodQueueBacklogHighWater.get()
                        val nowBacklog = flushResult.remainingQueued.toLong()
                        if (nowBacklog <= prev) break
                        if (floodQueueBacklogHighWater.compareAndSet(prev, nowBacklog)) {
                            logThrottledDiag(
                                nowBacklog,
                                "New ship flood queue high-water backlog={}",
                                nowBacklog,
                            )
                            break
                        }
                    }
                }
            }

            flushPersistedServerState(
                level = level,
                shipId = ship.id,
                state = state,
                force = false,
                nowTick = now,
            )
        }

        // Cleanup unloaded ships
        states.entries.removeIf { entry ->
            if (loadedShipIds.contains(entry.key)) return@removeIf false
            flushPersistedServerState(
                level = level,
                shipId = entry.key,
                state = entry.value,
                force = true,
                nowTick = level.gameTime,
            )
            entry.value.pendingGeometryFuture?.cancel(true)
            entry.value.pendingGeometryFuture = null
            entry.value.geometryJobInFlight = false
            entry.value.pendingWaterSolveFuture?.cancel(true)
            entry.value.pendingWaterSolveFuture = null
            entry.value.waterSolveJobInFlight = false
            true
        }

        if (log.isDebugEnabled && level.gameTime % ASYNC_DIAG_SUMMARY_INTERVAL_TICKS == 0L) {
            log.debug(
                "Async water-solver diag: submitted={}, completed={}, discarded={}, ageDiscard={}, overAgeCancel={}, starvationFallbacks={}, applyAgeBuckets=[{}, {}, {}, {}, {}, {}], pending={}, blockedExteriorWaterlogs={}, blockedExteriorPlacements={}, rejectedQueueAdds={}, missingFaceSamples={}, suppressionHits={}",
                waterSolveJobsSubmitted.get(),
                waterSolveJobsCompleted.get(),
                waterSolveJobsDiscarded.get(),
                waterSolveAgeDiscardCount.get(),
                waterSolveCancelledOverAgeCount.get(),
                waterSolveStarvationFallbackCount.get(),
                waterSolveApplyAgeBuckets[0].get(),
                waterSolveApplyAgeBuckets[1].get(),
                waterSolveApplyAgeBuckets[2].get(),
                waterSolveApplyAgeBuckets[3].get(),
                waterSolveApplyAgeBuckets[4].get(),
                waterSolveApplyAgeBuckets[5].get(),
                ShipPocketAsyncRuntime.pendingJobCount(),
                blockedExteriorWaterlogAttempts.get(),
                blockedExteriorPlacementAttempts.get(),
                rejectedFloodQueueAdds.get(),
                asyncOpeningFaceFallbackCount.get(),
                worldSuppressionHits.get(),
            )
        }
    }

    private fun computeShipGravityDownDir(shipTransform: ShipTransform): Direction {
        val rot = shipTransform.shipToWorldRotation
        val v = tmpShipGravityVec.get()

        v.set(0.0, 1.0, 0.0)
        rot.transform(v)
        val yY = v.y

        v.set(1.0, 0.0, 0.0)
        rot.transform(v)
        val yX = v.y

        v.set(0.0, 0.0, 1.0)
        rot.transform(v)
        val yZ = v.y

        var best = Direction.DOWN
        var bestY = -yY // local -Y

        if (yY < bestY) {
            bestY = yY
            best = Direction.UP
        }
        if (yX < bestY) {
            bestY = yX
            best = Direction.EAST
        }
        if (-yX < bestY) {
            bestY = -yX
            best = Direction.WEST
        }
        if (yZ < bestY) {
            bestY = yZ
            best = Direction.SOUTH
        }
        if (-yZ < bestY) {
            best = Direction.NORTH
        }

        return best
    }

    private fun tickGravityResettle(level: ServerLevel, state: ShipPocketState) {
        val nextIdx = state.pendingGravityResettleNextIdx
        if (nextIdx < 0) return

        val fluidBlocks = state.materializedWater
        if (fluidBlocks.isEmpty) {
            state.pendingGravityResettleNextIdx = -1
            return
        }

        val pos = BlockPos.MutableBlockPos()
        var idx = fluidBlocks.nextSetBit(nextIdx)
        var scheduled = 0
        while (idx >= 0 && scheduled < GRAVITY_RESETTLE_MAX_SCHEDULED_TICKS_PER_SHIP_PER_TICK) {
            posFromIndex(state, idx, pos)
            val fs = level.getFluidState(pos)
            if (!fs.isEmpty) {
                level.scheduleTick(pos, fs.type, 1)
                scheduled++
            }
            idx = fluidBlocks.nextSetBit(idx + 1)
        }

        state.pendingGravityResettleNextIdx = idx
    }

    private fun cleanupLeakedShipyardWater(level: ServerLevel, state: ShipPocketState) {
        if (state.materializedWater.isEmpty) return

        val toRemove = tmpLeakedWaterToRemove.get()
        toRemove.clear()
        toRemove.or(state.materializedWater)
        // Only purge cells that are no longer open (e.g. stale writes into solids).
        // Keep open-cell water managed by the drain solver even if interior classification changes this tick.
        toRemove.andNot(state.open)

        if (toRemove.isEmpty) return
        applyBlockChanges(level, state, toRemove, toWater = false, pos = BlockPos.MutableBlockPos())
    }

    private fun syncMaterializedFloodFluidFromWorld(level: ServerLevel, state: ShipPocketState) {
        val open = state.open
        val volume = state.sizeX * state.sizeY * state.sizeZ
        if (volume <= 0 || open.isEmpty) {
            state.materializedWater.clear()
            state.externalFluid.clear()
            state.flooded.clear()
            state.brokenByFlood.clear()
            state.drainSuppressed.clear()
            state.persistDirty = true
            return
        }

        val materialized = state.materializedWater
        val externalFluid = state.externalFluid
        val flooded = state.flooded
        val brokenByFlood = state.brokenByFlood
        val drainSuppressed = state.drainSuppressed
        val beforeMaterialized = materialized.clone() as BitSet
        val beforeFlooded = flooded.clone() as BitSet
        materialized.and(open)
        externalFluid.and(open)
        flooded.and(open)
        flooded.and(state.simulationDomain)
        brokenByFlood.and(open)
        brokenByFlood.and(state.simulationDomain)
        drainSuppressed.and(open)
        drainSuppressed.and(state.simulationDomain)
        var changed = beforeMaterialized != materialized || beforeFlooded != flooded
        var internalUpdatesActive = false

        fun beginInternalUpdates() {
            if (!internalUpdatesActive) {
                applyingInternalUpdates = true
                internalUpdatesActive = true
            }
        }

        fun touchesFloodOwnedFluid(idx: Int): Boolean {
            val lx = idx % state.sizeX
            val t = idx / state.sizeX
            val ly = t % state.sizeY
            val lz = t / state.sizeY
            val strideY = state.sizeX
            val strideZ = state.sizeX * state.sizeY

            fun owned(n: Int): Boolean = n in 0 until volume &&
                state.open.get(n) &&
                (materialized.get(n) || flooded.get(n) || state.queuedFloodAdds.get(n))

            return (lx > 0 && owned(idx - 1)) ||
                (lx + 1 < state.sizeX && owned(idx + 1)) ||
                (ly > 0 && owned(idx - strideY)) ||
                (ly + 1 < state.sizeY && owned(idx + strideY)) ||
                (lz > 0 && owned(idx - strideZ)) ||
                (lz + 1 < state.sizeZ && owned(idx + strideZ))
        }

        val pos = BlockPos.MutableBlockPos()
        try {
            var idx = open.nextSetBit(0)
            while (idx >= 0 && idx < volume) {
                posFromIndex(state, idx, pos)
                val current = level.getBlockState(pos)
                val isWaterloggable = isWaterloggableForFlood(current, state.floodFluid)
                val inSimulationDomain = state.simulationDomain.get(idx)
                val isFloodFluid = countsAsMaterializedFloodFluid(current, state.floodFluid)
                if (externalFluid.get(idx)) {
                    if (!isFloodFluid) {
                        changed = true
                        externalFluid.clear(idx)
                    } else {
                        if (materialized.get(idx)) {
                            changed = true
                            materialized.clear(idx)
                        }
                        if (flooded.get(idx)) {
                            changed = true
                            flooded.clear(idx)
                        }
                        if (brokenByFlood.get(idx)) {
                            changed = true
                            brokenByFlood.clear(idx)
                        }
                        idx = open.nextSetBit(idx + 1)
                        continue
                    }
                }
                if (isWaterloggable &&
                    current.getValue(BlockStateProperties.WATERLOGGED) &&
                    shouldPreventExteriorWaterlogging(state, idx)
                ) {
                    val count = blockedExteriorWaterlogAttempts.incrementAndGet()
                    logThrottledDiag(count, "Drained exterior waterlogged block during materialized-fluid sync")
                    beginInternalUpdates()
                    val drained = current.setValue(BlockStateProperties.WATERLOGGED, false)
                    level.setBlock(pos, drained, 3)
                    level.scheduleTick(pos, Fluids.WATER, 1)
                    if (materialized.get(idx)) {
                        changed = true
                        materialized.clear(idx)
                    }
                    // Keep logical flooded/drain state intact for simulation-domain cells.
                    // Otherwise vanilla can immediately flow back into cells that were just drained
                    // before the next flood/drain solve updates the logical mask.
                    if (!inSimulationDomain) {
                        if (flooded.get(idx)) {
                            changed = true
                            flooded.clear(idx)
                        }
                        if (brokenByFlood.get(idx)) {
                            changed = true
                            brokenByFlood.clear(idx)
                        }
                    }
                    idx = open.nextSetBit(idx + 1)
                    continue
                }

                if (!inSimulationDomain) {
                    if (materialized.get(idx)) {
                        changed = true
                        materialized.clear(idx)
                    }
                    if (flooded.get(idx)) {
                        changed = true
                        flooded.clear(idx)
                    }
                    if (brokenByFlood.get(idx)) {
                        changed = true
                        brokenByFlood.clear(idx)
                    }
                    idx = open.nextSetBit(idx + 1)
                    continue
                }

                if (isFloodFluid &&
                    (materialized.get(idx) || flooded.get(idx) || state.queuedFloodAdds.get(idx) || touchesFloodOwnedFluid(idx))
                ) {
                    if (!materialized.get(idx)) {
                        changed = true
                        materialized.set(idx)
                    }
                    if (!flooded.get(idx)) {
                        changed = true
                        flooded.set(idx)
                    }
                } else {
                    if (materialized.get(idx)) {
                        changed = true
                        materialized.clear(idx)
                    }
                }
                idx = open.nextSetBit(idx + 1)
            }
        } finally {
            if (internalUpdatesActive) {
                applyingInternalUpdates = false
            }
        }
        if (changed) {
            state.persistDirty = true
        }
    }

    private fun updateVsBuoyancyFromPockets(ship: LoadedShip, state: ShipPocketState) {
        val serverShip = ship as? LoadedServerShip ?: return
        val buoyancyHandler = serverShip.getAttachment(BuoyancyHandlerAttachment::class.java) ?: return

        if (true || !VSGameConfig.SERVER.enablePocketBuoyancy) return

        val props = getBuoyancyFluidProps(state.floodFluid)
        buoyancyHandler?.setBuoyancyFluidDensity(props.density)
        buoyancyHandler?.setBuoyancyFluidViscosity(props.viscosity)

        // The additional buoyant force from pockets is just the volume of *submerged interior air* that is currently
        // not flooded (i.e. displacing world water).
        val maxAbs = state.simulationDomain.cardinality().toDouble().coerceAtLeast(1.0)
        val displaced = state.buoyancy.submergedAirVolume.coerceIn(0.0, maxAbs)
        buoyancyHandler?.setDisplacedVolume(displaced)

        val centerX: Double
        val centerY: Double
        val centerZ: Double
        if (displaced > 1.0e-6) {
            centerX = state.buoyancy.submergedAirSumX / displaced
            centerY = state.buoyancy.submergedAirSumY / displaced
            centerZ = state.buoyancy.submergedAirSumZ / displaced
        } else {
            centerX = 0.0
            centerY = 0.0
            centerZ = 0.0
        }
        buoyancyHandler?.setPocketCenter(centerX, centerY, centerZ)
    }

    @JvmStatic
    fun tickClientLevel(level: Level) {
        if (!level.isClientSide) return
        if (!VSGameConfig.COMMON.enableAirPockets) return

        val states = clientStates.computeIfAbsent(level.dimensionId) { ConcurrentHashMap() }
        val clientCameraChunkCenter = getClientCameraChunkCenter(level)
        val loadedShipIds = LongOpenHashSet()
        var remainingGeometrySubmissions = GEOMETRY_ASYNC_SUBMISSIONS_PER_LEVEL_PER_TICK
        var remainingWaterSolveSubmissions = WATER_SOLVER_ASYNC_SUBMISSIONS_PER_LEVEL_PER_TICK

        level.shipObjectWorld.loadedShips.forEach { ship ->
            loadedShipIds.add(ship.id)
            val state = states.computeIfAbsent(ship.id) { ShipPocketState() }

            val aabb = ship.shipAABB ?: return@forEach
            val baseMinX = aabb.minX()
            val baseMinY = aabb.minY()
            val baseMinZ = aabb.minZ()
            val baseSizeX = aabb.maxX() - baseMinX
            val baseSizeY = aabb.maxY() - baseMinY
            val baseSizeZ = aabb.maxZ() - baseMinZ

            val minX = baseMinX - POCKET_BOUNDS_PADDING
            val minY = baseMinY - POCKET_BOUNDS_PADDING
            val minZ = baseMinZ - POCKET_BOUNDS_PADDING
            val sizeX = baseSizeX + POCKET_BOUNDS_PADDING * 2
            val sizeY = baseSizeY + POCKET_BOUNDS_PADDING * 2
            val sizeZ = baseSizeZ + POCKET_BOUNDS_PADDING * 2
            val volume = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
            if (volume <= 0 || volume > MAX_SIM_VOLUME.toLong()) {
                state.dirty = false
                state.pendingGeometryFuture?.cancel(true)
                state.pendingGeometryFuture = null
                state.geometryJobInFlight = false
                state.pendingWaterSolveFuture?.cancel(true)
                state.pendingWaterSolveFuture = null
                state.waterSolveJobInFlight = false
                clearFloodWriteQueues(state)
                return@forEach
            }

            val geometryApplied = tryApplyCompletedGeometryJob(
                state = state,
                minX = minX,
                minY = minY,
                minZ = minZ,
                sizeX = sizeX,
                sizeY = sizeY,
                sizeZ = sizeZ,
                preserveLiveFloodState = false,
            )
            val needsRecompute =
                state.dirty || boundsMismatch(state, minX, minY, minZ, sizeX, sizeY, sizeZ)
            if (needsRecompute) {
                // When (re)loading a ship, the shipyard chunks can arrive a few ticks after the ship object itself.
                // If we recompute while those chunks are still unloaded, `getBlockState` returns air everywhere, which
                // makes the ship appear entirely "open" and disables all air pockets until another shipyard block
                // update marks the ship dirty again.
                if (!areShipyardChunksLoaded(level, baseMinX, baseMinY, baseMinZ, baseSizeX, baseSizeY, baseSizeZ)) {
                    state.dirty = true
                } else if (remainingGeometrySubmissions > 0 &&
                    trySubmitGeometryJob(level, state, minX, minY, minZ, sizeX, sizeY, sizeZ)
                ) {
                    remainingGeometrySubmissions--
                }
            }

            val now = level.gameTime
            val shipTransform = getQueryTransform(ship)
            if (state.sizeX > 0 &&
                state.sizeY > 0 &&
                state.sizeZ > 0 &&
                state.sizeX.toLong() * state.sizeY.toLong() * state.sizeZ.toLong() <= MAX_SIM_VOLUME.toLong()
            ) {
                val shipPosTmpKey = tmpShipPos3.get()
                val worldPosTmpKey = tmpWorldPos3.get()
                val baseTransformKey = clientTransformKey(
                    minX = state.minX,
                    minY = state.minY,
                    minZ = state.minZ,
                    shipTransform = shipTransform,
                    shipPosTmp = shipPosTmpKey,
                    worldPosTmp = worldPosTmpKey,
                )
                val clientQueryWindow = buildClientWaterSolveQueryWindow(volume, clientCameraChunkCenter)
                val currentTransformKey =
                    combineClientWaterSolveTransformKey(baseTransformKey, clientQueryWindow)
                tryApplyCompletedWaterSolveJob(
                    state = state,
                    nowTick = now,
                    shipTransform = shipTransform,
                    isClientSide = true,
                    currentTransformKeyOverride = currentTransformKey,
                )
                val submitDecision = decideClientWaterSolveSubmission(
                    state = state,
                    volume = volume,
                    geometryApplied = geometryApplied,
                    currentTransformKey = currentTransformKey,
                    nowTick = now,
                )
                if (submitDecision.shouldSubmit) {
                    if (remainingWaterSolveSubmissions > 0 &&
                        trySubmitWaterSolveJob(
                            level = level,
                            state = state,
                            shipTransform = shipTransform,
                            captureTick = now,
                            transformKeyOverride = currentTransformKey,
                            clientWorldQueryBounds = clientQueryWindow?.bounds,
                        )
                    ) {
                        remainingWaterSolveSubmissions--
                        if (submitDecision.forcedRefresh) {
                            val count = clientWaterSolveForcedRefreshCount.incrementAndGet()
                            logThrottledDiag(
                                count,
                                "Forced stale client water solve refresh shipId={} resultAge={} cadenceTicks={}",
                                ship.id,
                                now - state.lastClientWaterSolveApplyTick,
                                submitDecision.cadenceTicks,
                            )
                        }
                    }
                } else {
                    when (submitDecision.skipReason) {
                        ClientWaterSolveSkipReason.UNCHANGED_TRANSFORM -> {
                            val count = clientWaterSolveSkippedUnchangedTransformCount.incrementAndGet()
                            logThrottledDiag(
                                count,
                                "Skipped client water solve shipId={} reason=unchanged_transform transformKey={}",
                                ship.id,
                                currentTransformKey,
                            )
                        }
                        ClientWaterSolveSkipReason.CADENCE -> {
                            val count = clientWaterSolveSkippedCadenceCount.incrementAndGet()
                            logThrottledDiag(
                                count,
                                "Skipped client water solve shipId={} reason=cadence cadenceTicks={} lastSubmitTick={} nowTick={}",
                                ship.id,
                                submitDecision.cadenceTicks,
                                state.lastWaterSolveSubmitTick,
                                now,
                            )
                        }
                        ClientWaterSolveSkipReason.NOT_DEMANDED_RECENTLY -> {
                            val count = clientWaterSolveSkippedNotDemandedCount.incrementAndGet()
                            logThrottledDiag(
                                count,
                                "Skipped client water solve shipId={} reason=not_demanded_recently lastDemandTick={} nowTick={}",
                                ship.id,
                                state.lastClientDemandTick,
                                now,
                            )
                        }
                        null -> Unit
                    }
                }
            } else {
                tryApplyCompletedWaterSolveJob(state, now, shipTransform, isClientSide = true)
            }

            // Server-authoritative ingress particles are emitted from confirmed flood-write adds.
            // Keep client heuristic disabled to avoid duplicate/false-positive leak effects.
        }

        states.entries.removeIf { entry ->
            if (loadedShipIds.contains(entry.key)) return@removeIf false
            entry.value.pendingGeometryFuture?.cancel(true)
            entry.value.pendingGeometryFuture = null
            entry.value.geometryJobInFlight = false
            entry.value.pendingWaterSolveFuture?.cancel(true)
            entry.value.pendingWaterSolveFuture = null
            entry.value.waterSolveJobInFlight = false
            true
        }
    }

    private fun leakParticleForFluid(fluid: Fluid): ParticleOptions {
        val canonical = canonicalFloodSource(fluid)
        if (canonical == Fluids.LAVA) return ParticleTypes.LAVA
        return try {
            val legacy = canonical.defaultFluidState().createLegacyBlock()
            if (!legacy.isAir) BlockParticleOption(ParticleTypes.BLOCK, legacy) else ParticleTypes.SPLASH
        } catch (_: Throwable) {
            ParticleTypes.SPLASH
        }
    }

    private fun emitDirectionalLeakParticles(
        level: Level,
        state: ShipPocketState,
        shipTransform: ShipTransform,
        cellIdx: Int,
        faceDirCode: Int,
        jetDirCode: Int,
        particle: ParticleOptions,
        particleCount: Int,
        baseSpeed: Double,
    ) {
        if (particleCount <= 0) return
        val sizeX = state.sizeX
        val sizeY = state.sizeY
        val sizeZ = state.sizeZ
        val volume = sizeX * sizeY * sizeZ
        if (cellIdx < 0 || cellIdx >= volume) return

        val cellLX = cellIdx % sizeX
        val cellT = cellIdx / sizeX
        val cellLY = cellT % sizeY
        val cellLZ = cellT / sizeY

        val centerShipX = (state.minX + cellLX).toDouble() + 0.5
        val centerShipY = (state.minY + cellLY).toDouble() + 0.5
        val centerShipZ = (state.minZ + cellLZ).toDouble() + 0.5

        val faceDirX: Int
        val faceDirY: Int
        val faceDirZ: Int
        when (faceDirCode) {
            0 -> {
                faceDirX = -1; faceDirY = 0; faceDirZ = 0
            }
            1 -> {
                faceDirX = 1; faceDirY = 0; faceDirZ = 0
            }
            2 -> {
                faceDirX = 0; faceDirY = -1; faceDirZ = 0
            }
            3 -> {
                faceDirX = 0; faceDirY = 1; faceDirZ = 0
            }
            4 -> {
                faceDirX = 0; faceDirY = 0; faceDirZ = -1
            }
            else -> {
                faceDirX = 0; faceDirY = 0; faceDirZ = 1
            }
        }

        val jetDirX: Int
        val jetDirY: Int
        val jetDirZ: Int
        when (jetDirCode) {
            0 -> {
                jetDirX = -1; jetDirY = 0; jetDirZ = 0
            }
            1 -> {
                jetDirX = 1; jetDirY = 0; jetDirZ = 0
            }
            2 -> {
                jetDirX = 0; jetDirY = -1; jetDirZ = 0
            }
            3 -> {
                jetDirX = 0; jetDirY = 1; jetDirZ = 0
            }
            4 -> {
                jetDirX = 0; jetDirY = 0; jetDirZ = -1
            }
            else -> {
                jetDirX = 0; jetDirY = 0; jetDirZ = 1
            }
        }

        val tangentAX: Double
        val tangentAY: Double
        val tangentAZ: Double
        val tangentBX: Double
        val tangentBY: Double
        val tangentBZ: Double
        when {
            faceDirX != 0 -> {
                tangentAX = 0.0; tangentAY = 1.0; tangentAZ = 0.0
                tangentBX = 0.0; tangentBY = 0.0; tangentBZ = 1.0
            }
            faceDirY != 0 -> {
                tangentAX = 1.0; tangentAY = 0.0; tangentAZ = 0.0
                tangentBX = 0.0; tangentBY = 0.0; tangentBZ = 1.0
            }
            else -> {
                tangentAX = 1.0; tangentAY = 0.0; tangentAZ = 0.0
                tangentBX = 0.0; tangentBY = 1.0; tangentBZ = 0.0
            }
        }

        val shipPosTmp = tmpShipPos.get()
        val worldPosTmp = tmpWorldPos.get()
        val jetWorld = tmpShipFlowDir.get()
        val tangentAWorld = tmpShipGravityVec.get()
        val tangentBWorld = Vector3d()
        val rand = level.random
        val serverLevel = level as? ServerLevel

        shipPosTmp.set(centerShipX, centerShipY, centerShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val centerWorldX = worldPosTmp.x
        val centerWorldY = worldPosTmp.y
        val centerWorldZ = worldPosTmp.z

        shipPosTmp.set(
            centerShipX + jetDirX.toDouble(),
            centerShipY + jetDirY.toDouble(),
            centerShipZ + jetDirZ.toDouble(),
        )
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        jetWorld.set(worldPosTmp.x - centerWorldX, worldPosTmp.y - centerWorldY, worldPosTmp.z - centerWorldZ)
        if (jetWorld.lengthSquared() > 1.0e-12) {
            jetWorld.normalize().mul(baseSpeed)
        } else {
            jetWorld.set(0.0, 0.0, 0.0)
        }

        shipPosTmp.set(centerShipX + tangentAX, centerShipY + tangentAY, centerShipZ + tangentAZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        tangentAWorld.set(worldPosTmp.x - centerWorldX, worldPosTmp.y - centerWorldY, worldPosTmp.z - centerWorldZ)
        if (tangentAWorld.lengthSquared() > 1.0e-12) {
            tangentAWorld.normalize()
        } else {
            tangentAWorld.set(0.0, 0.0, 0.0)
        }

        shipPosTmp.set(centerShipX + tangentBX, centerShipY + tangentBY, centerShipZ + tangentBZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        tangentBWorld.set(worldPosTmp.x - centerWorldX, worldPosTmp.y - centerWorldY, worldPosTmp.z - centerWorldZ)
        if (tangentBWorld.lengthSquared() > 1.0e-12) {
            tangentBWorld.normalize()
        } else {
            tangentBWorld.set(0.0, 0.0, 0.0)
        }

        val faceOffset = 0.501
        val positionSpread = 0.49
        val tangentialSpeed = 0.05
        repeat(particleCount) {
            val u = (rand.nextDouble() - 0.5) * 2.0 * positionSpread
            val v = (rand.nextDouble() - 0.5) * 2.0 * positionSpread

            val pxShip = centerShipX + faceDirX.toDouble() * faceOffset + tangentAX * u + tangentBX * v
            val pyShip = centerShipY + faceDirY.toDouble() * faceOffset + tangentAY * u + tangentBY * v
            val pzShip = centerShipZ + faceDirZ.toDouble() * faceOffset + tangentAZ * u + tangentBZ * v
            shipPosTmp.set(pxShip, pyShip, pzShip)
            shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)

            val su = (rand.nextDouble() - 0.5) * 2.0 * tangentialSpeed
            val sv = (rand.nextDouble() - 0.5) * 2.0 * tangentialSpeed
            val vx = jetWorld.x + tangentAWorld.x * su + tangentBWorld.x * sv
            val vy = jetWorld.y + tangentAWorld.y * su + tangentBWorld.y * sv
            val vz = jetWorld.z + tangentAWorld.z * su + tangentBWorld.z * sv

            if (serverLevel != null) {
                serverLevel.sendParticles(particle, worldPosTmp.x, worldPosTmp.y, worldPosTmp.z, 1, vx, vy, vz, 0.0)
            } else {
                level.addParticle(particle, worldPosTmp.x, worldPosTmp.y, worldPosTmp.z, vx, vy, vz)
            }
        }
    }

    private fun spawnIngressParticlesServer(
        level: ServerLevel,
        state: ShipPocketState,
        shipTransform: ShipTransform,
        addedIndices: IntArray,
    ) {
        if (addedIndices.isEmpty()) return
        if (!ensureOutsideVoidMask(state)) return

        val sizeX = state.sizeX
        val sizeY = state.sizeY
        val sizeZ = state.sizeZ
        val volume = sizeX * sizeY * sizeZ
        if (volume <= 0) return

        val strideY = sizeX
        val strideZ = sizeX * sizeY
        val open = state.open
        val exterior = state.outsideVoid
        val simulationDomain = state.simulationDomain
        val particle = leakParticleForFluid(state.floodFluid)
        val particleSpeedMultiplier = VSGameConfig.COMMON.shipPocketParticleSpeedMultiplier.coerceIn(0.1, 5.0)
        val visited = tmpFloodComponentVisited.get()
        var queue = tmpFloodQueue.get()
        if (queue.size < volume) {
            queue = IntArray(volume)
            tmpFloodQueue.set(queue)
        }

        data class Opening(val cellIdx: Int, val dirCode: Int, val conductance: Int)

        fun bestExteriorOpening(cellIdx: Int): Opening? {
            if (cellIdx < 0 || cellIdx >= volume) return null
            val lx = cellIdx % sizeX
            val t = cellIdx / sizeX
            val ly = t % sizeY
            val lz = t / sizeY
            var bestDir = -1
            var bestConductance = 0

            fun consider(n: Int, dirCode: Int) {
                if (n < 0 || n >= volume) return
                if (!open.get(n) || !exterior.get(n)) return
                val cond = edgeConductance(state, cellIdx, lx, ly, lz, dirCode)
                if (cond > bestConductance) {
                    bestConductance = cond
                    bestDir = dirCode
                }
            }

            if (lx > 0) consider(cellIdx - 1, 0)
            if (lx + 1 < sizeX) consider(cellIdx + 1, 1)
            if (ly > 0) consider(cellIdx - strideY, 2)
            if (ly + 1 < sizeY) consider(cellIdx + strideY, 3)
            if (lz > 0) consider(cellIdx - strideZ, 4)
            if (lz + 1 < sizeZ) consider(cellIdx + strideZ, 5)

            return if (bestDir >= 0 && bestConductance > 0) {
                Opening(cellIdx, bestDir, bestConductance)
            } else {
                null
            }
        }

        fun findNearestExteriorOpening(seedIdx: Int): Opening? {
            bestExteriorOpening(seedIdx)?.let { return it }
            visited.clear()
            var head = 0
            var tail = 0
            visited.set(seedIdx)
            queue[tail++] = seedIdx

            while (head < tail && head < 64 && tail < queue.size) {
                val cur = queue[head++]
                bestExteriorOpening(cur)?.let { return it }
                val lx = cur % sizeX
                val t = cur / sizeX
                val ly = t % sizeY
                val lz = t / sizeY

                fun enqueue(n: Int) {
                    if (n < 0 || n >= volume || visited.get(n)) return
                    if (!open.get(n) || !simulationDomain.get(n)) return
                    if (!state.materializedWater.get(n) && n != seedIdx) return
                    visited.set(n)
                    queue[tail++] = n
                }

                if (lx > 0) enqueue(cur - 1)
                if (lx + 1 < sizeX) enqueue(cur + 1)
                if (ly > 0) enqueue(cur - strideY)
                if (ly + 1 < sizeY) enqueue(cur + strideY)
                if (lz > 0) enqueue(cur - strideZ)
                if (lz + 1 < sizeZ) enqueue(cur + strideZ)
            }
            return null
        }

        var budget = 8
        for (idx in addedIndices) {
            if (budget <= 0) break
            if (idx < 0 || idx >= volume) continue
            if (!open.get(idx) || !simulationDomain.get(idx)) continue

            val opening = findNearestExteriorOpening(idx) ?: continue
            budget--

            val particleCount = (2 + opening.conductance / 10).coerceIn(2, 12)
            val speed = ((0.08 + opening.conductance * 0.00035).coerceIn(0.08, 0.2)) * particleSpeedMultiplier
            emitDirectionalLeakParticles(
                level = level,
                state = state,
                shipTransform = shipTransform,
                cellIdx = opening.cellIdx,
                faceDirCode = opening.dirCode,
                jetDirCode = opening.dirCode xor 1,
                particle = particle,
                particleCount = particleCount,
                baseSpeed = speed,
            )
        }
    }

    private fun spawnLeakParticlesClient(level: Level, state: ShipPocketState, shipTransform: ShipTransform) {
        if (!level.isClientSide) return

        val interior = state.simulationDomain
        if (interior.isEmpty) return

        val targetWet = state.waterReachable
        if (targetWet.isEmpty) return

        val targetWetInterior = targetWet.clone() as BitSet
        targetWetInterior.and(interior)
        if (targetWetInterior.isEmpty) return

        val missing = targetWetInterior.clone() as BitSet
        missing.andNot(state.materializedWater)
        if (missing.isEmpty) return

        val maxDistSq = 96.0 * 96.0
        val shipWorld = shipTransform.positionInWorld
        val anyViewerNearby = level.players().any {
            val dx = it.x - shipWorld.x()
            val dy = it.y - shipWorld.y()
            val dz = it.z - shipWorld.z()
            dx * dx + dy * dy + dz * dz <= maxDistSq
        }
        if (!anyViewerNearby) return

        val sizeX = state.sizeX
        val sizeY = state.sizeY
        val sizeZ = state.sizeZ
        val volume = sizeX * sizeY * sizeZ
        val strideY = sizeX
        val strideZ = sizeX * sizeY

        val open = state.open
        val rand = level.random
        val shipCellPos = BlockPos.MutableBlockPos()

        fun isCellAlreadyFloodFluid(cellIdx: Int): Boolean {
            if (cellIdx < 0 || cellIdx >= volume) return false
            posFromIndex(state, cellIdx, shipCellPos)
            val cellFluid = FluidStateManager.getFluidData(level, shipCellPos)
            return cellFluid != null && cellFluid.sourceFluid() == state.floodFluid
        }

        val faceDirBuf = IntArray(6)
        val faceConductanceBuf = IntArray(6)

        val maxHoles = 4
        val chosenHoleIdx = IntArray(maxHoles)
        val chosenOutDirCode = IntArray(maxHoles)
        val chosenConductance = IntArray(maxHoles)
        var chosenCount = 0

        fun alreadyChosen(holeIdx: Int): Boolean {
            for (i in 0 until chosenCount) if (chosenHoleIdx[i] == holeIdx) return true
            return false
        }

        fun chooseHole(holeIdx: Int, outDirCode: Int, conductance: Int) {
            if (chosenCount >= maxHoles) return
            chosenHoleIdx[chosenCount] = holeIdx
            chosenOutDirCode[chosenCount] = outDirCode
            chosenConductance[chosenCount] = conductance
            chosenCount++
        }

        fun tryChooseHoleInterior(cellIdx: Int): Boolean {
            if (cellIdx < 0 || cellIdx >= volume) return false
            if (!interior.get(cellIdx)) return false
            if (!targetWetInterior.get(cellIdx)) return false
            if (isCellAlreadyFloodFluid(cellIdx)) return false
            if (alreadyChosen(cellIdx)) return false

            val lx = cellIdx % sizeX
            val t = cellIdx / sizeX
            val ly = t % sizeY
            val lz = t / sizeY

            var faceCount = 0
            fun tryFace(nIdx: Int, outDirCode: Int, conductance: Int) {
                if (conductance <= 0) return
                if (nIdx < 0 || nIdx >= volume) return
                if (interior.get(nIdx)) return
                if (!open.get(nIdx)) return
                if (!targetWet.get(nIdx)) return
                faceDirBuf[faceCount] = outDirCode
                faceConductanceBuf[faceCount] = conductance
                faceCount++
            }

            if (lx > 0) tryFace(cellIdx - 1, 0, edgeConductance(state, cellIdx, lx, ly, lz, 0))
            if (lx + 1 < sizeX) tryFace(cellIdx + 1, 1, edgeConductance(state, cellIdx, lx, ly, lz, 1))
            if (ly > 0) tryFace(cellIdx - strideY, 2, edgeConductance(state, cellIdx, lx, ly, lz, 2))
            if (ly + 1 < sizeY) tryFace(cellIdx + strideY, 3, edgeConductance(state, cellIdx, lx, ly, lz, 3))
            if (lz > 0) tryFace(cellIdx - strideZ, 4, edgeConductance(state, cellIdx, lx, ly, lz, 4))
            if (lz + 1 < sizeZ) tryFace(cellIdx + strideZ, 5, edgeConductance(state, cellIdx, lx, ly, lz, 5))

            if (faceCount == 0) return false

            var chosenFace = 0
            var bestScore = Int.MIN_VALUE
            for (i in 0 until faceCount) {
                val candidateOut = faceDirBuf[i]
                val inDir = candidateOut xor 1
                val inNeighbor = when (inDir) {
                    0 -> if (lx > 0) cellIdx - 1 else -1
                    1 -> if (lx + 1 < sizeX) cellIdx + 1 else -1
                    2 -> if (ly > 0) cellIdx - strideY else -1
                    3 -> if (ly + 1 < sizeY) cellIdx + strideY else -1
                    4 -> if (lz > 0) cellIdx - strideZ else -1
                    else -> if (lz + 1 < sizeZ) cellIdx + strideZ else -1
                }
                var score = faceConductanceBuf[i]
                if (inNeighbor >= 0 && missing.get(inNeighbor)) score += 10_000
                if (score > bestScore) {
                    bestScore = score
                    chosenFace = i
                }
            }

            chooseHole(cellIdx, faceDirBuf[chosenFace], faceConductanceBuf[chosenFace])
            return true
        }

        val scanBudget = 512
        var scanned = 0
        var idx = missing.nextSetBit(rand.nextInt(volume))
        if (idx < 0) idx = missing.nextSetBit(0)
        while (idx >= 0 && idx < volume && chosenCount < maxHoles && scanned < scanBudget) {
            val lx = idx % sizeX
            val t = idx / sizeX
            val ly = t % sizeY
            val lz = t / sizeY

            tryChooseHoleInterior(idx)
            if (chosenCount >= maxHoles) break
            if (lx > 0) tryChooseHoleInterior(idx - 1)
            if (chosenCount >= maxHoles) break
            if (lx + 1 < sizeX) tryChooseHoleInterior(idx + 1)
            if (chosenCount >= maxHoles) break
            if (ly > 0) tryChooseHoleInterior(idx - strideY)
            if (chosenCount >= maxHoles) break
            if (ly + 1 < sizeY) tryChooseHoleInterior(idx + strideY)
            if (chosenCount >= maxHoles) break
            if (lz > 0) tryChooseHoleInterior(idx - strideZ)
            if (chosenCount >= maxHoles) break
            if (lz + 1 < sizeZ) tryChooseHoleInterior(idx + strideZ)

            scanned++
            idx = missing.nextSetBit(idx + 1)
        }

        if (chosenCount == 0) {
            repeat(64) {
                if (chosenCount >= maxHoles) return@repeat
                val start = rand.nextInt(volume)
                var candidate = targetWetInterior.nextSetBit(start)
                if (candidate < 0) candidate = targetWetInterior.nextSetBit(0)
                if (candidate >= 0) tryChooseHoleInterior(candidate)
            }
        }
        if (chosenCount == 0) return

        val leakParticle = leakParticleForFluid(state.floodFluid)
        val particleSpeedMultiplier = VSGameConfig.COMMON.shipPocketParticleSpeedMultiplier.coerceIn(0.1, 5.0)
        for (iHole in 0 until chosenCount) {
            val holeIdx = chosenHoleIdx[iHole]
            val outDirCode = chosenOutDirCode[iHole]
            val conductance = chosenConductance[iHole].coerceAtLeast(1)
            val particleCount = (3 + conductance / 8).coerceIn(3, 18)
            val speed = ((0.09 + conductance * 0.0004).coerceIn(0.09, 0.22)) * particleSpeedMultiplier
            emitDirectionalLeakParticles(
                level = level,
                state = state,
                shipTransform = shipTransform,
                cellIdx = holeIdx,
                faceDirCode = outDirCode,
                jetDirCode = outDirCode xor 1,
                particle = leakParticle,
                particleCount = particleCount,
                baseSpeed = speed,
            )
            val outwardCount = (particleCount / 2).coerceIn(1, 9)
            emitDirectionalLeakParticles(
                level = level,
                state = state,
                shipTransform = shipTransform,
                cellIdx = holeIdx,
                faceDirCode = outDirCode,
                jetDirCode = outDirCode,
                particle = leakParticle,
                particleCount = outwardCount,
                baseSpeed = (speed * 0.9).coerceAtMost(0.3 * particleSpeedMultiplier),
            )
        }
    }

    private fun areShipyardChunksLoaded(
        level: Level,
        minX: Int,
        minY: Int,
        minZ: Int,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
    ): Boolean {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return false
        val maxX = minX + sizeX - 1
        val maxY = minY + sizeY - 1
        val maxZ = minZ + sizeZ - 1
        return level.hasChunksAt(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ))
    }

    private fun areWorldChunksLoadedForShipBounds(
        level: Level,
        shipTransform: ShipTransform,
        minX: Int,
        minY: Int,
        minZ: Int,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
    ): Boolean {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) return false

        val maxX = minX + sizeX
        val maxY = minY + sizeY
        val maxZ = minZ + sizeZ

        val shipPosTmp = tmpShipPos3.get()
        val worldPosTmp = tmpWorldPos3.get()

        var minWX = Double.POSITIVE_INFINITY
        var minWY = Double.POSITIVE_INFINITY
        var minWZ = Double.POSITIVE_INFINITY
        var maxWX = Double.NEGATIVE_INFINITY
        var maxWY = Double.NEGATIVE_INFINITY
        var maxWZ = Double.NEGATIVE_INFINITY

        for (corner in 0 until 8) {
            val x = if ((corner and 1) == 0) minX.toDouble() else maxX.toDouble()
            val y = if ((corner and 2) == 0) minY.toDouble() else maxY.toDouble()
            val z = if ((corner and 4) == 0) minZ.toDouble() else maxZ.toDouble()
            shipPosTmp.set(x, y, z)
            shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
            if (worldPosTmp.x < minWX) minWX = worldPosTmp.x
            if (worldPosTmp.y < minWY) minWY = worldPosTmp.y
            if (worldPosTmp.z < minWZ) minWZ = worldPosTmp.z
            if (worldPosTmp.x > maxWX) maxWX = worldPosTmp.x
            if (worldPosTmp.y > maxWY) maxWY = worldPosTmp.y
            if (worldPosTmp.z > maxWZ) maxWZ = worldPosTmp.z
        }

        if (!minWX.isFinite() || !minWY.isFinite() || !minWZ.isFinite()) return false
        if (!maxWX.isFinite() || !maxWY.isFinite() || !maxWZ.isFinite()) return false

        val eps = 1.0E-7
        var minBlockX = Mth.floor(minWX + eps)
        var minBlockY = Mth.floor(minWY + eps)
        var minBlockZ = Mth.floor(minWZ + eps)
        var maxBlockX = Mth.floor(maxWX - eps)
        var maxBlockY = Mth.floor(maxWY - eps)
        var maxBlockZ = Mth.floor(maxWZ - eps)
        if (maxBlockX < minBlockX) maxBlockX = minBlockX
        if (maxBlockY < minBlockY) maxBlockY = minBlockY
        if (maxBlockZ < minBlockZ) maxBlockZ = minBlockZ

        return level.hasChunksAt(
            BlockPos(minBlockX, minBlockY, minBlockZ),
            BlockPos(maxBlockX, maxBlockY, maxBlockZ),
        )
    }

    data class ClientWaterReachableSnapshot(
        val geometryRevision: Long,
        val floodFluid: Fluid,
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val sizeX: Int,
        val sizeY: Int,
        val sizeZ: Int,
        val open: BitSet,
        val interior: BitSet,
        val waterReachable: BitSet,
        val unreachableVoid: BitSet,
    )

    @JvmStatic
    fun getClientWaterReachableSnapshot(level: Level, shipId: Long): ClientWaterReachableSnapshot? {
        if (!level.isClientSide) return null
        val state = clientStates[level.dimensionId]?.get(shipId) ?: return null
        markClientStateDemanded(level, state)
        return ClientWaterReachableSnapshot(
            state.geometryRevision,
            state.floodFluid,
            state.minX,
            state.minY,
            state.minZ,
            state.sizeX,
            state.sizeY,
            state.sizeZ,
            state.open,
            state.simulationDomain,
            state.waterReachable,
            state.unreachableVoid,
        )
    }

    @JvmStatic
    fun hasClientWaterReachableSnapshots(level: Level): Boolean {
        if (!level.isClientSide) return false
        return clientStates[level.dimensionId]?.isNotEmpty() == true
    }

    private fun isSuppressionClassification(state: ShipPocketState, classification: PointVoidClassification): Boolean {
        if (classification.kind == PointVoidClass.OUT_OF_BOUNDS || classification.kind == PointVoidClass.SOLID) return false
        val idx = classification.voxelIndex
        if (idx < 0) return false
        if (state.materializedWater.get(idx)) return false
        if (state.waterReachable.get(idx)) return false
        return isClassificationInSimulationDomain(state, classification)
    }

    private fun isAirPocketClassification(state: ShipPocketState, classification: PointVoidClassification): Boolean {
        if (classification.kind != PointVoidClass.INTERIOR_VOID) return false
        val idx = classification.voxelIndex
        if (idx < 0) return false
        if (state.materializedWater.get(idx)) return false
        return state.unreachableVoid.get(idx)
    }

    private fun getIntersectingShipsCached(
        level: Level,
        worldBlockPos: BlockPos,
        queryAabb: AABBd,
    ): List<Ship> {
        val cache = tmpIntersectingShipsCache.get()
        val tick = level.gameTime
        val posLong = worldBlockPos.asLong()
        val slot = intersectingShipsCacheSlot(level, tick, posLong)
        if (cache.occupied[slot] &&
            cache.levels[slot] === level &&
            cache.ticks[slot] == tick &&
            cache.worldPosLongs[slot] == posLong
        ) {
            return cache.shipsBySlot[slot]
        }

        val ships = ArrayList<Ship>()
        val localQueryAabb = AABBd()
        for (ship in level.shipObjectWorld.loadedShips.getIntersecting(queryAabb, level.dimensionId)) {
            queryAabb.transform(ship.worldToShip, localQueryAabb)
            if (!EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, localQueryAabb)) {
                continue
            }
            ships.add(ship)
        }

        val cachedShips = if (ships.isEmpty()) emptyList() else ships
        cache.occupied[slot] = true
        cache.levels[slot] = level
        cache.ticks[slot] = tick
        cache.worldPosLongs[slot] = posLong
        cache.shipsBySlot[slot] = cachedShips
        return cachedShips
    }

    @JvmStatic
    fun overrideWaterFluidState(level: Level, worldBlockPos: BlockPos, original: FluidState): FluidState {
        return overrideWaterFluidState(
            level = level,
            worldX = worldBlockPos.x + 0.5,
            worldY = worldBlockPos.y + 0.5,
            worldZ = worldBlockPos.z + 0.5,
            original = original,
        )
    }

    @JvmStatic
    fun overrideWaterFluidState(
        level: Level,
        worldX: Double,
        worldY: Double,
        worldZ: Double,
        original: FluidState,
    ): FluidState {
        if (!VSGameConfig.COMMON.enableAirPockets) return original
        if (level.isBlockInShipyard(worldX, worldY, worldZ)) return original
        val queryCache = tmpChunkQueryCache.get().apply { reset() }

        val worldBlockPos = BlockPos.containing(worldX, worldY, worldZ)
        val queryAabb = ShipWaterPocketManager.tmpQueryAabb.get().apply {
            minX = worldBlockPos.x.toDouble()
            minY = worldBlockPos.y.toDouble()
            minZ = worldBlockPos.z.toDouble()
            maxX = (worldBlockPos.x + 1).toDouble()
            maxY = (worldBlockPos.y + 1).toDouble()
            maxZ = (worldBlockPos.z + 1).toDouble()
        }
        val worldPos = ShipWaterPocketManager.tmpWorldPos.get().set(worldX, worldY, worldZ)
        val shipPosTmp = ShipWaterPocketManager.tmpShipPos.get()
        val shipBlockPosTmp = ShipWaterPocketManager.tmpShipBlockPos.get()

        for (ship in ShipWaterPocketManager.getIntersectingShipsCached(
            level, worldBlockPos, queryAabb
        )) {
            val state = ShipWaterPocketManager.getState(
                level, ship.id
            ) ?: continue
            markClientStateDemanded(level, state)
            val shipTransform =
                ShipWaterPocketManager.getQueryTransform(
                    ship
                )

            shipTransform.worldToShip.transformPosition(worldPos, shipPosTmp)
            val classification = classifyShipPointWithEpsilon(
                state = state,
                x = shipPosTmp.x,
                y = shipPosTmp.y,
                z = shipPosTmp.z,
                out = shipBlockPosTmp,
            )
            val suppressionCell =
                if (classification.kind == PointVoidClass.SOLID) {
                    findNearbyWorldFluidSuppressionZone(state, shipPosTmp, shipBlockPosTmp, radius = 0)
                } else {
                    null
                }
            if (classification.kind == PointVoidClass.OUT_OF_BOUNDS ||
                (classification.kind == PointVoidClass.SOLID && suppressionCell == null)
            ) {
                continue
            }

            val shipFluid = findShipFluidAtShipPoint(level, shipPosTmp, shipBlockPosTmp, queryCache)
            if (!shipFluid.isEmpty) return shipFluid
            if (!original.isEmpty && (suppressionCell != null || ShipWaterPocketManager.isSuppressionClassification(
                    state, classification
                ))
            ) {
                val count = ShipWaterPocketManager.worldSuppressionHits.incrementAndGet()
                ShipWaterPocketManager.logThrottledDiag(
                    count, "Suppressed world fluid query in ship simulation-domain suppression zone"
                )
                return Fluids.EMPTY.defaultFluidState()
            }
        }

        return original
    }

    @JvmStatic
    fun overrideShipyardWaterFluidState(
        level: Level,
        shipBlockPos: BlockPos,
        original: FluidState,
        currentState: BlockState? = null,
    ): FluidState {
        if (!VSGameConfig.COMMON.enableAirPockets) return original
        if (!level.isBlockInShipyard(shipBlockPos)) return original

        val ship = level.getShipManagingPos(shipBlockPos) ?: return original
        val state = getState(level, ship.id) ?: return original
        if (state.sizeX <= 0 || state.sizeY <= 0 || state.sizeZ <= 0) return original

        val lx = shipBlockPos.x - state.minX
        val ly = shipBlockPos.y - state.minY
        val lz = shipBlockPos.z - state.minZ
        if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return original

        val idx = indexOf(state, lx, ly, lz)
        if (!state.open.get(idx) || !state.simulationDomain.get(idx)) return original
        if (state.materializedWater.get(idx)) return original

        val blockState = currentState ?: level.getBlockState(shipBlockPos)
        return if (countsAsMaterializedFloodFluid(blockState, state.floodFluid)) {
            original
        } else {
            Fluids.EMPTY.defaultFluidState()
        }
    }

    /**
     * If [worldBlockPos] intersects a ship-space fluid block (shipyard geometry), returns that fluid's flow vector
     * rotated into world space based on the ship's current transform. Returns null if no ship fluid applies.
     *
     * This is used by entity fluid pushing so that ship fluids push entities in the correct direction when ships are
     * rotated. World water is intentionally unaffected.
     */
    @JvmStatic
    fun computeRotatedShipFluidFlow(level: Level, worldBlockPos: BlockPos): Vec3? {
        return computeShipFluidSample(level, worldBlockPos).flow
    }

    private fun computeShipFluidSample(level: Level, worldBlockPos: BlockPos): ShipFluidSampleCache {
        val cache = tmpShipFluidSampleCache.get()
        val tick = level.gameTime
        val posLong = worldBlockPos.asLong()
        val enabled = VSGameConfig.COMMON.enableAirPockets
        if (
            cache.lastLevel === level &&
            cache.lastTick == tick &&
            cache.lastWorldPosLong == posLong &&
            cache.lastConfigEnabled == enabled &&
            cache.computed
        ) {
            return cache
        }

        cache.lastLevel = level
        cache.lastTick = tick
        cache.lastWorldPosLong = posLong
        cache.lastConfigEnabled = enabled
        cache.computed = true
        cache.flow = null
        cache.height = null

        if (!enabled) return cache
        if (level.isBlockInShipyard(worldBlockPos)) return cache
        if (isWorldPosInShipAirPocket(level, worldBlockPos)) return cache
        val queryCache = tmpChunkQueryCache.get().apply { reset() }

        val queryAabb = tmpQueryAabb.get().apply {
            minX = worldBlockPos.x.toDouble()
            minY = worldBlockPos.y.toDouble()
            minZ = worldBlockPos.z.toDouble()
            maxX = (worldBlockPos.x + 1).toDouble()
            maxY = (worldBlockPos.y + 1).toDouble()
            maxZ = (worldBlockPos.z + 1).toDouble()
        }
        val worldPos = tmpWorldPos.get().set(
            worldBlockPos.x + 0.5,
            worldBlockPos.y + 0.5,
            worldBlockPos.z + 0.5
        )
        val shipPosTmp = tmpShipPos.get()
        val shipBlockPosTmp = tmpShipBlockPos.get()
        val dir = tmpShipFlowDir.get()

        for (ship in getIntersectingShipsCached(level, worldBlockPos, queryAabb)) {
            val shipTransform = getQueryTransform(ship)

            shipTransform.worldToShip.transformPosition(worldPos, shipPosTmp)
            val state = getState(level, ship.id)
            if (state != null) {
                markClientStateDemanded(level, state)
                val classification = classifyShipPointWithEpsilon(
                    state = state,
                    x = shipPosTmp.x,
                    y = shipPosTmp.y,
                    z = shipPosTmp.z,
                    out = shipBlockPosTmp,
                )
                if (classification.kind == PointVoidClass.OUT_OF_BOUNDS || classification.kind == PointVoidClass.SOLID) {
                    continue
                }
            } else {
                shipBlockPosTmp.set(Mth.floor(shipPosTmp.x), Mth.floor(shipPosTmp.y), Mth.floor(shipPosTmp.z))
            }

            val shipFluid = findShipFluidAtShipPoint(level, shipPosTmp, shipBlockPosTmp, queryCache)
            if (shipFluid.isEmpty) continue

            cache.height = shipFluid.getHeight(level, shipBlockPosTmp)

            val shipFlow = shipFluid.getFlow(level, shipBlockPosTmp)
            if (shipFlow.lengthSqr() < 1.0e-12) {
                cache.flow = shipFlow
                return cache
            }

            dir.set(shipFlow.x, shipFlow.y, shipFlow.z)
            shipTransform.shipToWorldRotation.transform(dir)
            cache.flow = Vec3(dir.x, dir.y, dir.z)
            return cache
        }

        return cache
    }

    /**
     * If [worldBlockPos] intersects a ship-space fluid block (shipyard geometry), returns that fluid's height within
     * the shipyard cell. Returns null if no ship fluid applies.
     *
     * This is required because vanilla/Forge fluid logic computes heights using neighbor block queries, and when ship
     * fluids are queried from world space the neighbor lookups would otherwise sample world blocks (air) instead of the
     * shipyard, causing incorrect swimming/overlays (especially for flowing fluids).
     */
    @JvmStatic
    fun computeShipFluidHeight(level: Level, worldBlockPos: BlockPos): Float? {
        return computeShipFluidSample(level, worldBlockPos).height
    }

    @JvmStatic
    fun isWorldPosInShipWorldFluidSuppressionZone(level: Level, worldBlockPos: BlockPos): Boolean {
        return isWorldPosInShipWorldFluidSuppressionZone(
            level = level,
            worldX = worldBlockPos.x + 0.5,
            worldY = worldBlockPos.y + 0.5,
            worldZ = worldBlockPos.z + 0.5,
        )
    }

    @JvmStatic
    fun isWorldPosInShipWorldFluidSuppressionZone(level: Level, worldX: Double, worldY: Double, worldZ: Double): Boolean {
        if (!VSGameConfig.COMMON.enableAirPockets) return false
        if (level.isBlockInShipyard(worldX, worldY, worldZ)) return false

        val worldBlockPos = BlockPos.containing(worldX, worldY, worldZ)
        val queryAabb = tmpQueryAabb.get().apply {
            minX = worldBlockPos.x.toDouble()
            minY = worldBlockPos.y.toDouble()
            minZ = worldBlockPos.z.toDouble()
            maxX = (worldBlockPos.x + 1).toDouble()
            maxY = (worldBlockPos.y + 1).toDouble()
            maxZ = (worldBlockPos.z + 1).toDouble()
        }
        val worldPos = tmpWorldPos.get().set(worldX, worldY, worldZ)
        val shipPosTmp = tmpShipPos.get()
        val shipBlockPosTmp = tmpShipBlockPos.get()

        for (ship in getIntersectingShipsCached(level, worldBlockPos, queryAabb)) {
            val state = getState(level, ship.id) ?: continue
            markClientStateDemanded(level, state)
            val shipTransform = getQueryTransform(ship)

            shipTransform.worldToShip.transformPosition(worldPos, shipPosTmp)
            val classification = classifyShipPointWithEpsilon(
                state = state,
                x = shipPosTmp.x,
                y = shipPosTmp.y,
                z = shipPosTmp.z,
                out = shipBlockPosTmp,
            )
            if (isSuppressionClassification(state, classification) ||
                (classification.kind == PointVoidClass.SOLID &&
                    findNearbyWorldFluidSuppressionZone(state, shipPosTmp, shipBlockPosTmp, radius = 0) != null)
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Returns true if the given world-space block position is inside a sealed ship air pocket (i.e. open space that is
     * not reachable by world water).
     *
     * This intentionally does *not* check the actual world fluid/block state at [worldBlockPos]; it only answers whether
     * "world water should be treated as air here" based on the ship pocket simulation.
     */
    @JvmStatic
    fun isShipyardBlockPosInShipAirPocket(level: Level, shipBlockPos: BlockPos): Boolean {
        if (!VSGameConfig.COMMON.enableAirPockets) return false
        if (!level.isBlockInShipyard(shipBlockPos)) return false

        val ship = level.getShipManagingPos(shipBlockPos) ?: return false
        val state = getState(level, ship.id) ?: return false
        if (level.isClientSide) {
            markClientStateDemanded(level, state)
        }
        return isAirPocket(state, shipBlockPos)
    }

    @JvmStatic
    fun hasShipyardAirPocketCellsInSection(level: Level, chunkX: Int, sectionY: Int, chunkZ: Int): Boolean {
        if (!VSGameConfig.COMMON.enableAirPockets) return false

        val ship = level.getShipManagingPos(chunkX, chunkZ) ?: return false
        val state = getState(level, ship.id) ?: return false

        val minX = (chunkX shl 4).coerceAtLeast(state.minX)
        val minY = (sectionY shl 4).coerceAtLeast(state.minY)
        val minZ = (chunkZ shl 4).coerceAtLeast(state.minZ)
        val maxX = ((chunkX shl 4) + 15).coerceAtMost(state.minX + state.sizeX - 1)
        val maxY = ((sectionY shl 4) + 15).coerceAtMost(state.minY + state.sizeY - 1)
        val maxZ = ((chunkZ shl 4) + 15).coerceAtMost(state.minZ + state.sizeZ - 1)
        if (minX > maxX || minY > maxY || minZ > maxZ) return false

        for (z in minZ..maxZ) {
            val lz = z - state.minZ
            for (y in minY..maxY) {
                val ly = y - state.minY
                for (x in minX..maxX) {
                    val idx = indexOf(state, x - state.minX, ly, lz)
                    if (state.interior.get(idx) && !state.materializedWater.get(idx)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    @JvmStatic
    fun isWorldPosInShipAirPocket(level: Level, worldBlockPos: BlockPos): Boolean {
        return isWorldPosInAnyShipAirPocket(
            level = level,
            worldX = worldBlockPos.x + 0.5,
            worldY = worldBlockPos.y + 0.5,
            worldZ = worldBlockPos.z + 0.5,
        )
    }

    @JvmStatic
    fun isWorldPosInShipAirPocket(level: Level, worldX: Double, worldY: Double, worldZ: Double): Boolean {
        return isWorldPosInAnyShipAirPocket(
            level = level,
            worldX = worldX,
            worldY = worldY,
            worldZ = worldZ,
        )
    }

    private fun isWorldPosInAnyShipAirPocket(
        level: Level,
        worldX: Double,
        worldY: Double,
        worldZ: Double,
    ): Boolean {
        if (!VSGameConfig.COMMON.enableAirPockets) return false
        if (level.isBlockInShipyard(worldX, worldY, worldZ)) return false

        val worldBlockPos = BlockPos.containing(worldX, worldY, worldZ)
        val queryAabb = tmpQueryAabb.get().apply {
            minX = worldBlockPos.x.toDouble()
            minY = worldBlockPos.y.toDouble()
            minZ = worldBlockPos.z.toDouble()
            maxX = (worldBlockPos.x + 1).toDouble()
            maxY = (worldBlockPos.y + 1).toDouble()
            maxZ = (worldBlockPos.z + 1).toDouble()
        }
        val worldPos = tmpWorldPos.get().set(worldX, worldY, worldZ)
        val shipPosTmp = tmpShipPos.get()
        val shipBlockPosTmp = tmpShipBlockPos.get()

        for (ship in getIntersectingShipsCached(level, worldBlockPos, queryAabb)) {
            val state = getState(level, ship.id) ?: continue
            markClientStateDemanded(level, state)
            val shipTransform = getQueryTransform(ship)

            shipTransform.worldToShip.transformPosition(worldPos, shipPosTmp)
            val classification = classifyShipPointWithEpsilon(
                state = state,
                x = shipPosTmp.x,
                y = shipPosTmp.y,
                z = shipPosTmp.z,
                out = shipBlockPosTmp,
            )
            if (isAirPocketClassification(state, classification) ||
                (classification.kind == PointVoidClass.SOLID &&
                    findNearbyAirPocket(state, shipPosTmp, shipBlockPosTmp, radius = 0) != null)
            ) {
                return true
            }
        }

        return false
    }

    /**
     * If [worldBlockPos] is inside a sealed ship air pocket (see [isWorldPosInShipAirPocket]), returns the corresponding
     * shipyard block position for that point. Returns null if the position is not inside a ship air pocket.
     *
     * This is useful when a vanilla behavior tries to place a block into world water (e.g. fire) at a location that is
     * visually "inside the ship", where we actually want the block to exist in the shipyard instead of the world.
     */
    @JvmStatic
    fun getShipBlockPosForWorldPosInShipAirPocket(level: Level, worldBlockPos: BlockPos): BlockPos? {
        if (!VSGameConfig.COMMON.enableAirPockets) return null
        if (level.isBlockInShipyard(worldBlockPos)) return null

        val queryAabb = tmpQueryAabb.get().apply {
            minX = worldBlockPos.x.toDouble()
            minY = worldBlockPos.y.toDouble()
            minZ = worldBlockPos.z.toDouble()
            maxX = (worldBlockPos.x + 1).toDouble()
            maxY = (worldBlockPos.y + 1).toDouble()
            maxZ = (worldBlockPos.z + 1).toDouble()
        }
        val worldPos = tmpWorldPos.get().set(
            worldBlockPos.x + 0.5,
            worldBlockPos.y + 0.5,
            worldBlockPos.z + 0.5
        )
        val shipPosTmp = tmpShipPos.get()
        val shipBlockPosTmp = tmpShipBlockPos.get()

        for (ship in getIntersectingShipsCached(level, worldBlockPos, queryAabb)) {
            val state = getState(level, ship.id) ?: continue
            markClientStateDemanded(level, state)
            val shipTransform = getQueryTransform(ship)

            shipTransform.worldToShip.transformPosition(worldPos, shipPosTmp)
            val classification = classifyShipPointWithEpsilon(
                state = state,
                x = shipPosTmp.x,
                y = shipPosTmp.y,
                z = shipPosTmp.z,
                out = shipBlockPosTmp,
            )
            if (isAirPocketClassification(state, classification)) {
                return BlockPos(classification.voxelX, classification.voxelY, classification.voxelZ)
            }
            if (classification.kind == PointVoidClass.SOLID) {
                val fallback = findNearbyAirPocket(state, shipPosTmp, shipBlockPosTmp, radius = 0) ?: continue
                return BlockPos(fallback.x, fallback.y, fallback.z)
            }
        }

        return null
    }

    private fun getState(level: Level, shipId: Long): ShipPocketState? {
        val map = if (level.isClientSide) clientStates else serverStates
        return map[level.dimensionId]?.get(shipId)
    }

    private fun isInterior(state: ShipPocketState, shipPos: BlockPos): Boolean {
        val lx = shipPos.x - state.minX
        val ly = shipPos.y - state.minY
        val lz = shipPos.z - state.minZ
        if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
        val idx = indexOf(state, lx, ly, lz)
        return state.interior.get(idx)
    }

    private fun isOpen(state: ShipPocketState, shipPos: BlockPos): Boolean {
        val lx = shipPos.x - state.minX
        val ly = shipPos.y - state.minY
        val lz = shipPos.z - state.minZ
        if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
        val idx = indexOf(state, lx, ly, lz)
        return state.open.get(idx)
    }

    private fun isAirPocket(state: ShipPocketState, shipPos: BlockPos): Boolean {
        val lx = shipPos.x - state.minX
        val ly = shipPos.y - state.minY
        val lz = shipPos.z - state.minZ
        if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
        val idx = indexOf(state, lx, ly, lz)
        // "Air pocket" is only meaningful for watertight interior cells (not exterior air above the waterline).
        return state.interior.get(idx) && !state.materializedWater.get(idx)
    }

    private fun findShipFluidAtShipPoint(
        level: Level,
        shipPos: Vector3d,
        shipBlockPos: BlockPos.MutableBlockPos,
        queryCache: FluidStateManager.QueryCache? = null,
    ): FluidState {
        val baseX = shipBlockPos.x
        val baseY = shipBlockPos.y
        val baseZ = shipBlockPos.z

        var shipFluid = FluidStateManager.getBlockState(level, shipBlockPos, queryCache).fluidState
        if (!shipFluid.isEmpty) return shipFluid

        val e = POINT_QUERY_EPS
        for (dxi in -1..1) {
            val dx = dxi.toDouble() * e
            for (dyi in -1..1) {
                val dy = dyi.toDouble() * e
                for (dzi in -1..1) {
                    val dz = dzi.toDouble() * e
                    if (dxi == 0 && dyi == 0 && dzi == 0) continue
                    shipBlockPos.set(
                        Mth.floor(shipPos.x + dx),
                        Mth.floor(shipPos.y + dy),
                        Mth.floor(shipPos.z + dz),
                    )
                    shipFluid = FluidStateManager.getBlockState(level, shipBlockPos, queryCache).fluidState
                    if (!shipFluid.isEmpty) return shipFluid
                }
            }
        }

        shipBlockPos.set(baseX, baseY, baseZ)
        return shipFluid
    }

    private fun indexOf(state: ShipPocketState, lx: Int, ly: Int, lz: Int): Int =
        lx + state.sizeX * (ly + state.sizeY * lz)

    private fun captureDisplacementAirPhysicsSnapshot(state: ShipPocketState): DisplacementAirPhysicsSnapshot {
        val mask = state.interior.clone() as BitSet
        mask.andNot(state.materializedWater)
        return DisplacementAirPhysicsSnapshot(
            minX = state.minX,
            minY = state.minY,
            minZ = state.minZ,
            sizeX = state.sizeX,
            sizeY = state.sizeY,
            sizeZ = state.sizeZ,
            mask = mask,
        )
    }

    private fun syncDisplacementAirPhysics(
        level: ServerLevel,
        previous: DisplacementAirPhysicsSnapshot,
        state: ShipPocketState,
    ) {
        val next = captureDisplacementAirPhysicsSnapshot(state)
        val pos = BlockPos.MutableBlockPos()
        val airType = ValkyrienSkiesMod.vsCore.blockTypes.air
        val displacementAirType = ValkyrienSkiesMod.vsCore.blockTypes.displacementAir

        fun sendUpdate(oldIsDisplacementAir: Boolean, newIsDisplacementAir: Boolean) {
            if (oldIsDisplacementAir == newIsDisplacementAir || !level.getBlockState(pos).isAir) return
            level.shipObjectWorld.onSetBlock(
                pos.x,
                pos.y,
                pos.z,
                level.dimensionId,
                if (oldIsDisplacementAir) displacementAirType else airType,
                if (newIsDisplacementAir) displacementAirType else airType,
                0.0,
                0.0,
            )
        }

        var idx = previous.mask.nextSetBit(0)
        while (idx >= 0) {
            posFromSnapshotIndex(previous, idx, pos)
            sendUpdate(
                oldIsDisplacementAir = true,
                newIsDisplacementAir = snapshotContains(next, pos),
            )
            idx = previous.mask.nextSetBit(idx + 1)
        }

        idx = next.mask.nextSetBit(0)
        while (idx >= 0) {
            posFromSnapshotIndex(next, idx, pos)
            if (!snapshotContains(previous, pos)) {
                sendUpdate(
                    oldIsDisplacementAir = false,
                    newIsDisplacementAir = true,
                )
            }
            idx = next.mask.nextSetBit(idx + 1)
        }
    }

    private fun snapshotContains(snapshot: DisplacementAirPhysicsSnapshot, pos: BlockPos): Boolean {
        val lx = pos.x - snapshot.minX
        val ly = pos.y - snapshot.minY
        val lz = pos.z - snapshot.minZ
        if (lx !in 0 until snapshot.sizeX ||
            ly !in 0 until snapshot.sizeY ||
            lz !in 0 until snapshot.sizeZ
        ) {
            return false
        }
        return snapshot.mask.get(lx + snapshot.sizeX * (ly + snapshot.sizeY * lz))
    }

    private fun posFromSnapshotIndex(
        snapshot: DisplacementAirPhysicsSnapshot,
        idx: Int,
        out: BlockPos.MutableBlockPos,
    ): BlockPos.MutableBlockPos {
        val lx = idx % snapshot.sizeX
        val t = idx / snapshot.sizeX
        val ly = t % snapshot.sizeY
        val lz = t / snapshot.sizeY
        return out.set(snapshot.minX + lx, snapshot.minY + ly, snapshot.minZ + lz)
    }

    private fun posFromIndex(state: ShipPocketState, idx: Int, out: BlockPos.MutableBlockPos): BlockPos.MutableBlockPos {
        val sx = state.sizeX
        val sy = state.sizeY
        val lx = idx % sx
        val t = idx / sx
        val ly = t % sy
        val lz = t / sy
        return out.set(state.minX + lx, state.minY + ly, state.minZ + lz)
    }

    private fun getQueryTransform(ship: Ship): ShipTransform {
        return ship.transform
    }

    private fun canonicalFloodSource(fluid: Fluid): Fluid = floodCanonicalSource(fluid)

    private fun isWaterloggableForFlood(state: BlockState, floodFluid: Fluid): Boolean =
        isWaterloggableFloodState(state, floodFluid)

    private fun countsAsMaterializedFloodFluid(state: BlockState, floodFluid: Fluid): Boolean =
        run {
            val currentFluid = state.fluidState
            if (currentFluid.isEmpty) return@run false
            if (canonicalFloodSource(currentFluid.type) != canonicalFloodSource(floodFluid)) return@run false
            if (state.block is LiquidBlock) return@run true
            isWaterloggableFloodState(state, floodFluid) && state.getValue(BlockStateProperties.WATERLOGGED)
        }

    private fun computeWaterReachableWithPressure(
        level: Level?,
        minX: Int,
        minY: Int,
        minZ: Int,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
        open: BitSet,
        interior: BitSet,
        outsideVoid: BitSet? = null,
        shipTransform: ShipTransform?,
        out: BitSet,
        exteriorOpen: BitSet? = null,
        buoyancyOut: BuoyancyMetrics? = null,
        materializedWater: BitSet? = null,
        floodFluidOut: AtomicReference<Fluid?>? = null,
        faceCondXP: ShortArray? = null,
        faceCondYP: ShortArray? = null,
        faceCondZP: ShortArray? = null,
        templatePalette: List<ShapeCellTemplate>? = null,
        templateIndexByVoxel: IntArray? = null,
        voxelExteriorComponentMask: LongArray? = null,
        voxelInteriorComponentMask: LongArray? = null,
        precomputedSubmerged: BitSet? = null,
        precomputedSubmergedCoverage: DoubleArray? = null,
        precomputedBuoyancySubmergedCoverage: DoubleArray? = null,
        precomputedDominantFloodFluid: Fluid? = null,
        precomputedSurfaceYByCell: DoubleArray? = null,
        precomputedOpeningFaceSamples: Long2ObjectOpenHashMap<OpeningFaceCoverageSnapshot>? = null,
        precomputedAffine: WorldYAffine? = null,
        allowWorldSurfaceScan: Boolean = true,
    ): BitSet {
        out.clear()
        val queryCache = if (level != null) tmpChunkQueryCache.get().apply { reset() } else null

        val buoyancy = buoyancyOut
        buoyancy?.reset()

        val volumeLong = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
        if (volumeLong <= 0 || volumeLong > MAX_SIM_VOLUME.toLong()) return out
        if (open.isEmpty) return out

        val volume = volumeLong.toInt()

        var componentQueue = tmpFloodQueue.get()
        if (componentQueue.size < volume) {
            componentQueue = IntArray(volume)
            tmpFloodQueue.set(componentQueue)
        }

        var waterQueue = tmpPressureHeapIdx.get()
        if (waterQueue.size < volume) {
            waterQueue = IntArray(volume)
            tmpPressureHeapIdx.set(waterQueue)
        }

        val interiorVisited = tmpPressureComponentVisited.get()
        interiorVisited.clear()

        val submerged = tmpPressureSubmerged.get()
        submerged.clear()

        val worldPosTmp = if (level != null) tmpWorldPos.get() else null
        val shipPosTmp = if (level != null) tmpShipPos.get() else null
        val shipBlockPos = if (level != null) tmpShipBlockPos.get() else null
        val worldBlockPos = if (level != null) BlockPos.MutableBlockPos() else null

        var submergedCoverage = tmpSubmergedCoverage.get()
        if (submergedCoverage.size < volume) {
            submergedCoverage = DoubleArray(volume)
            tmpSubmergedCoverage.set(submergedCoverage)
        } else {
            java.util.Arrays.fill(submergedCoverage, 0, volume, 0.0)
        }
        var dominantFloodFluid: Fluid? = precomputedDominantFloodFluid
        if (precomputedSubmerged != null &&
            precomputedSubmergedCoverage != null &&
            precomputedSubmergedCoverage.size >= volume
        ) {
            submerged.or(precomputedSubmerged)
            java.lang.System.arraycopy(precomputedSubmergedCoverage, 0, submergedCoverage, 0, volume)
        } else {
            val sampledLevel = level
            val sampledTransform = shipTransform
            val sampledWorldPosTmp = worldPosTmp
            val sampledShipPosTmp = shipPosTmp
            val sampledShipBlockPos = shipBlockPos
            val sampledWorldBlockPos = worldBlockPos
            if (sampledLevel == null ||
                sampledTransform == null ||
                sampledWorldPosTmp == null ||
                sampledShipPosTmp == null ||
                sampledShipBlockPos == null ||
                sampledWorldBlockPos == null
            ) {
                return out
            }

            val floodFluidScores = HashMap<Fluid, Double>()

            fun shipCellFluidCoverage(idx: Int): FluidCoverageSample {
                val lx = idx % sizeX
                val t = idx / sizeX
                val ly = t % sizeY
                val lz = t / sizeY
                sampledShipBlockPos.set(minX + lx, minY + ly, minZ + lz)
                return getShipCellFluidCoverage(
                    sampledLevel,
                    sampledTransform,
                    sampledShipBlockPos,
                    sampledShipPosTmp,
                    sampledWorldPosTmp,
                    sampledWorldBlockPos,
                    queryCache = queryCache,
                )
            }

            // Cache which open cells are submerged in world fluid, using coverage-aware qualification.
            var idx = open.nextSetBit(0)
            while (idx >= 0 && idx < volume) {
                val coverage = shipCellFluidCoverage(idx)
                val fluid = coverage.canonicalFluid
                if (coverage.isSubmergedAny() && fluid != null) {
                    submergedCoverage[idx] = coverage.coverageRatio
                    val score = if (coverage.isIngressQualified()) {
                        coverage.coverageRatio.coerceAtLeast(SUBMERGED_INGRESS_MIN_COVERAGE)
                    } else {
                        coverage.coverageRatio * 0.25
                    }
                    floodFluidScores[fluid] = (floodFluidScores[fluid] ?: 0.0) + score
                }
                if (coverage.isIngressQualified()) {
                    submerged.set(idx)
                }
                idx = open.nextSetBit(idx + 1)
            }

            var dominantScore = Double.NEGATIVE_INFINITY
            for ((fluid, score) in floodFluidScores) {
                if (score > dominantScore) {
                    dominantScore = score
                    dominantFloodFluid = fluid
                }
            }
        }
        val buoyancyCoverage = if (
            precomputedBuoyancySubmergedCoverage != null &&
            precomputedBuoyancySubmergedCoverage.size >= volume
        ) {
            precomputedBuoyancySubmergedCoverage
        } else {
            submergedCoverage
        }
        if (dominantFloodFluid != null && floodFluidOut != null && floodFluidOut.get() == null) {
            floodFluidOut.set(dominantFloodFluid)
        }

        val strideY = sizeX
        val strideZ = sizeX * sizeY
        val hasFaceConductance =
            faceCondXP != null &&
                faceCondYP != null &&
                faceCondZP != null &&
                faceCondXP.size == volume &&
                faceCondYP.size == volume &&
                faceCondZP.size == volume

        fun edgeCond(idx: Int, lx: Int, ly: Int, lz: Int, dirCode: Int): Int {
            if (!hasFaceConductance) return 1
            return when (dirCode) {
                0 -> if (lx > 0) faceCondXP[idx - 1].toInt() and 0xFFFF else 0
                1 -> if (lx + 1 < sizeX) faceCondXP[idx].toInt() and 0xFFFF else 0
                2 -> if (ly > 0) faceCondYP[idx - strideY].toInt() and 0xFFFF else 0
                3 -> if (ly + 1 < sizeY) faceCondYP[idx].toInt() and 0xFFFF else 0
                4 -> if (lz > 0) faceCondZP[idx - strideZ].toInt() and 0xFFFF else 0
                else -> if (lz + 1 < sizeZ) faceCondZP[idx].toInt() and 0xFFFF else 0
            }
        }

        // "True outside" within the sim bounds: boundary-connected open volume excluding simulationDomain.
        // If not provided, derive it deterministically from geometry connectivity (ignoring micro cracks).
        val outsideVoidMask: BitSet = outsideVoid ?: run {
            val passCond = if (hasFaceConductance) MIN_OPENING_CONDUCTANCE else 1
            val outsideCandidates = open.clone() as BitSet
            outsideCandidates.andNot(interior)
            if (outsideCandidates.isEmpty) {
                BitSet(volume)
            } else {
                val visited = BitSet(volume)
                var head = 0
                var tail = 0

                fun tryEnqueue(i: Int) {
                    if (i < 0 || i >= volume) return
                    if (!outsideCandidates.get(i) || visited.get(i)) return
                    visited.set(i)
                    componentQueue[tail++] = i
                }

                forEachBoundaryIndexGraph(sizeX, sizeY, sizeZ) { boundaryIdx ->
                    tryEnqueue(boundaryIdx)
                }

                fun trySpread(cur: Int, lx: Int, ly: Int, lz: Int, n: Int, dirCode: Int) {
                    if (n < 0 || n >= volume) return
                    if (!outsideCandidates.get(n) || visited.get(n)) return
                    if (edgeCond(cur, lx, ly, lz, dirCode) < passCond) return
                    visited.set(n)
                    componentQueue[tail++] = n
                }

                while (head < tail) {
                    val cur = componentQueue[head++]
                    val lx = cur % sizeX
                    val t = cur / sizeX
                    val ly = t % sizeY
                    val lz = t / sizeY

                    if (lx > 0) trySpread(cur, lx, ly, lz, cur - 1, 0)
                    if (lx + 1 < sizeX) trySpread(cur, lx, ly, lz, cur + 1, 1)
                    if (ly > 0) trySpread(cur, lx, ly, lz, cur - strideY, 2)
                    if (ly + 1 < sizeY) trySpread(cur, lx, ly, lz, cur + strideY, 3)
                    if (lz > 0) trySpread(cur, lx, ly, lz, cur - strideZ, 4)
                    if (lz + 1 < sizeZ) trySpread(cur, lx, ly, lz, cur + strideZ, 5)
                }

                visited
            }
        }

        val hasTemplateConnectivity =
            templatePalette != null &&
                templatePalette.isNotEmpty() &&
                templateIndexByVoxel != null &&
                templateIndexByVoxel.size == volume &&
                voxelExteriorComponentMask != null &&
                voxelExteriorComponentMask.size >= volume &&
                voxelInteriorComponentMask != null &&
                voxelInteriorComponentMask.size >= volume

        fun filteredEdgeCond(
            idxCur: Int,
            idxNeighbor: Int,
            lx: Int,
            ly: Int,
            lz: Int,
            dirCode: Int,
            componentMaskCur: Long = -1L,
            componentMaskNeighbor: Long = -1L,
        ): Int {
            if (componentMaskCur == 0L || componentMaskNeighbor == 0L) return 0
            if (!hasTemplateConnectivity) {
                return edgeCond(idxCur, lx, ly, lz, dirCode)
            }

            val palette = templatePalette ?: return edgeCond(idxCur, lx, ly, lz, dirCode)
            val templateIdxArr = templateIndexByVoxel ?: return edgeCond(idxCur, lx, ly, lz, dirCode)
            val templateIdxCur = templateIdxArr[idxCur]
            val templateIdxNeighbor = templateIdxArr[idxNeighbor]
            if (templateIdxCur !in palette.indices || templateIdxNeighbor !in palette.indices) {
                return edgeCond(idxCur, lx, ly, lz, dirCode)
            }

            return computeTemplateFaceConductance(
                templateA = palette[templateIdxCur],
                templateB = palette[templateIdxNeighbor],
                dirCodeFromA = dirCode,
                componentMaskA = componentMaskCur,
                componentMaskB = componentMaskNeighbor,
            )
        }

        // Compute an affine map from local ship voxel coords -> world Y. This is much faster than per-point transforms.
        val affine = precomputedAffine ?: run {
            val sampledTransform = shipTransform ?: return out
            val sampledShipPosTmp = shipPosTmp ?: return out
            val sampledWorldPosTmp = worldPosTmp ?: return out
            computeWorldYAffine(
                minX = minX,
                minY = minY,
                minZ = minZ,
                shipTransform = sampledTransform,
                shipPosTmp = sampledShipPosTmp,
                worldPosTmp = sampledWorldPosTmp,
            )
        }
        val baseWorldY = affine.baseWorldY
        val incX = affine.incX
        val incY = affine.incY
        val incZ = affine.incZ

        fun worldYAtLocal(x: Double, y: Double, z: Double): Double {
            return baseWorldY + incX * x + incY * y + incZ * z
        }

        fun cellCenterWorldY(lx: Int, ly: Int, lz: Int): Double {
            return baseWorldY + incX * (lx + 0.5) + incY * (ly + 0.5) + incZ * (lz + 0.5)
        }

        fun openingFaceTopWorldYFromCorners(lx: Int, ly: Int, lz: Int, outDirCode: Int): Double {
            val x0 = lx.toDouble()
            val y0 = ly.toDouble()
            val z0 = lz.toDouble()
            val x1 = x0 + 1.0
            val y1 = y0 + 1.0
            val z1 = z0 + 1.0

            return when (outDirCode) {
                0 -> maxOf(
                    worldYAtLocal(x0, y0, z0),
                    worldYAtLocal(x0, y1, z0),
                    worldYAtLocal(x0, y0, z1),
                    worldYAtLocal(x0, y1, z1),
                )
                1 -> maxOf(
                    worldYAtLocal(x1, y0, z0),
                    worldYAtLocal(x1, y1, z0),
                    worldYAtLocal(x1, y0, z1),
                    worldYAtLocal(x1, y1, z1),
                )
                2 -> maxOf(
                    worldYAtLocal(x0, y0, z0),
                    worldYAtLocal(x1, y0, z0),
                    worldYAtLocal(x0, y0, z1),
                    worldYAtLocal(x1, y0, z1),
                )
                3 -> maxOf(
                    worldYAtLocal(x0, y1, z0),
                    worldYAtLocal(x1, y1, z0),
                    worldYAtLocal(x0, y1, z1),
                    worldYAtLocal(x1, y1, z1),
                )
                4 -> maxOf(
                    worldYAtLocal(x0, y0, z0),
                    worldYAtLocal(x1, y0, z0),
                    worldYAtLocal(x0, y1, z0),
                    worldYAtLocal(x1, y1, z0),
                )
                else -> maxOf(
                    worldYAtLocal(x0, y0, z1),
                    worldYAtLocal(x1, y0, z1),
                    worldYAtLocal(x0, y1, z1),
                    worldYAtLocal(x1, y1, z1),
                )
            }
        }

        fun openingFaceTopWorldY(
            curIdx: Int,
            lx: Int,
            ly: Int,
            lz: Int,
            nIdx: Int,
            outDirCode: Int,
            componentMaskCur: Long = -1L,
            componentMaskNeighbor: Long = -1L,
        ): Double {
            val fallback = openingFaceTopWorldYFromCorners(lx, ly, lz, outDirCode)
            if (!hasTemplateConnectivity) return fallback
            if (curIdx !in 0 until volume || nIdx !in 0 until volume) return fallback

            val palette = templatePalette ?: return fallback
            val templateIdxArr = templateIndexByVoxel ?: return fallback
            if (templateIdxArr.size != volume || palette.isEmpty()) return fallback

            val templateCurIdx = templateIdxArr[curIdx]
            val templateNeighborIdx = templateIdxArr[nIdx]
            if (templateCurIdx !in palette.indices || templateNeighborIdx !in palette.indices) return fallback

            val templateCur = palette[templateCurIdx]
            val templateNeighbor = palette[templateNeighborIdx]
            val faceCur = when (outDirCode) {
                0 -> SHAPE_FACE_NEG_X
                1 -> SHAPE_FACE_POS_X
                2 -> SHAPE_FACE_NEG_Y
                3 -> SHAPE_FACE_POS_Y
                4 -> SHAPE_FACE_NEG_Z
                else -> SHAPE_FACE_POS_Z
            }
            val faceNeighbor = when (faceCur) {
                SHAPE_FACE_NEG_X -> SHAPE_FACE_POS_X
                SHAPE_FACE_POS_X -> SHAPE_FACE_NEG_X
                SHAPE_FACE_NEG_Y -> SHAPE_FACE_POS_Y
                SHAPE_FACE_POS_Y -> SHAPE_FACE_NEG_Y
                SHAPE_FACE_NEG_Z -> SHAPE_FACE_POS_Z
                else -> SHAPE_FACE_NEG_Z
            }
            val faceOffsetCur = faceCur * SHAPE_FACE_SAMPLE_COUNT
            val faceOffsetNeighbor = faceNeighbor * SHAPE_FACE_SAMPLE_COUNT

            var bestY = Double.NEGATIVE_INFINITY
            for (sampleIdx in 0 until SHAPE_FACE_SAMPLE_COUNT) {
                val componentCur = templateCur.faceSampleComponent[faceOffsetCur + sampleIdx].toInt()
                if (componentCur < 0) continue
                if (componentMaskCur != -1L && ((componentMaskCur ushr componentCur) and 1L) == 0L) continue

                val componentNeighbor = templateNeighbor.faceSampleComponent[faceOffsetNeighbor + sampleIdx].toInt()
                if (componentNeighbor < 0) continue
                if (componentMaskNeighbor != -1L &&
                    ((componentMaskNeighbor ushr componentNeighbor) and 1L) == 0L
                ) {
                    continue
                }

                val u = sampleIdx and (SHAPE_FACE_SAMPLE_RES - 1)
                val v = sampleIdx ushr 3
                val du = (u + 0.5) / SHAPE_FACE_SAMPLE_RES.toDouble()
                val dv = (v + 0.5) / SHAPE_FACE_SAMPLE_RES.toDouble()

                val sampleX: Double
                val sampleY: Double
                val sampleZ: Double
                when (outDirCode) {
                    0 -> {
                        sampleX = lx.toDouble()
                        sampleY = ly + du
                        sampleZ = lz + dv
                    }
                    1 -> {
                        sampleX = lx + 1.0
                        sampleY = ly + du
                        sampleZ = lz + dv
                    }
                    2 -> {
                        sampleX = lx + du
                        sampleY = ly.toDouble()
                        sampleZ = lz + dv
                    }
                    3 -> {
                        sampleX = lx + du
                        sampleY = ly + 1.0
                        sampleZ = lz + dv
                    }
                    4 -> {
                        sampleX = lx + du
                        sampleY = ly + dv
                        sampleZ = lz.toDouble()
                    }
                    else -> {
                        sampleX = lx + du
                        sampleY = ly + dv
                        sampleZ = lz + 1.0
                    }
                }

                val sampleWorldY = worldYAtLocal(sampleX, sampleY, sampleZ)
                if (sampleWorldY > bestY) bestY = sampleWorldY
            }

            return if (bestY.isFinite()) bestY else fallback
        }

        data class OpeningFaceCoverageCacheKey(
            val faceKey: Long,
            val componentMaskCur: Long,
            val componentMaskNeighbor: Long,
        )

        val openingFaceCoverageCache = HashMap<OpeningFaceCoverageCacheKey, OpeningFaceFluidCoverageSample>()

        fun sampleOpeningFaceFluidCoverage(
            curIdx: Int,
            lx: Int,
            ly: Int,
            lz: Int,
            nIdx: Int,
            outDirCode: Int,
            componentMaskCur: Long = -1L,
            componentMaskNeighbor: Long = -1L,
        ): OpeningFaceFluidCoverageSample {
            val cacheKey = OpeningFaceCoverageCacheKey(
                faceKey = (curIdx.toLong() shl 3) or (outDirCode.toLong() and 7L),
                componentMaskCur = componentMaskCur,
                componentMaskNeighbor = componentMaskNeighbor,
            )
            val cached = openingFaceCoverageCache[cacheKey]
            if (cached != null) {
                return cached
            }

            val faceTopY = openingFaceTopWorldY(
                curIdx = curIdx,
                lx = lx,
                ly = ly,
                lz = lz,
                nIdx = nIdx,
                outDirCode = outDirCode,
                componentMaskCur = componentMaskCur,
                componentMaskNeighbor = componentMaskNeighbor,
            )

            if (level == null || shipTransform == null || shipPosTmp == null || worldPosTmp == null || worldBlockPos == null) {
                val key = (curIdx.toLong() shl 3) or (outDirCode.toLong() and 7L)
                val precomputed = precomputedOpeningFaceSamples?.get(key)
                if (precomputed != null) {
                    val result = OpeningFaceFluidCoverageSample(
                        canonicalFluid = precomputed.canonicalFluid,
                        coverageRatio = precomputed.coverageRatio.coerceIn(0.0, 1.0),
                        centerSubmerged = precomputed.centerSubmerged,
                        faceTopWorldY = precomputed.faceTopWorldY,
                        estimatedSurfaceY = precomputed.estimatedSurfaceY,
                    )
                    openingFaceCoverageCache[cacheKey] = result
                    return result
                }
                val fallbackCount = asyncOpeningFaceFallbackCount.incrementAndGet()
                logThrottledDiag(
                    fallbackCount,
                    "Missing precomputed opening-face sample in async solver curIdx={} dir={} nIdx={}",
                    curIdx,
                    outDirCode,
                    nIdx,
                )
                val result = OpeningFaceFluidCoverageSample(
                    canonicalFluid = null,
                    coverageRatio = 0.0,
                    centerSubmerged = false,
                    faceTopWorldY = faceTopY,
                    estimatedSurfaceY = null,
                )
                openingFaceCoverageCache[cacheKey] = result
                return result
            }

            val result = withBypassedFluidOverrides {
                val sampledFluids = arrayOfNulls<Fluid>(5)
                val sampledCounts = IntArray(5)
                var sampledFluidCount = 0
                var submergedSamples = 0
                var centerFluid: Fluid? = null

                val faceOffset = 1.0e-4
                val lo = 1.0e-4
                val hi = 1.0 - lo

                fun sampleAt(u: Double, v: Double, isCenter: Boolean) {
                    val localX: Double
                    val localY: Double
                    val localZ: Double
                    when (outDirCode) {
                        0 -> {
                            localX = lx - faceOffset
                            localY = ly + u
                            localZ = lz + v
                        }
                        1 -> {
                            localX = lx + 1.0 + faceOffset
                            localY = ly + u
                            localZ = lz + v
                        }
                        2 -> {
                            localX = lx + u
                            localY = ly - faceOffset
                            localZ = lz + v
                        }
                        3 -> {
                            localX = lx + u
                            localY = ly + 1.0 + faceOffset
                            localZ = lz + v
                        }
                        4 -> {
                            localX = lx + u
                            localY = ly + v
                            localZ = lz - faceOffset
                        }
                        else -> {
                            localX = lx + u
                            localY = ly + v
                            localZ = lz + 1.0 + faceOffset
                        }
                    }

                    val fluid = sampleCanonicalWorldFluidAtShipPoint(
                        level = level,
                        shipTransform = shipTransform,
                        shipX = minX + localX,
                        shipY = minY + localY,
                        shipZ = minZ + localZ,
                        shipPosTmp = shipPosTmp,
                        worldPosTmp = worldPosTmp,
                        worldBlockPos = worldBlockPos,
                        queryCache = queryCache,
                    )
                    if (isCenter) centerFluid = fluid
                    if (fluid == null) return

                    submergedSamples++
                    for (i in 0 until sampledFluidCount) {
                        if (sampledFluids[i] == fluid) {
                            sampledCounts[i]++
                            return
                        }
                    }
                    if (sampledFluidCount < sampledFluids.size) {
                        sampledFluids[sampledFluidCount] = fluid
                        sampledCounts[sampledFluidCount] = 1
                        sampledFluidCount++
                    } else {
                        sampledCounts[0]++
                    }
                }

                sampleAt(0.5, 0.5, isCenter = true)
                sampleAt(lo, lo, isCenter = false)
                sampleAt(hi, lo, isCenter = false)
                sampleAt(lo, hi, isCenter = false)
                sampleAt(hi, hi, isCenter = false)

                var bestFluid: Fluid? = null
                var bestCount = 0
                for (i in 0 until sampledFluidCount) {
                    val fluid = sampledFluids[i] ?: continue
                    val count = sampledCounts[i]
                    if (count > bestCount || (count == bestCount && centerFluid != null && fluid == centerFluid)) {
                        bestCount = count
                        bestFluid = fluid
                    }
                }

                val ratio = if (submergedSamples <= 0 || bestCount <= 0) 0.0 else (bestCount / 5.0).coerceIn(0.0, 1.0)
                val centerSubmerged = centerFluid != null && bestFluid != null && centerFluid == bestFluid
                val centerLocalX: Double
                val centerLocalY: Double
                val centerLocalZ: Double
                when (outDirCode) {
                    0 -> {
                        centerLocalX = lx - faceOffset
                        centerLocalY = ly + 0.5
                        centerLocalZ = lz + 0.5
                    }
                    1 -> {
                        centerLocalX = lx + 1.0 + faceOffset
                        centerLocalY = ly + 0.5
                        centerLocalZ = lz + 0.5
                    }
                    2 -> {
                        centerLocalX = lx + 0.5
                        centerLocalY = ly - faceOffset
                        centerLocalZ = lz + 0.5
                    }
                    3 -> {
                        centerLocalX = lx + 0.5
                        centerLocalY = ly + 1.0 + faceOffset
                        centerLocalZ = lz + 0.5
                    }
                    4 -> {
                        centerLocalX = lx + 0.5
                        centerLocalY = ly + 0.5
                        centerLocalZ = lz - faceOffset
                    }
                    else -> {
                        centerLocalX = lx + 0.5
                        centerLocalY = ly + 0.5
                        centerLocalZ = lz + 1.0 + faceOffset
                    }
                }
                val centerShipX = minX + centerLocalX
                val centerShipY = minY + centerLocalY
                val centerShipZ = minZ + centerLocalZ
                val estimatedSurfaceY = if (bestFluid != null) {
                    estimateExteriorFluidSurfaceYAtShipPoint(
                        level = level,
                        shipTransform = shipTransform,
                        shipX = centerShipX,
                        shipY = centerShipY,
                        shipZ = centerShipZ,
                        sampleFluid = bestFluid,
                        shipPosTmp = shipPosTmp,
                        worldPosTmp = worldPosTmp,
                        worldBlockPos = worldBlockPos,
                        queryCache = queryCache,
                    )
                } else {
                    null
                }

                OpeningFaceFluidCoverageSample(
                    canonicalFluid = bestFluid,
                    coverageRatio = ratio,
                    centerSubmerged = centerSubmerged,
                    faceTopWorldY = faceTopY,
                    estimatedSurfaceY = estimatedSurfaceY,
                )
            }
            openingFaceCoverageCache[cacheKey] = result
            return result
        }

        // 1) Flood-fill exterior world water. This ensures we never cull ocean water around the ship.
        run {
            var head = 0
            var tail = 0

            fun tryEnqueueExterior(i: Int) {
                // Only treat "true outside" as exterior for water reachability.
                if (!outsideVoidMask.get(i)) return
                if (!submerged.get(i) || out.get(i)) return
                out.set(i)
                componentQueue[tail++] = i
            }

            forEachBoundaryIndexGraph(sizeX, sizeY, sizeZ) { boundaryIdx ->
                tryEnqueueExterior(boundaryIdx)
            }

            while (head < tail) {
                val cur = componentQueue[head++]

                val lx = cur % sizeX
                val t = cur / sizeX
                val ly = t % sizeY
                val lz = t / sizeY
                val curExteriorMask = voxelExteriorComponentMask?.let { masks ->
                    if (cur in masks.indices) masks[cur] else 0L
                } ?: -1L

                fun trySpreadExterior(n: Int, dirCode: Int) {
                    if (n < 0 || n >= volume) return
                    if (!outsideVoidMask.get(n)) return
                    val cond = if (hasTemplateConnectivity) {
                        val nExteriorMask = voxelExteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = dirCode,
                            componentMaskCur = curExteriorMask,
                            componentMaskNeighbor = nExteriorMask,
                        )
                    } else {
                        edgeCond(cur, lx, ly, lz, dirCode)
                    }
                    if (cond > 0) {
                        tryEnqueueExterior(n)
                    }
                }

                if (lx > 0) trySpreadExterior(cur - 1, 0)
                if (lx + 1 < sizeX) trySpreadExterior(cur + 1, 1)
                if (ly > 0) trySpreadExterior(cur - strideY, 2)
                if (ly + 1 < sizeY) trySpreadExterior(cur + strideY, 3)
                if (lz > 0) trySpreadExterior(cur - strideZ, 4)
                if (lz + 1 < sizeZ) trySpreadExterior(cur + strideZ, 5)
            }
        }

        // 2) For each interior air-space component, compute holes to outside and fill water to the highest submerged hole.
        val planeEps = 1e-6

        var start = interior.nextSetBit(0)
        while (start >= 0 && start < volume) {
            if (interiorVisited.get(start) || !open.get(start)) {
                start = interior.nextSetBit(start + 1)
                continue
            }

            var head = 0
            var tail = 0
            interiorVisited.set(start)
            componentQueue[tail++] = start

            var hasAirVent = false
            var waterLevel = Double.NEGATIVE_INFINITY
            var seedCount = 0
            var airVentConductance = 0
            var bestSurfaceSampleIdx = -1
            var bestSurfaceSampleY = Double.NEGATIVE_INFINITY
            var componentFloodFluid: Fluid? = dominantFloodFluid?.let { canonicalFloodSource(it) }

            fun processHole(
                curIdx: Int,
                lx: Int,
                ly: Int,
                lz: Int,
                nIdx: Int,
                outDirCode: Int,
                conductance: Int,
                componentMaskCur: Long = -1L,
                componentMaskNeighbor: Long = -1L,
            ) {
                if (conductance <= 0) return
                if (conductance < MIN_OPENING_CONDUCTANCE) {
                    microOpeningFilteredCount.incrementAndGet()
                    return
                }
                if (!open.get(nIdx)) return
                // Only accept openings that connect to "true outside" (not to other non-sim-domain cavities).
                if (!outsideVoidMask.get(nIdx)) return

                val openingSample = sampleOpeningFaceFluidCoverage(
                    curIdx = curIdx,
                    lx = lx,
                    ly = ly,
                    lz = lz,
                    nIdx = nIdx,
                    outDirCode = outDirCode,
                    componentMaskCur = componentMaskCur,
                    componentMaskNeighbor = componentMaskNeighbor,
                )
                val holeFluid = openingSample.canonicalFluid?.let { canonicalFloodSource(it) }
                if (componentFloodFluid == null && holeFluid != null) {
                    componentFloodFluid = holeFluid
                }

                val fluidMatches = holeFluid != null && (componentFloodFluid == null || componentFloodFluid == holeFluid)
                val surfaceY = openingSample.estimatedSurfaceY
                val x0 = lx.toDouble()
                val y0 = ly.toDouble()
                val z0 = lz.toDouble()
                val x1 = x0 + 1.0
                val y1 = y0 + 1.0
                val z1 = z0 + 1.0
                val openingBottomY = when (outDirCode) {
                    0 -> minOf(worldYAtLocal(x0, y0, z0), worldYAtLocal(x0, y1, z0), worldYAtLocal(x0, y0, z1), worldYAtLocal(x0, y1, z1))
                    1 -> minOf(worldYAtLocal(x1, y0, z0), worldYAtLocal(x1, y1, z0), worldYAtLocal(x1, y0, z1), worldYAtLocal(x1, y1, z1))
                    2 -> minOf(worldYAtLocal(x0, y0, z0), worldYAtLocal(x1, y0, z0), worldYAtLocal(x0, y0, z1), worldYAtLocal(x1, y0, z1))
                    3 -> minOf(worldYAtLocal(x0, y1, z0), worldYAtLocal(x1, y1, z0), worldYAtLocal(x0, y1, z1), worldYAtLocal(x1, y1, z1))
                    4 -> minOf(worldYAtLocal(x0, y0, z0), worldYAtLocal(x1, y0, z0), worldYAtLocal(x0, y1, z0), worldYAtLocal(x1, y1, z0))
                    else -> minOf(worldYAtLocal(x0, y0, z1), worldYAtLocal(x1, y0, z1), worldYAtLocal(x0, y1, z1), worldYAtLocal(x1, y1, z1))
                }
                val openingBelowSurface =
                    surfaceY == null || openingBottomY <= surfaceY + FLOOD_OPENING_LEVEL_EPS
                val openingSubmerged = openingSample.isSubmergedAny() && fluidMatches && openingBelowSurface

                if (openingSubmerged) {
                    // Submerged hull opening: water can enter. Track the highest submerged opening as the fill level.
                    waterLevel = maxOf(waterLevel, openingSample.faceTopWorldY)
                    if (seedCount < waterQueue.size) {
                        waterQueue[seedCount++] = curIdx
                    }

                    // Representative "near-surface" sample point for estimating exterior water pressure.
                    // (Choosing the highest submerged opening tends to reduce the scan distance to the fluid surface.)
                    val sampleY = cellCenterWorldY(lx, ly, lz)
                    if (sampleY > bestSurfaceSampleY) {
                        bestSurfaceSampleY = sampleY
                        bestSurfaceSampleIdx = curIdx
                    }
                } else if (!openingSample.isSubmergedAny()) {
                    // Non-submerged opening to the exterior air: air can escape, so the pocket is unpressurized.
                    airVentConductance += conductance
                }
            }

            fun enqueueInterior(i: Int) {
                if (!open.get(i) || !interior.get(i) || interiorVisited.get(i)) return
                interiorVisited.set(i)
                componentQueue[tail++] = i
            }

            while (head < tail) {
                val cur = componentQueue[head++]

                val lx = cur % sizeX
                val t = cur / sizeX
                val ly = t % sizeY
                val lz = t / sizeY
                val curInteriorMask = voxelInteriorComponentMask?.let { masks ->
                    if (cur in masks.indices) masks[cur] else 0L
                } ?: -1L

                if (lx > 0) {
                    val n = cur - 1
                    if (hasTemplateConnectivity) {
                        val neighborInteriorMask = voxelInteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condInterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 0,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborInteriorMask,
                        )
                        if (condInterior > 0) enqueueInterior(n)

                        val neighborExteriorMask = voxelExteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condExterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 0,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborExteriorMask,
                        )
                        if (condExterior > 0) {
                            processHole(cur, lx, ly, lz, n, 0, condExterior, curInteriorMask, neighborExteriorMask)
                        }
                    } else {
                        val cond = edgeCond(cur, lx, ly, lz, 0)
                        if (cond > 0) {
                            if (interior.get(n)) enqueueInterior(n) else processHole(cur, lx, ly, lz, n, 0, cond, -1L, -1L)
                        }
                    }
                }
                if (lx + 1 < sizeX) {
                    val n = cur + 1
                    if (hasTemplateConnectivity) {
                        val neighborInteriorMask = voxelInteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condInterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 1,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborInteriorMask,
                        )
                        if (condInterior > 0) enqueueInterior(n)

                        val neighborExteriorMask = voxelExteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condExterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 1,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborExteriorMask,
                        )
                        if (condExterior > 0) {
                            processHole(cur, lx, ly, lz, n, 1, condExterior, curInteriorMask, neighborExteriorMask)
                        }
                    } else {
                        val cond = edgeCond(cur, lx, ly, lz, 1)
                        if (cond > 0) {
                            if (interior.get(n)) enqueueInterior(n) else processHole(cur, lx, ly, lz, n, 1, cond, -1L, -1L)
                        }
                    }
                }
                if (ly > 0) {
                    val n = cur - strideY
                    if (hasTemplateConnectivity) {
                        val neighborInteriorMask = voxelInteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condInterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 2,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborInteriorMask,
                        )
                        if (condInterior > 0) enqueueInterior(n)

                        val neighborExteriorMask = voxelExteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condExterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 2,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborExteriorMask,
                        )
                        if (condExterior > 0) {
                            processHole(cur, lx, ly, lz, n, 2, condExterior, curInteriorMask, neighborExteriorMask)
                        }
                    } else {
                        val cond = edgeCond(cur, lx, ly, lz, 2)
                        if (cond > 0) {
                            if (interior.get(n)) enqueueInterior(n) else processHole(cur, lx, ly, lz, n, 2, cond, -1L, -1L)
                        }
                    }
                }
                if (ly + 1 < sizeY) {
                    val n = cur + strideY
                    if (hasTemplateConnectivity) {
                        val neighborInteriorMask = voxelInteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condInterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 3,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborInteriorMask,
                        )
                        if (condInterior > 0) enqueueInterior(n)

                        val neighborExteriorMask = voxelExteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condExterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 3,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborExteriorMask,
                        )
                        if (condExterior > 0) {
                            processHole(cur, lx, ly, lz, n, 3, condExterior, curInteriorMask, neighborExteriorMask)
                        }
                    } else {
                        val cond = edgeCond(cur, lx, ly, lz, 3)
                        if (cond > 0) {
                            if (interior.get(n)) enqueueInterior(n) else processHole(cur, lx, ly, lz, n, 3, cond, -1L, -1L)
                        }
                    }
                }
                if (lz > 0) {
                    val n = cur - strideZ
                    if (hasTemplateConnectivity) {
                        val neighborInteriorMask = voxelInteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condInterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 4,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborInteriorMask,
                        )
                        if (condInterior > 0) enqueueInterior(n)

                        val neighborExteriorMask = voxelExteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condExterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 4,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborExteriorMask,
                        )
                        if (condExterior > 0) {
                            processHole(cur, lx, ly, lz, n, 4, condExterior, curInteriorMask, neighborExteriorMask)
                        }
                    } else {
                        val cond = edgeCond(cur, lx, ly, lz, 4)
                        if (cond > 0) {
                            if (interior.get(n)) enqueueInterior(n) else processHole(cur, lx, ly, lz, n, 4, cond, -1L, -1L)
                        }
                    }
                }
                if (lz + 1 < sizeZ) {
                    val n = cur + strideZ
                    if (hasTemplateConnectivity) {
                        val neighborInteriorMask = voxelInteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condInterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 5,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborInteriorMask,
                        )
                        if (condInterior > 0) enqueueInterior(n)

                        val neighborExteriorMask = voxelExteriorComponentMask?.let { masks ->
                            if (n in masks.indices) masks[n] else 0L
                        } ?: 0L
                        val condExterior = filteredEdgeCond(
                            idxCur = cur,
                            idxNeighbor = n,
                            lx = lx,
                            ly = ly,
                            lz = lz,
                            dirCode = 5,
                            componentMaskCur = curInteriorMask,
                            componentMaskNeighbor = neighborExteriorMask,
                        )
                        if (condExterior > 0) {
                            processHole(cur, lx, ly, lz, n, 5, condExterior, curInteriorMask, neighborExteriorMask)
                        }
                    } else {
                        val cond = edgeCond(cur, lx, ly, lz, 5)
                        if (cond > 0) {
                            if (interior.get(n)) enqueueInterior(n) else processHole(cur, lx, ly, lz, n, 5, cond, -1L, -1L)
                        }
                    }
                }
            }

            hasAirVent = airVentConductance > 0

            if (seedCount > 0) {
                var pressurizedPlane = waterLevel
                if (!hasAirVent && bestSurfaceSampleIdx >= 0 && waterLevel.isFinite()) {
                    var sampleFluid: Fluid? = componentFloodFluid ?: dominantFloodFluid
                    if (sampleFluid == null &&
                        level != null &&
                        shipTransform != null &&
                        shipBlockPos != null &&
                        shipPosTmp != null &&
                        worldPosTmp != null &&
                        worldBlockPos != null
                    ) {
                        val lx = bestSurfaceSampleIdx % sizeX
                        val t = bestSurfaceSampleIdx / sizeX
                        val ly = t % sizeY
                        val lz = t / sizeY
                        shipBlockPos.set(minX + lx, minY + ly, minZ + lz)
                        sampleFluid = getShipCellFluidCoverage(
                            level = level,
                            shipTransform = shipTransform,
                            shipBlockPos = shipBlockPos,
                            shipPosTmp = shipPosTmp,
                            worldPosTmp = worldPosTmp,
                            worldBlockPos = worldBlockPos,
                            queryCache = queryCache,
                        ).canonicalFluid
                    }
                    if (sampleFluid != null) sampleFluid = canonicalFloodSource(sampleFluid)
                    if (sampleFluid != null) {
                        val precomputedSurfaceY = precomputedSurfaceYByCell?.let { arr ->
                            if (bestSurfaceSampleIdx in arr.indices) arr[bestSurfaceSampleIdx] else Double.NaN
                        }
                        var surfaceY: Double? =
                            if (precomputedSurfaceY != null && precomputedSurfaceY.isFinite()) precomputedSurfaceY else null

                        if (surfaceY == null &&
                            allowWorldSurfaceScan &&
                            level != null &&
                            shipTransform != null &&
                            shipBlockPos != null &&
                            shipPosTmp != null &&
                            worldPosTmp != null &&
                            worldBlockPos != null
                        ) {
                            val lx = bestSurfaceSampleIdx % sizeX
                            val t = bestSurfaceSampleIdx / sizeX
                            val ly = t % sizeY
                            val lz = t / sizeY
                            shipBlockPos.set(minX + lx, minY + ly, minZ + lz)
                            surfaceY = estimateExteriorFluidSurfaceY(
                                level = level,
                                shipTransform = shipTransform,
                                shipBlockPos = shipBlockPos,
                                sampleFluid = sampleFluid,
                                shipPosTmp = shipPosTmp,
                                worldPosTmp = worldPosTmp,
                                worldBlockPos = worldBlockPos,
                                queryCache = queryCache,
                            )
                        }

                        if (surfaceY != null) {
                            // Keep sealed-pocket flooding clamped to the highest submerged opening so side holes do
                            // not magically raise the interior water level above the real inlet height.
                            pressurizedPlane = waterLevel
                        }
                    }
                }

                var waterHead = 0
                var waterTail = 0

                fun canFillPressurized(i: Int): Boolean {
                    if (!open.get(i) || !interior.get(i) || out.get(i)) return false
                    if (!pressurizedPlane.isFinite()) return false

                    val lx = i % sizeX
                    val t = i / sizeX
                    val ly = t % sizeY
                    val lz = t / sizeY
                    val wy = cellCenterWorldY(lx, ly, lz)
                    return wy <= pressurizedPlane + planeEps
                }

                fun tryEnqueueWater(i: Int) {
                    if (!canFillPressurized(i)) return
                    out.set(i)
                    waterQueue[waterTail++] = i
                }

                // Seed from interior cells adjacent to submerged exterior openings.
                for (i in 0 until seedCount) {
                    tryEnqueueWater(waterQueue[i])
                }

                while (waterHead < waterTail) {
                    val cur = waterQueue[waterHead++]

                    val lx = cur % sizeX
                    val t = cur / sizeX
                    val ly = t % sizeY
                    val lz = t / sizeY
                    val curInteriorMask = voxelInteriorComponentMask?.let { masks ->
                        if (cur in masks.indices) masks[cur] else 0L
                    } ?: -1L

                    fun trySpreadWater(n: Int, dirCode: Int) {
                        if (n < 0 || n >= volume) return
                        if (!interior.get(n)) return
                        val cond = if (hasTemplateConnectivity) {
                            val nInteriorMask = voxelInteriorComponentMask?.let { masks ->
                                if (n in masks.indices) masks[n] else 0L
                            } ?: 0L
                            filteredEdgeCond(
                                idxCur = cur,
                                idxNeighbor = n,
                                lx = lx,
                                ly = ly,
                                lz = lz,
                                dirCode = dirCode,
                                componentMaskCur = curInteriorMask,
                                componentMaskNeighbor = nInteriorMask,
                            )
                        } else {
                            edgeCond(cur, lx, ly, lz, dirCode)
                        }
                        if (cond > 0) {
                            tryEnqueueWater(n)
                        }
                    }

                    if (lx > 0) trySpreadWater(cur - 1, 0)
                    if (lx + 1 < sizeX) trySpreadWater(cur + 1, 1)
                    if (ly > 0) trySpreadWater(cur - strideY, 2)
                    if (ly + 1 < sizeY) trySpreadWater(cur + strideY, 3)
                    if (lz > 0) trySpreadWater(cur - strideZ, 4)
                    if (lz + 1 < sizeZ) trySpreadWater(cur + strideZ, 5)
                }
            }

            // Buoyancy accounting:
            // - Only *submerged* interior air displaces world water and contributes buoyancy.
            if (buoyancy != null) {
                for (i in 0 until tail) {
                    val cellIdx = componentQueue[i]
                    val coverage = buoyancyCoverage[cellIdx].coerceIn(0.0, 1.0)
                    if (coverage <= 1.0e-6) continue

                    val lx = cellIdx % sizeX
                    val t = cellIdx / sizeX
                    val ly = t % sizeY
                    val lz = t / sizeY

                    val sx = minX + lx + 0.5
                    val sy = minY + ly + 0.5
                    val sz = minZ + lz + 0.5

                    if (materializedWater != null && materializedWater.get(cellIdx)) continue

                    buoyancy.submergedAirVolume += coverage
                    buoyancy.submergedAirSumX += sx * coverage
                    buoyancy.submergedAirSumY += sy * coverage
                    buoyancy.submergedAirSumZ += sz * coverage
                }
            }

            start = interior.nextSetBit(start + 1)
        }

        return out
    }

    internal fun computeWaterReachableWithPressurePrepared(
        snapshot: WaterSolveSnapshot,
        out: BitSet,
        buoyancyOut: BuoyancyMetrics,
        floodFluidOut: AtomicReference<Fluid?>,
    ) {
        computeWaterReachableWithPressure(
            level = null,
            minX = snapshot.minX,
            minY = snapshot.minY,
            minZ = snapshot.minZ,
            sizeX = snapshot.sizeX,
            sizeY = snapshot.sizeY,
            sizeZ = snapshot.sizeZ,
            open = snapshot.open,
            interior = snapshot.interior,
            outsideVoid = snapshot.outsideVoid,
            shipTransform = null,
            out = out,
            exteriorOpen = snapshot.exterior,
            buoyancyOut = buoyancyOut,
            materializedWater = snapshot.materializedWater,
            floodFluidOut = floodFluidOut,
            faceCondXP = snapshot.faceCondXP,
            faceCondYP = snapshot.faceCondYP,
            faceCondZP = snapshot.faceCondZP,
            templatePalette = snapshot.templatePalette,
            templateIndexByVoxel = snapshot.templateIndexByVoxel,
            voxelExteriorComponentMask = snapshot.voxelExteriorComponentMask,
            voxelInteriorComponentMask = snapshot.voxelInteriorComponentMask,
            precomputedSubmerged = snapshot.submerged,
            precomputedSubmergedCoverage = snapshot.submergedCoverage,
            precomputedBuoyancySubmergedCoverage = snapshot.buoyancySubmergedCoverage,
            precomputedDominantFloodFluid = snapshot.dominantFloodFluid,
            precomputedSurfaceYByCell = snapshot.surfaceYByCell,
            precomputedOpeningFaceSamples = snapshot.openingFaceSamples,
            precomputedAffine = WorldYAffine(
                baseWorldY = snapshot.baseWorldY,
                incX = snapshot.incX,
                incY = snapshot.incY,
                incZ = snapshot.incZ,
            ),
            allowWorldSurfaceScan = false,
        )
    }

    private fun computeWaterReachable(
        level: Level,
        state: ShipPocketState,
        shipTransform: ShipTransform,
    ): BitSet {
        val buoyancyOut = if (level.isClientSide) null else state.buoyancy
        buoyancyOut?.reset()
        val floodFluidOut = AtomicReference<Fluid?>()
        val reachable = computeWaterReachableWithPressure(
            level,
            state.minX,
            state.minY,
            state.minZ,
            state.sizeX,
            state.sizeY,
            state.sizeZ,
            state.open,
            state.simulationDomain,
            state.outsideVoid,
            shipTransform,
            state.waterReachable,
            exteriorOpen = state.exterior,
            buoyancyOut = buoyancyOut,
            materializedWater = if (level.isClientSide) null else state.materializedWater,
            floodFluidOut = floodFluidOut,
            faceCondXP = state.faceCondXP,
            faceCondYP = state.faceCondYP,
            faceCondZP = state.faceCondZP,
            templatePalette = state.shapeTemplatePalette,
            templateIndexByVoxel = state.templateIndexByVoxel,
            voxelExteriorComponentMask = state.voxelExteriorComponentMask,
            voxelInteriorComponentMask = state.voxelSimulationComponentMask,
        )
        val floodFluid = floodFluidOut.get()
        if (floodFluid != null) {
            val canonical = canonicalFloodSource(floodFluid)
            if (canonical != state.floodFluid) {
                state.floodFluid = canonical
                // The active flood fluid changed (e.g. ship entered a different liquid). Re-scan shipyard blocks so our
                // cached flooded/materialized masks stay consistent.
                state.dirty = true
            }
        }
        state.unreachableVoid = state.open.clone() as BitSet
        state.unreachableVoid.andNot(reachable)
        return reachable
    }

    /**
     * Computes a water-reachability mask for rendering, using the provided [shipTransform].
     *
     * This is used by the water-surface culling shader to stay stable when ships move/rotate quickly between ticks.
     * It intentionally operates on the already-computed [open] voxel set and does not mutate any [ShipPocketState].
     */
    @JvmStatic
    fun computeWaterReachableForRender(
        level: Level,
        minX: Int,
        minY: Int,
        minZ: Int,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
        open: BitSet,
        interior: BitSet,
        shipTransform: ShipTransform,
        out: BitSet,
    ): BitSet {
        return computeWaterReachableWithPressure(
            level = level,
            minX = minX,
            minY = minY,
            minZ = minZ,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            open = open,
            interior = interior,
            outsideVoid = null,
            shipTransform = shipTransform,
            out = out,
            exteriorOpen = null,
            faceCondXP = null,
            faceCondYP = null,
            faceCondZP = null,
        )
    }

    private fun updateFlooding(level: ServerLevel, state: ShipPocketState, shipTransform: ShipTransform) {
        val open = state.open
        val interior = state.simulationDomain
        val materialized = state.materializedWater
        if (open.isEmpty) {
            state.activeFloodIngressPoints = 1
            state.activeFloodIngressConductanceUnits = 1
            state.floodPlaneByComponent.clear()
            state.drainSuppressed.clear()
            return
        }

        // Target flooded interior (equilibrium) from outside water contact / pressure simulation.
        val targetWetInterior = state.waterReachable.clone() as BitSet
        targetWetInterior.and(interior)
        // NOTE: Even if *some* interior pockets are under exterior water pressure, other pockets may be above the
        // waterline and should still be able to drain out through openings to outside air. We handle this per interior
        // component below (see drainFloodedInteriorToOutsideAir).

        val sizeX = state.sizeX
        val sizeY = state.sizeY
        val sizeZ = state.sizeZ
        val volume = sizeX * sizeY * sizeZ

        val newPlanes = Int2DoubleOpenHashMap()
        val toAddAll = BitSet(volume)
        val toRemoveAll = BitSet(volume)
        val drainSuppressedAll = BitSet(volume)
        val orderedAddComponents = mutableListOf<PendingFloodComponentOrder>()
        var virtualFrontsRemaining = MAX_VIRTUAL_INGRESS_FRONTS
        var activeIngressConductanceUnits = 0
        val hadDrainSuppressed = !state.drainSuppressed.isEmpty

        if (!targetWetInterior.isEmpty) {
            // If everything that *should* be wet is already wet, stop the slow-fill simulation.
            val missing = targetWetInterior.clone() as BitSet
            missing.andNot(materialized)
            if (missing.isEmpty) {
                // Still water stabilisation: when a pocket reaches its equilibrium fill level and remains connected to
                // outside water, force any remaining flowing water blocks inside the flooded region to become sources.
                //
                // This avoids "stuck" flowing levels that can happen because we materialize water gradually and vanilla
                // fluid updates may leave behind non-source states.
                stabilizeFloodedWater(level, state, targetWetInterior)
            } else {
                // Compute a fast affine map from local voxel coords -> world Y for this ship transform.
                val baseShipX = state.minX.toDouble()
                val baseShipY = state.minY.toDouble()
                val baseShipZ = state.minZ.toDouble()

                val shipPosTmp = tmpShipPos2.get()
                val worldPosTmp = tmpWorldPos2.get()

                shipPosTmp.set(baseShipX, baseShipY, baseShipZ)
                shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
                val baseWorldY = worldPosTmp.y

                shipPosTmp.set(baseShipX + 1.0, baseShipY, baseShipZ)
                shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
                val incX = worldPosTmp.y - baseWorldY

                shipPosTmp.set(baseShipX, baseShipY + 1.0, baseShipZ)
                shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
                val incY = worldPosTmp.y - baseWorldY

                shipPosTmp.set(baseShipX, baseShipY, baseShipZ + 1.0)
                shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
                val incZ = worldPosTmp.y - baseWorldY

                fun cellCenterWorldY(lx: Int, ly: Int, lz: Int): Double {
                    return baseWorldY + incX * (lx + 0.5) + incY * (ly + 0.5) + incZ * (lz + 0.5)
                }

                val strideY = sizeX
                val strideZ = sizeX * sizeY
                val hasComponentConnectivity = hasComponentTraversalSupport(state)
                val gravityDownDir = computeShipGravityDownDir(shipTransform)

                val visited = tmpFloodComponentVisited.get()
                visited.clear()

                var queue = tmpFloodQueue.get()
                if (queue.size < volume) {
                    queue = IntArray(volume)
                    tmpFloodQueue.set(queue)
                }

                fun scanComponent(start: Int) {
                    var head = 0
                    var tail = 0
                    queue[tail++] = start
                    visited.set(start)

                    var rep = start
                    var targetPlane = Double.NEGATIVE_INFINITY
                    var seedIdx = -1
                    var seedY = Double.NEGATIVE_INFINITY
                    val ingressIdxs = IntArray(MAX_VIRTUAL_INGRESS_FRONTS) { -1 }
                    val ingressConductance = IntArray(MAX_VIRTUAL_INGRESS_FRONTS)
                    val ingressWorldYKey = IntArray(MAX_VIRTUAL_INGRESS_FRONTS)
                    var ingressCount = 0

                    fun isIngressBetter(
                        conductanceA: Int,
                        worldYKeyA: Int,
                        idxA: Int,
                        conductanceB: Int,
                        worldYKeyB: Int,
                        idxB: Int,
                    ): Boolean {
                        if (conductanceA != conductanceB) return conductanceA > conductanceB
                        if (worldYKeyA != worldYKeyB) return worldYKeyA > worldYKeyB
                        return idxA < idxB
                    }

                    fun manhattanIdxDist(idxA: Int, idxB: Int): Int {
                        val ax = idxA % sizeX
                        val at = idxA / sizeX
                        val ay = at % sizeY
                        val az = at / sizeY

                        val bx = idxB % sizeX
                        val bt = idxB / sizeX
                        val by = bt % sizeY
                        val bz = bt / sizeY

                        val dx = if (ax >= bx) ax - bx else bx - ax
                        val dy = if (ay >= by) ay - by else by - ay
                        val dz = if (az >= bz) az - bz else bz - az
                        return dx + dy + dz
                    }

                    fun considerIngressAnchor(idx: Int, conductance: Int, wy: Double) {
                        if (idx < 0 || idx >= volume || conductance <= 0) return
                        val worldYKey = kotlin.math.floor(wy * 1024.0).toInt()

                        for (i in 0 until ingressCount) {
                            if (ingressIdxs[i] != idx) continue
                            val mergedConductance = (ingressConductance[i] + conductance).coerceAtMost(1_000_000)
                            ingressConductance[i] = mergedConductance
                            if (worldYKey > ingressWorldYKey[i]) ingressWorldYKey[i] = worldYKey
                            return
                        }

                        var nearSlot = -1
                        for (i in 0 until ingressCount) {
                            val existingIdx = ingressIdxs[i]
                            if (existingIdx < 0) continue
                            if (manhattanIdxDist(existingIdx, idx) <= VIRTUAL_INGRESS_MIN_SEPARATION) {
                                nearSlot = i
                                break
                            }
                        }
                        if (nearSlot >= 0) {
                            if (isIngressBetter(
                                    conductance,
                                    worldYKey,
                                    idx,
                                    ingressConductance[nearSlot],
                                    ingressWorldYKey[nearSlot],
                                    ingressIdxs[nearSlot],
                                )
                            ) {
                                ingressIdxs[nearSlot] = idx
                                ingressConductance[nearSlot] = conductance
                                ingressWorldYKey[nearSlot] = worldYKey
                            }
                            return
                        }

                        if (ingressCount < MAX_VIRTUAL_INGRESS_FRONTS) {
                            ingressIdxs[ingressCount] = idx
                            ingressConductance[ingressCount] = conductance
                            ingressWorldYKey[ingressCount] = worldYKey
                            ingressCount++
                            return
                        }

                        var weakest = 0
                        for (i in 1 until ingressCount) {
                            if (isIngressBetter(
                                    ingressConductance[weakest],
                                    ingressWorldYKey[weakest],
                                    ingressIdxs[weakest],
                                    ingressConductance[i],
                                    ingressWorldYKey[i],
                                    ingressIdxs[i],
                                )
                            ) {
                                weakest = i
                            }
                        }

                        if (isIngressBetter(
                                conductance,
                                worldYKey,
                                idx,
                                ingressConductance[weakest],
                                ingressWorldYKey[weakest],
                                ingressIdxs[weakest],
                            )
                        ) {
                            ingressIdxs[weakest] = idx
                            ingressConductance[weakest] = conductance
                            ingressWorldYKey[weakest] = worldYKey
                        }
                    }

                    while (head < tail) {
                        val idx = queue[head++]
                        if (idx < rep) rep = idx

                        val lx = idx % sizeX
                        val t = idx / sizeX
                        val ly = t % sizeY
                        val lz = t / sizeY
                        val curInteriorMask = if (hasComponentConnectivity) simulationComponentMaskAt(state, idx) else -1L

                        val wy = cellCenterWorldY(lx, ly, lz)
                        if (targetWetInterior.get(idx) && wy > targetPlane) targetPlane = wy

                        fun tryNeighbor(n: Int, dirCode: Int) {
                            if (n < 0 || n >= volume) return
                            val conductance = if (hasComponentConnectivity) {
                                val nMask = if (interior.get(n)) {
                                    simulationComponentMaskAt(state, n)
                                } else {
                                    exteriorComponentMaskAt(state, n)
                                }
                                computeFilteredFaceConductance(
                                    state = state,
                                    idxA = idx,
                                    idxB = n,
                                    dirCode = dirCode,
                                    componentMaskA = curInteriorMask,
                                    componentMaskB = nMask,
                                )
                            } else {
                                edgeConductance(state, idx, lx, ly, lz, dirCode)
                            }
                            if (conductance <= 0) return
                            if (interior.get(n)) {
                                if (!visited.get(n)) {
                                    visited.set(n)
                                    queue[tail++] = n
                                }
                            } else {
                                // Track a seed candidate at submerged ingress so queued writes expand from the opening.
                                if (!open.get(n)) return
                                if (!state.outsideVoid.get(n)) return
                                if (!state.waterReachable.get(n)) return
                                if (conductance < MIN_OPENING_CONDUCTANCE) {
                                    microOpeningFilteredCount.incrementAndGet()
                                    return
                                }
                                if (wy > seedY) {
                                    seedY = wy
                                    seedIdx = idx
                                }
                                considerIngressAnchor(idx, conductance, wy)
                            }
                        }

                        if (lx > 0) tryNeighbor(idx - 1, 0)
                        if (lx + 1 < sizeX) tryNeighbor(idx + 1, 1)
                        if (ly > 0) tryNeighbor(idx - strideY, 2)
                        if (ly + 1 < sizeY) tryNeighbor(idx + strideY, 3)
                        if (lz > 0) tryNeighbor(idx - strideZ, 4)
                        if (lz + 1 < sizeZ) tryNeighbor(idx + strideZ, 5)
                    }

                    if (ingressCount > 0) {
                        var bestIngress = 0
                        for (i in 1 until ingressCount) {
                            if (isIngressBetter(
                                    ingressConductance[i],
                                    ingressWorldYKey[i],
                                    ingressIdxs[i],
                                    ingressConductance[bestIngress],
                                    ingressWorldYKey[bestIngress],
                                    ingressIdxs[bestIngress],
                                )
                            ) {
                                bestIngress = i
                            }
                        }
                        seedIdx = ingressIdxs[bestIngress]
                    }

                    if (!targetPlane.isFinite()) return

                    val newPlane = targetPlane
                    newPlanes.put(rep, newPlane)

                    // Queue all newly-reachable cells now, then order by:
                    // 1) a short interleaved multi-ingress prelude (line + first-layer samples per ingress)
                    // 2) furthest layer
                    // 3) second furthest layer
                    // 4) continue inward until fully flooded
                    // Layers are gravity-oriented (world-down relative to the ship), matching original behavior.
                    var firstMissingIdx = -1
                    var seedMissing = false
                    val candidateIdxs = IntArray(tail)
                    val candidateLayerKey = IntArray(tail)
                    var candidateCount = 0
                    for (i in 0 until tail) {
                        val idx = queue[i]
                        if (!targetWetInterior.get(idx)) continue
                        if (materialized.get(idx)) continue
                        if (state.queuedFloodAdds.get(idx)) continue
                        if (firstMissingIdx < 0) firstMissingIdx = idx
                        if (idx == seedIdx) seedMissing = true
                        toAddAll.set(idx)

                        val lx = idx % sizeX
                        val t = idx / sizeX
                        val ly = t % sizeY
                        val lz = t / sizeY
                        val layerKey = when (gravityDownDir) {
                            Direction.DOWN -> -ly
                            Direction.UP -> ly
                            Direction.EAST -> lx
                            Direction.WEST -> -lx
                            Direction.SOUTH -> lz
                            Direction.NORTH -> -lz
                        }

                        candidateIdxs[candidateCount] = idx
                        candidateLayerKey[candidateCount] = layerKey
                        candidateCount++
                    }
                    if (firstMissingIdx < 0) return

                    val anchorIdx = if (seedIdx >= 0) seedIdx else firstMissingIdx
                    val anchorX = anchorIdx % sizeX
                    val anchorT = anchorIdx / sizeX
                    val anchorY = anchorT % sizeY
                    val anchorZ = anchorT / sizeY

                    val emitted = BitSet(volume)
                    val orderedAddsForComponent = IntArrayList(candidateCount)
                    fun emitIfPending(idx: Int) {
                        if (idx < 0 || idx >= volume) return
                        if (emitted.get(idx) || !toAddAll.get(idx)) return
                        emitted.set(idx)
                        orderedAddsForComponent.add(idx)
                    }

                    if (seedMissing) {
                        emitIfPending(seedIdx)
                    }

                    var maxLayerKey = Int.MIN_VALUE
                    var minLayerKey = Int.MAX_VALUE
                    for (i in 0 until candidateCount) {
                        val idx = candidateIdxs[i]
                        val lx = idx % sizeX
                        val t = idx / sizeX
                        val ly = t % sizeY
                        val lz = t / sizeY
                        val dx = if (lx >= anchorX) lx - anchorX else anchorX - lx
                        val dy = if (ly >= anchorY) ly - anchorY else anchorY - ly
                        val dz = if (lz >= anchorZ) lz - anchorZ else anchorZ - lz
                        val layerKey = candidateLayerKey[i]
                        if (layerKey > maxLayerKey) maxLayerKey = layerKey
                        if (layerKey < minLayerKey) minLayerKey = layerKey
                    }

                    val firstLayerKey = maxLayerKey
                    val frontAnchorIdxs = IntArray(MAX_VIRTUAL_INGRESS_FRONTS)
                    val frontWeights = IntArray(MAX_VIRTUAL_INGRESS_FRONTS)
                    var frontCount = 0
                    val maxFrontsForComponent = virtualFrontsRemaining.coerceAtMost(MAX_VIRTUAL_INGRESS_FRONTS)

                    fun addFront(anchor: Int, conductance: Int) {
                        if (anchor < 0 || anchor >= volume) return
                        for (i in 0 until frontCount) {
                            if (frontAnchorIdxs[i] == anchor) {
                                if (conductance > frontWeights[i]) {
                                    frontWeights[i] = conductance
                                }
                                return
                            }
                        }
                        if (frontCount >= maxFrontsForComponent) return
                        frontAnchorIdxs[frontCount] = anchor
                        frontWeights[frontCount] = conductance
                        frontCount++
                    }

                    if (ingressCount > 0 && maxFrontsForComponent > 0) {
                        val ingressPicked = BooleanArray(ingressCount)
                        while (frontCount < maxFrontsForComponent) {
                            var best = -1
                            for (i in 0 until ingressCount) {
                                if (ingressPicked[i] || ingressIdxs[i] < 0) continue
                                if (best < 0 || isIngressBetter(
                                        ingressConductance[i],
                                        ingressWorldYKey[i],
                                        ingressIdxs[i],
                                        ingressConductance[best],
                                        ingressWorldYKey[best],
                                        ingressIdxs[best],
                                    )
                                ) {
                                    best = i
                                }
                            }
                            if (best < 0) break
                            ingressPicked[best] = true
                            addFront(ingressIdxs[best], ingressConductance[best])
                        }
                    }
                    if (frontCount == 0 && ingressCount == 0) {
                        addFront(anchorIdx, MIN_OPENING_CONDUCTANCE)
                    }

                    if (frontCount > 0) {
                        val streamCap = VIRTUAL_FRONT_PRELUDE_LINE_CAP + VIRTUAL_FRONT_PRELUDE_LAYER_CAP
                        val frontStreams = Array(frontCount) { IntArray(streamCap) }
                        val frontStreamSizes = IntArray(frontCount)
                        val frontStreamCursors = IntArray(frontCount)

                        fun appendFrontPoint(front: Int, idx: Int): Boolean {
                            if (idx < 0 || idx >= volume || !toAddAll.get(idx)) return false
                            val size = frontStreamSizes[front]
                            if (size >= streamCap) return false
                            for (i in 0 until size) {
                                if (frontStreams[front][i] == idx) return false
                            }
                            frontStreams[front][size] = idx
                            frontStreamSizes[front] = size + 1
                            return true
                        }

                        for (front in 0 until frontCount) {
                            val frontAnchor = frontAnchorIdxs[front]
                            val ax = frontAnchor % sizeX
                            val at = frontAnchor / sizeX
                            val ay = at % sizeY
                            val az = at / sizeY
                            val frontFirstLayerKey = maxLayerKey

                            var frontLineTargetIdx = -1
                            var frontLineTargetDistSq = Int.MAX_VALUE
                            for (i in 0 until candidateCount) {
                                if (candidateLayerKey[i] != frontFirstLayerKey) continue
                                val idx = candidateIdxs[i]
                                val lx = idx % sizeX
                                val t = idx / sizeX
                                val ly = t % sizeY
                                val lz = t / sizeY
                                val dx = if (lx >= ax) lx - ax else ax - lx
                                val dy = if (ly >= ay) ly - ay else ay - ly
                                val dz = if (lz >= az) lz - az else az - lz
                                val distSq = dx * dx + dy * dy + dz * dz
                                if (distSq < frontLineTargetDistSq ||
                                    (distSq == frontLineTargetDistSq && idx < frontLineTargetIdx)
                                ) {
                                    frontLineTargetDistSq = distSq
                                    frontLineTargetIdx = idx
                                }
                            }

                            if (frontLineTargetIdx >= 0) {
                                var x0 = ax
                                var y0 = ay
                                var z0 = az
                                val x1 = frontLineTargetIdx % sizeX
                                val t1 = frontLineTargetIdx / sizeX
                                val y1 = t1 % sizeY
                                val z1 = t1 / sizeY

                                val dx = kotlin.math.abs(x1 - x0)
                                val dy = kotlin.math.abs(y1 - y0)
                                val dz = kotlin.math.abs(z1 - z0)
                                val xs = if (x1 > x0) 1 else -1
                                val ys = if (y1 > y0) 1 else -1
                                val zs = if (z1 > z0) 1 else -1

                                var lineAdded = 0
                                fun emitLinePoint(x: Int, y: Int, z: Int) {
                                    if (lineAdded >= VIRTUAL_FRONT_PRELUDE_LINE_CAP) return
                                    if (x !in 0 until sizeX || y !in 0 until sizeY || z !in 0 until sizeZ) return
                                    val idx = x + y * strideY + z * strideZ
                                    if (appendFrontPoint(front, idx)) {
                                        lineAdded++
                                    }
                                }

                                if (dx >= dy && dx >= dz) {
                                    var p1 = 2 * dy - dx
                                    var p2 = 2 * dz - dx
                                    while (x0 != x1 && lineAdded < VIRTUAL_FRONT_PRELUDE_LINE_CAP) {
                                        x0 += xs
                                        if (p1 >= 0) {
                                            y0 += ys
                                            p1 -= 2 * dx
                                        }
                                        if (p2 >= 0) {
                                            z0 += zs
                                            p2 -= 2 * dx
                                        }
                                        p1 += 2 * dy
                                        p2 += 2 * dz
                                        emitLinePoint(x0, y0, z0)
                                    }
                                } else if (dy >= dx && dy >= dz) {
                                    var p1 = 2 * dx - dy
                                    var p2 = 2 * dz - dy
                                    while (y0 != y1 && lineAdded < VIRTUAL_FRONT_PRELUDE_LINE_CAP) {
                                        y0 += ys
                                        if (p1 >= 0) {
                                            x0 += xs
                                            p1 -= 2 * dy
                                        }
                                        if (p2 >= 0) {
                                            z0 += zs
                                            p2 -= 2 * dy
                                        }
                                        p1 += 2 * dx
                                        p2 += 2 * dz
                                        emitLinePoint(x0, y0, z0)
                                    }
                                } else {
                                    var p1 = 2 * dy - dz
                                    var p2 = 2 * dx - dz
                                    while (z0 != z1 && lineAdded < VIRTUAL_FRONT_PRELUDE_LINE_CAP) {
                                        z0 += zs
                                        if (p1 >= 0) {
                                            y0 += ys
                                            p1 -= 2 * dz
                                        }
                                        if (p2 >= 0) {
                                            x0 += xs
                                            p2 -= 2 * dz
                                        }
                                        p1 += 2 * dy
                                        p2 += 2 * dx
                                        emitLinePoint(x0, y0, z0)
                                    }
                                }
                            }

                            if (VIRTUAL_FRONT_PRELUDE_LAYER_CAP > 0) {
                                val nearestIdx = IntArray(VIRTUAL_FRONT_PRELUDE_LAYER_CAP) { -1 }
                                val nearestDistSq = IntArray(VIRTUAL_FRONT_PRELUDE_LAYER_CAP)
                                var nearestCount = 0

                                for (i in 0 until candidateCount) {
                                    if (candidateLayerKey[i] != frontFirstLayerKey) continue
                                    val idx = candidateIdxs[i]
                                    val lx = idx % sizeX
                                    val t = idx / sizeX
                                    val ly = t % sizeY
                                    val lz = t / sizeY
                                    val dx = if (lx >= ax) lx - ax else ax - lx
                                    val dy = if (ly >= ay) ly - ay else ay - ly
                                    val dz = if (lz >= az) lz - az else az - lz
                                    val distSq = dx * dx + dy * dy + dz * dz

                                    var insert = nearestCount
                                    while (insert > 0) {
                                        val prevDist = nearestDistSq[insert - 1]
                                        val prevIdx = nearestIdx[insert - 1]
                                        if (distSq > prevDist || (distSq == prevDist && idx >= prevIdx)) break
                                        insert--
                                    }
                                    if (insert >= VIRTUAL_FRONT_PRELUDE_LAYER_CAP) continue

                                    val newCount = if (nearestCount < VIRTUAL_FRONT_PRELUDE_LAYER_CAP) {
                                        nearestCount + 1
                                    } else {
                                        nearestCount
                                    }
                                    var j = newCount - 1
                                    while (j > insert) {
                                        nearestDistSq[j] = nearestDistSq[j - 1]
                                        nearestIdx[j] = nearestIdx[j - 1]
                                        j--
                                    }
                                    nearestDistSq[insert] = distSq
                                    nearestIdx[insert] = idx
                                    nearestCount = newCount
                                }

                                for (i in 0 until nearestCount) {
                                    appendFrontPoint(front, nearestIdx[i])
                                }
                            }
                        }

                        var preludeBudget = VIRTUAL_FRONT_PRELUDE_TOTAL_CAP
                        while (preludeBudget > 0) {
                            var progressed = false
                            for (front in 0 until frontCount) {
                                val weight = ((frontWeights[front] + MIN_OPENING_CONDUCTANCE - 1) / MIN_OPENING_CONDUCTANCE)
                                    .coerceIn(1, 4)
                                var pulls = 0
                                while (pulls < weight && preludeBudget > 0) {
                                    val cursor = frontStreamCursors[front]
                                    if (cursor >= frontStreamSizes[front]) break
                                    emitIfPending(frontStreams[front][cursor])
                                    frontStreamCursors[front] = cursor + 1
                                    preludeBudget--
                                    pulls++
                                    progressed = true
                                }
                            }
                            if (!progressed) break
                        }
                    }
                    if (ingressCount > 0 && frontCount > 0) {
                        var componentConductanceUnits = 0
                        for (front in 0 until frontCount) {
                            componentConductanceUnits +=
                                ((frontWeights[front] + MIN_OPENING_CONDUCTANCE - 1) / MIN_OPENING_CONDUCTANCE)
                                    .coerceIn(1, 4)
                        }
                        activeIngressConductanceUnits += componentConductanceUnits.coerceAtLeast(frontCount)
                        virtualFrontsRemaining = (virtualFrontsRemaining - frontCount).coerceAtLeast(0)
                    }

                    if (maxLayerKey >= minLayerKey) {
                        val layerCount = maxLayerKey - minLayerKey + 1
                        val countsByLayer = IntArray(layerCount)
                        for (i in 0 until candidateCount) {
                            val idx = candidateIdxs[i]
                            if (emitted.get(idx)) continue
                            val layer = candidateLayerKey[i] - minLayerKey
                            if (layer in 0 until layerCount) countsByLayer[layer]++
                        }

                        val buckets = Array(layerCount) { layer ->
                            LongArray(countsByLayer[layer])
                        }
                        java.util.Arrays.fill(countsByLayer, 0)

                        for (i in 0 until candidateCount) {
                            val idx = candidateIdxs[i]
                            if (emitted.get(idx)) continue
                            val layer = candidateLayerKey[i] - minLayerKey
                            if (layer !in 0 until layerCount) continue
                            val lx = idx % sizeX
                            val t = idx / sizeX
                            val ly = t % sizeY
                            val lz = t / sizeY
                            val dx = if (lx >= anchorX) lx - anchorX else anchorX - lx
                            val dy = if (ly >= anchorY) ly - anchorY else anchorY - ly
                            val dz = if (lz >= anchorZ) lz - anchorZ else anchorZ - lz
                            val distSq = dx * dx + dy * dy + dz * dz
                            val key = ((distSq.toLong() and 0xffff_ffffL) shl 32) or (idx.toLong() and 0xffff_ffffL)
                            val at = countsByLayer[layer]
                            buckets[layer][at] = key
                            countsByLayer[layer] = at + 1
                        }

                        val layerStep = if (firstLayerKey == maxLayerKey) -1 else 1
                        var layerKey = firstLayerKey
                        while (layerKey in minLayerKey..maxLayerKey) {
                            val layer = layerKey - minLayerKey
                            val bucket = buckets[layer]
                            if (!bucket.isEmpty()) {
                                Arrays.sort(bucket)
                                for (k in bucket.indices) {
                                    emitIfPending(bucket[k].toInt())
                                }
                            }
                            layerKey += layerStep
                        }
                    }

                    for (i in 0 until candidateCount) {
                        emitted.clear(candidateIdxs[i])
                    }

                    if (!orderedAddsForComponent.isEmpty) {
                        orderedAddComponents.add(
                            PendingFloodComponentOrder(
                                orderedIndices = orderedAddsForComponent,
                                fairnessWeight = frontCount.coerceAtLeast(1),
                            ),
                        )
                    }
                }

                var start = missing.nextSetBit(0)
                while (start >= 0 && start < volume) {
                    if (!interior.get(start) || visited.get(start)) {
                        start = missing.nextSetBit(start + 1)
                        continue
                    }
                    scanComponent(start)
                    start = missing.nextSetBit(start + 1)
                }
            }
        }

        // Drain any flooded interior components which are no longer under exterior water pressure.
        drainFloodedInteriorToOutsideAir(
            level,
            state,
            shipTransform,
            protectedInterior = targetWetInterior,
            newPlanesOut = newPlanes,
            toRemoveAll = toRemoveAll,
            drainSuppressedOut = drainSuppressedAll,
        )

        state.floodPlaneByComponent = newPlanes
        state.drainSuppressed = drainSuppressedAll
        state.activeFloodIngressPoints = (MAX_VIRTUAL_INGRESS_FRONTS - virtualFrontsRemaining).coerceAtLeast(1)
        state.activeFloodIngressConductanceUnits = activeIngressConductanceUnits.coerceAtLeast(1)

        if (hadDrainSuppressed && drainSuppressedAll.isEmpty && !state.materializedWater.isEmpty) {
            state.pendingGravityResettleNextIdx = 0
        }

        val orderedAddsAll = mergeOrderedFloodComponentAdds(orderedAddComponents)
        enqueueFloodWriteDiffs(state, toAddAll, toRemoveAll, orderedAddsAll)
        state.persistDirty = true
    }

    private fun redistributeMaterializedWaterForGravity(
        level: ServerLevel,
        state: ShipPocketState,
        shipTransform: ShipTransform,
    ) {
        val open = state.open
        val interior = state.simulationDomain
        val materialized = state.materializedWater
        if (open.isEmpty || interior.isEmpty || materialized.isEmpty) return

        val sizeX = state.sizeX
        val sizeY = state.sizeY
        val sizeZ = state.sizeZ
        val volume = sizeX * sizeY * sizeZ
        if (volume <= 0) return

        val baseShipX = state.minX.toDouble()
        val baseShipY = state.minY.toDouble()
        val baseShipZ = state.minZ.toDouble()
        val shipPosTmp = tmpShipPos2.get()
        val worldPosTmp = tmpWorldPos2.get()

        shipPosTmp.set(baseShipX, baseShipY, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val baseWorldY = worldPosTmp.y

        shipPosTmp.set(baseShipX + 1.0, baseShipY, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incX = worldPosTmp.y - baseWorldY

        shipPosTmp.set(baseShipX, baseShipY + 1.0, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incY = worldPosTmp.y - baseWorldY

        shipPosTmp.set(baseShipX, baseShipY, baseShipZ + 1.0)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incZ = worldPosTmp.y - baseWorldY

        fun cellCenterWorldYKey(idx: Int): Long {
            val lx = idx % sizeX
            val t = idx / sizeX
            val ly = t % sizeY
            val lz = t / sizeY
            val wy = baseWorldY + incX * (lx + 0.5) + incY * (ly + 0.5) + incZ * (lz + 0.5)
            return kotlin.math.floor(wy * 1_000_000.0).toLong()
        }

        val strideY = sizeX
        val strideZ = sizeX * sizeY
        val hasComponentConnectivity = hasComponentTraversalSupport(state)
        val visited = tmpFloodComponentVisited.get()
        visited.clear()

        var queue = tmpFloodQueue.get()
        if (queue.size < volume) {
            queue = IntArray(volume)
            tmpFloodQueue.set(queue)
        }

        val toAdd = BitSet(volume)
        val toRemove = BitSet(volume)

        fun scanComponent(start: Int) {
            var head = 0
            var tail = 0
            visited.set(start)
            queue[tail++] = start
            var waterCount = 0

            while (head < tail) {
                val idx = queue[head++]
                if (materialized.get(idx)) waterCount++

                val lx = idx % sizeX
                val t = idx / sizeX
                val ly = t % sizeY
                val lz = t / sizeY
                val curMask = if (hasComponentConnectivity) simulationComponentMaskAt(state, idx) else -1L

                fun tryNeighbor(n: Int, dirCode: Int) {
                    if (n < 0 || n >= volume) return
                    if (!open.get(n) || !interior.get(n) || visited.get(n)) return
                    val conductance = if (hasComponentConnectivity) {
                        computeFilteredFaceConductance(
                            state = state,
                            idxA = idx,
                            idxB = n,
                            dirCode = dirCode,
                            componentMaskA = curMask,
                            componentMaskB = simulationComponentMaskAt(state, n),
                        )
                    } else {
                        edgeConductance(state, idx, lx, ly, lz, dirCode)
                    }
                    if (conductance <= 0) return
                    visited.set(n)
                    queue[tail++] = n
                }

                if (lx > 0) tryNeighbor(idx - 1, 0)
                if (lx + 1 < sizeX) tryNeighbor(idx + 1, 1)
                if (ly > 0) tryNeighbor(idx - strideY, 2)
                if (ly + 1 < sizeY) tryNeighbor(idx + strideY, 3)
                if (lz > 0) tryNeighbor(idx - strideZ, 4)
                if (lz + 1 < sizeZ) tryNeighbor(idx + strideZ, 5)
            }

            if (waterCount <= 0 || waterCount >= tail) return

            val ordered = LongArray(tail)
            for (i in 0 until tail) {
                val idx = queue[i]
                ordered[i] = (cellCenterWorldYKey(idx) shl 32) or (idx.toLong() and 0xffff_ffffL)
            }
            Arrays.sort(ordered)

            val target = BitSet(volume)
            for (i in 0 until waterCount) {
                target.set(ordered[i].toInt())
            }

            for (i in 0 until tail) {
                val idx = queue[i]
                if (target.get(idx)) {
                    if (!materialized.get(idx)) toAdd.set(idx)
                } else if (materialized.get(idx)) {
                    toRemove.set(idx)
                }
            }
        }

        var start = materialized.nextSetBit(0)
        while (start >= 0 && start < volume) {
            if (!open.get(start) || !interior.get(start) || visited.get(start)) {
                start = materialized.nextSetBit(start + 1)
                continue
            }
            scanComponent(start)
            start = materialized.nextSetBit(start + 1)
        }

        if (toAdd.isEmpty && toRemove.isEmpty) return
        state.queuedFloodAdds.andNot(toRemove)
        state.queuedFloodRemoves.andNot(toAdd)
        applyBlockChanges(level, state, toAdd, toWater = true, pos = BlockPos.MutableBlockPos())
        applyBlockChanges(level, state, toRemove, toWater = false, pos = BlockPos.MutableBlockPos())
    }

    private fun drainFloodedInteriorToOutsideAir(
        level: ServerLevel,
        state: ShipPocketState,
        shipTransform: ShipTransform,
        protectedInterior: BitSet?,
        newPlanesOut: Int2DoubleOpenHashMap,
        toRemoveAll: BitSet,
        drainSuppressedOut: BitSet,
    ) {
        val open = state.open
        val interior = state.simulationDomain
        val materialized = state.materializedWater
        if (open.isEmpty || materialized.isEmpty) {
            return
        }

        val sizeX = state.sizeX
        val sizeY = state.sizeY
        val sizeZ = state.sizeZ
        val volume = sizeX * sizeY * sizeZ
        val strideY = sizeX
        val strideZ = sizeX * sizeY
        val hasComponentConnectivity = hasComponentTraversalSupport(state)

        // "True outside" void space (connected to the sim bounds), used to avoid treating enclosed cavities as vents.
        val exteriorOpen = state.outsideVoid

        // Compute a fast affine map from local voxel coords -> world Y for this ship transform.
        val baseShipX = state.minX.toDouble()
        val baseShipY = state.minY.toDouble()
        val baseShipZ = state.minZ.toDouble()

        val shipPosTmp = tmpShipPos2.get()
        val worldPosTmp = tmpWorldPos2.get()
        val queryCache = tmpChunkQueryCache.get().apply { reset() }

        shipPosTmp.set(baseShipX, baseShipY, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val baseWorldY = worldPosTmp.y

        shipPosTmp.set(baseShipX + 1.0, baseShipY, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incX = worldPosTmp.y - baseWorldY

        shipPosTmp.set(baseShipX, baseShipY + 1.0, baseShipZ)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incY = worldPosTmp.y - baseWorldY

        shipPosTmp.set(baseShipX, baseShipY, baseShipZ + 1.0)
        shipTransform.shipToWorld.transformPosition(shipPosTmp, worldPosTmp)
        val incZ = worldPosTmp.y - baseWorldY

        fun cellCenterWorldY(lx: Int, ly: Int, lz: Int): Double {
            return baseWorldY + incX * (lx + 0.5) + incY * (ly + 0.5) + incZ * (lz + 0.5)
        }

        fun openingFaceMinWorldY(lx: Int, ly: Int, lz: Int, outDirCode: Int): Double {
            val x0 = lx.toDouble()
            val y0 = ly.toDouble()
            val z0 = lz.toDouble()
            val x1 = x0 + 1.0
            val y1 = y0 + 1.0
            val z1 = z0 + 1.0
            fun wy(x: Double, y: Double, z: Double): Double = baseWorldY + incX * x + incY * y + incZ * z
            return when (outDirCode) {
                0 -> minOf(wy(x0, y0, z0), wy(x0, y1, z0), wy(x0, y0, z1), wy(x0, y1, z1))
                1 -> minOf(wy(x1, y0, z0), wy(x1, y1, z0), wy(x1, y0, z1), wy(x1, y1, z1))
                2 -> minOf(wy(x0, y0, z0), wy(x1, y0, z0), wy(x0, y0, z1), wy(x1, y0, z1))
                3 -> minOf(wy(x0, y1, z0), wy(x1, y1, z0), wy(x0, y1, z1), wy(x1, y1, z1))
                4 -> minOf(wy(x0, y0, z0), wy(x1, y0, z0), wy(x0, y1, z0), wy(x1, y1, z0))
                else -> minOf(wy(x0, y0, z1), wy(x1, y0, z1), wy(x0, y1, z1), wy(x1, y1, z1))
            }
        }

        val visited = tmpFloodComponentVisited.get()
        visited.clear()

        var queue = tmpFloodQueue.get()
        if (queue.size < volume) {
            queue = IntArray(volume)
            tmpFloodQueue.set(queue)
        }

        val shipPosCornerTmp = tmpShipPos3.get()
        val worldPosCornerTmp = tmpWorldPos3.get()
        val worldBlockPos = BlockPos.MutableBlockPos()
        val openingOutsideAirCache = Long2ByteOpenHashMap()
        var drainParticleBudget = 2
        fun spawnDrainParticles(ventIdx: Int, outDirCode: Int, conductance: Int) {
            if (drainParticleBudget <= 0) return
            if (ventIdx < 0 || ventIdx >= volume) return
            if (conductance <= 0) return
            drainParticleBudget--

            val particle = leakParticleForFluid(state.floodFluid)
            val particleCount = (2 + conductance / 12).coerceIn(2, 10)
            val particleSpeedMultiplier = VSGameConfig.COMMON.shipPocketParticleSpeedMultiplier.coerceIn(0.1, 5.0)
            val speed = ((0.10 + conductance * 0.00035).coerceIn(0.10, 0.18)) * particleSpeedMultiplier
            emitDirectionalLeakParticles(
                level = level,
                state = state,
                shipTransform = shipTransform,
                cellIdx = ventIdx,
                faceDirCode = outDirCode xor 1,
                jetDirCode = outDirCode,
                particle = particle,
                particleCount = particleCount,
                baseSpeed = speed,
            )
        }

        fun openingExposesOutsideAir(lx: Int, ly: Int, lz: Int, outDirCode: Int): Boolean {
            val openingIdx = lx + ly * strideY + lz * strideZ
            val openingKey = (openingIdx.toLong() shl 3) or outDirCode.toLong()
            when (openingOutsideAirCache.getOrDefault(openingKey, 0).toInt()) {
                1 -> return true
                2 -> return false
            }

            val faceOffset = 1.0e-4
            val lo = 1.0e-4
            val hi = 1.0 - lo

            fun sampleOutsideFace(u: Double, v: Double): Boolean {
                val sampleX: Double
                val sampleY: Double
                val sampleZ: Double
                when (outDirCode) {
                    0 -> {
                        sampleX = lx - faceOffset
                        sampleY = ly + u
                        sampleZ = lz + v
                    }
                    1 -> {
                        sampleX = lx + 1.0 + faceOffset
                        sampleY = ly + u
                        sampleZ = lz + v
                    }
                    2 -> {
                        sampleX = lx + u
                        sampleY = ly - faceOffset
                        sampleZ = lz + v
                    }
                    3 -> {
                        sampleX = lx + u
                        sampleY = ly + 1.0 + faceOffset
                        sampleZ = lz + v
                    }
                    4 -> {
                        sampleX = lx + u
                        sampleY = ly + v
                        sampleZ = lz - faceOffset
                    }
                    else -> {
                        sampleX = lx + u
                        sampleY = ly + v
                        sampleZ = lz + 1.0 + faceOffset
                    }
                }

                val shipX = state.minX + sampleX
                val shipY = state.minY + sampleY
                val shipZ = state.minZ + sampleZ
                if (sampleCanonicalWorldFluidAtShipPoint(
                        level = level,
                        shipTransform = shipTransform,
                        shipX = shipX,
                        shipY = shipY,
                        shipZ = shipZ,
                        shipPosTmp = shipPosCornerTmp,
                        worldPosTmp = worldPosCornerTmp,
                        worldBlockPos = worldBlockPos,
                        queryCache = queryCache,
                    ) != null
                ) {
                    return false
                }

                shipPosCornerTmp.set(shipX, shipY, shipZ)
                shipTransform.shipToWorld.transformPosition(shipPosCornerTmp, worldPosCornerTmp)
                worldBlockPos.set(
                    Mth.floor(worldPosCornerTmp.x),
                    Mth.floor(worldPosCornerTmp.y),
                    Mth.floor(worldPosCornerTmp.z),
                )

                val outsideBlock = FluidStateManager.getBlockState(level, worldBlockPos, queryCache)
                if (outsideBlock.isAir) return true

                val shape = outsideBlock.getCollisionShape(level, worldBlockPos)
                if (shape.isEmpty) return true

                val localX = worldPosCornerTmp.x - worldBlockPos.x.toDouble()
                val localY = worldPosCornerTmp.y - worldBlockPos.y.toDouble()
                val localZ = worldPosCornerTmp.z - worldBlockPos.z.toDouble()
                val eps = 1.0e-7
                for (box in shape.toAabbs()) {
                    if (localX >= box.minX - eps && localX <= box.maxX + eps &&
                        localY >= box.minY - eps && localY <= box.maxY + eps &&
                        localZ >= box.minZ - eps && localZ <= box.maxZ + eps
                    ) {
                        return false
                    }
                }

                return true
            }

            val exposed = sampleOutsideFace(0.5, 0.5) ||
                sampleOutsideFace(lo, lo) ||
                sampleOutsideFace(hi, lo) ||
                sampleOutsideFace(lo, hi) ||
                sampleOutsideFace(hi, hi)
            openingOutsideAirCache.put(openingKey, if (exposed) 1 else 2)
            return exposed
        }

        fun scanComponent(start: Int) {
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited.set(start)

            var rep = start
            var currentTop = Double.NEGATIVE_INFINITY
            var drainTarget = Double.POSITIVE_INFINITY
            var drainFaces = 0
            var bestVentIdx = -1
            var bestVentOutDirCode = 0
            var bestVentConductance = 0

            fun considerVent(
                holeIdx: Int,
                outDirCode: Int,
                fromWaterWy: Double,
                waterLX: Int,
                waterLY: Int,
                waterLZ: Int,
                conductance: Int,
            ) {
                if (conductance <= 0) return
                if (conductance < MIN_OPENING_CONDUCTANCE) {
                    microOpeningFilteredCount.incrementAndGet()
                    return
                }
                if (!open.get(holeIdx) || !exteriorOpen.get(holeIdx)) return
                val holeLX = holeIdx % sizeX
                val holeT = holeIdx / sizeX
                val holeLY = holeT % sizeY
                val holeLZ = holeT / sizeY
                val isBoundaryCell =
                    holeLX == 0 || holeLX + 1 == sizeX ||
                        holeLY == 0 || holeLY + 1 == sizeY ||
                        holeLZ == 0 || holeLZ + 1 == sizeZ
                if (state.strictInterior.get(holeIdx) && !isBoundaryCell) return
                if (!openingExposesOutsideAir(waterLX, waterLY, waterLZ, outDirCode)) return

                // Water can't "flush" out through an opening that's above the draining water cell in world-space.
                // This fixes bowls/open-top containers losing water upward when moved out of the ocean.
                val holeWy = openingFaceMinWorldY(waterLX, waterLY, waterLZ, outDirCode)
                if (holeWy > fromWaterWy + 1.0e-6) return

                val filteredConductance = conductance
                if (filteredConductance <= 0) return

                drainFaces += filteredConductance
                if (holeWy < drainTarget) {
                    drainTarget = holeWy
                    bestVentIdx = holeIdx
                    bestVentOutDirCode = outDirCode
                    bestVentConductance = filteredConductance
                }
            }

            while (head < tail) {
                val idx = queue[head++]
                if (idx < rep) rep = idx

                val lx = idx % sizeX
                val t = idx / sizeX
                val ly = t % sizeY
                val lz = t / sizeY
                val curOpenMask = if (hasComponentConnectivity) allOpenComponentMaskAt(state, idx) else -1L
                val curInteriorMask = if (hasComponentConnectivity) simulationComponentMaskAt(state, idx) else -1L

                val isWaterCell = materialized.get(idx)
                val waterWy = if (isWaterCell) cellCenterWorldY(lx, ly, lz) else Double.NaN
                if (isWaterCell && waterWy > currentTop) currentTop = waterWy

                fun tryNeighbor(n: Int, outDirCode: Int) {
                    if (n < 0 || n >= volume) return
                    if (!open.get(n)) return
                    val conductance = if (hasComponentConnectivity) {
                        val nMask = allOpenComponentMaskAt(state, n)
                        computeFilteredFaceConductance(
                            state = state,
                            idxA = idx,
                            idxB = n,
                            dirCode = outDirCode,
                            componentMaskA = curOpenMask,
                            componentMaskB = nMask,
                        )
                    } else {
                        edgeConductance(state, idx, lx, ly, lz, outDirCode)
                    }
                    if (conductance <= 0) return
                    if (!visited.get(n)) {
                        visited.set(n)
                        queue[tail++] = n
                    }
                    if (isWaterCell && exteriorOpen.get(n)) {
                        val ventConductance = if (hasComponentConnectivity) {
                            val nExteriorMask = exteriorComponentMaskAt(state, n)
                            computeFilteredFaceConductance(
                                state = state,
                                idxA = idx,
                                idxB = n,
                                dirCode = outDirCode,
                                componentMaskA = curInteriorMask,
                                componentMaskB = nExteriorMask,
                            )
                        } else {
                            conductance
                        }
                        considerVent(n, outDirCode, waterWy, lx, ly, lz, ventConductance)
                    }
                }

                if (lx > 0) tryNeighbor(idx - 1, 0)
                if (lx + 1 < sizeX) tryNeighbor(idx + 1, 1)
                if (ly > 0) tryNeighbor(idx - strideY, 2)
                if (ly + 1 < sizeY) tryNeighbor(idx + strideY, 3)
                if (lz > 0) tryNeighbor(idx - strideZ, 4)
                if (lz + 1 < sizeZ) tryNeighbor(idx + strideZ, 5)
            }

            if (!currentTop.isFinite()) return
            if (!drainTarget.isFinite() || drainFaces <= 0) return

            val oldPlane =
                if (state.floodPlaneByComponent.containsKey(rep)) state.floodPlaneByComponent.get(rep) else currentTop
            val floodRateMultiplier = VSGameConfig.COMMON.shipPocketFloodRateMultiplier.coerceIn(0.05, 5.0)
            val drainRate = ((FLOOD_RISE_PER_TICK_BASE +
                drainFaces.toDouble() * FLOOD_RISE_PER_TICK_PER_HOLE_FACE)
                .coerceAtMost(FLOOD_RISE_MAX_PER_TICK)) * floodRateMultiplier
            val newPlane = maxOf(drainTarget, oldPlane - drainRate)
            newPlanesOut.put(rep, newPlane)

            var removedCount = 0
            for (i in 0 until tail) {
                val idx = queue[i]
                if (!interior.get(idx)) continue

                val lx = idx % sizeX
                val t = idx / sizeX
                val ly = t % sizeY
                val lz = t / sizeY
                val wy = cellCenterWorldY(lx, ly, lz)
                if (wy <= newPlane + FLOOD_EXIT_PLANE_EPS) continue
                if (protectedInterior != null && protectedInterior.get(idx)) continue
                drainSuppressedOut.set(idx)
                if (!materialized.get(idx)) continue
                if (state.queuedFloodRemoves.get(idx)) continue
                toRemoveAll.set(idx)
                removedCount++
            }

            if (bestVentIdx >= 0 && oldPlane - newPlane > 1.0e-6 && removedCount > 0) {
                spawnDrainParticles(bestVentIdx, bestVentOutDirCode, bestVentConductance)
            }
        }

        var start = materialized.nextSetBit(0)
        while (start >= 0 && start < volume) {
            if (!open.get(start) || visited.get(start)) {
                start = materialized.nextSetBit(start + 1)
                continue
            }
            scanComponent(start)
            start = materialized.nextSetBit(start + 1)
        }
    }

    private fun stabilizeFloodedWater(level: ServerLevel, state: ShipPocketState, targetWetInterior: BitSet) {
        if (targetWetInterior.isEmpty) return

        val flags = 11 // 1 (block update) + 2 (send to clients) + 8 (force rerender)
        val pos = BlockPos.MutableBlockPos()
        val sourceBlockState = state.floodFluid.defaultFluidState().createLegacyBlock()

        applyingInternalUpdates = true
        try {
            var idx = targetWetInterior.nextSetBit(0)
            while (idx >= 0) {
                posFromIndex(state, idx, pos)
                val bs = level.getBlockState(pos)
                val fluidState = bs.fluidState
                if (!fluidState.isEmpty &&
                    canonicalFloodSource(fluidState.type) == state.floodFluid &&
                    !fluidState.isSource
                ) {
                    level.setBlock(pos, sourceBlockState, flags)
                }
                idx = targetWetInterior.nextSetBit(idx + 1)
            }
        } finally {
            applyingInternalUpdates = false
        }
    }

    private fun floodFillFromSeedsSubmerged(
        level: Level,
        shipTransform: ShipTransform,
        state: ShipPocketState,
        seeds: BitSet,
    ): BitSet {
        val sizeX = state.sizeX
        val sizeY = state.sizeY
        val sizeZ = state.sizeZ
        val volume = sizeX * sizeY * sizeZ

        val open = state.open
        if (open.isEmpty || seeds.isEmpty) return BitSet()

        val visited = BitSet(volume)
        var queue = tmpFloodQueue.get()
        if (queue.size < volume) {
            queue = IntArray(volume)
            tmpFloodQueue.set(queue)
        }
        var head = 0
        var tail = 0

        val worldPosTmp = tmpWorldPos2.get()
        val shipPosTmp = tmpShipPos2.get()
        val shipBlockPos = BlockPos.MutableBlockPos()
        val worldBlockPos = BlockPos.MutableBlockPos()
        val queryCache = tmpChunkQueryCache.get().apply { reset() }
        val submergedCache = Long2ByteOpenHashMap()

        fun shipCellSubmerged(idx: Int): Boolean {
            when (submergedCache.getOrDefault(idx.toLong(), 0).toInt()) {
                1 -> return true
                2 -> return false
            }
            posFromIndex(state, idx, shipBlockPos)
            val submerged = isShipCellSubmergedInWorldFluid(
                level,
                shipTransform,
                shipBlockPos,
                shipPosTmp,
                worldPosTmp,
                worldBlockPos,
                queryCache = queryCache,
            )
            submergedCache.put(idx.toLong(), if (submerged) 1 else 2)
            return submerged
        }

        fun tryEnqueue(idx: Int, requireSubmerged: Boolean) {
            if (!open.get(idx) || visited.get(idx)) return
            if (requireSubmerged && !shipCellSubmerged(idx)) return
            visited.set(idx)
            queue[tail++] = idx
        }

        var idx = seeds.nextSetBit(0)
        while (idx >= 0) {
            // Always seed from existing materialized water, even if it's above the waterline.
            tryEnqueue(idx, requireSubmerged = false)
            idx = seeds.nextSetBit(idx + 1)
        }

        val strideY = sizeX
        val strideZ = sizeX * sizeY

        while (head < tail) {
            val cur = queue[head++]

            val lx = cur % sizeX
            val t = cur / sizeX
            val ly = t % sizeY
            val lz = t / sizeY

            // Spread only through submerged open cells to prevent water rising above the external waterline.
            if (lx > 0 && edgeConductance(state, cur, lx, ly, lz, 0) > 0) tryEnqueue(cur - 1, requireSubmerged = true)
            if (lx + 1 < sizeX && edgeConductance(state, cur, lx, ly, lz, 1) > 0) tryEnqueue(cur + 1, requireSubmerged = true)
            if (ly > 0 && edgeConductance(state, cur, lx, ly, lz, 2) > 0) tryEnqueue(cur - strideY, requireSubmerged = true)
            if (ly + 1 < sizeY && edgeConductance(state, cur, lx, ly, lz, 3) > 0) tryEnqueue(cur + strideY, requireSubmerged = true)
            if (lz > 0 && edgeConductance(state, cur, lx, ly, lz, 4) > 0) tryEnqueue(cur - strideZ, requireSubmerged = true)
            if (lz + 1 < sizeZ && edgeConductance(state, cur, lx, ly, lz, 5) > 0) tryEnqueue(cur + strideZ, requireSubmerged = true)
        }

        return visited
    }

    private fun applyBlockChanges(
        level: ServerLevel,
        state: ShipPocketState,
        indices: BitSet,
        toWater: Boolean,
        pos: BlockPos.MutableBlockPos,
        shipTransform: ShipTransform? = null,
    ) {
        if (indices.isEmpty) return

        val flags = 11 // 1 (block update) + 2 (send to clients) + 8 (force rerender)

        val worldPosTmp = tmpWorldPos2.get()
        val shipPosTmp = tmpShipPos2.get()
        val worldBlockPos = BlockPos.MutableBlockPos()
        val queryCache = tmpChunkQueryCache.get().apply { reset() }
        val submergedCoverageCache = Long2ObjectOpenHashMap<FluidCoverageSample>()
        val coverageTransform = shipTransform

        fun shipCellFluidCoverage(idx: Int): FluidCoverageSample {
            val key = idx.toLong()
            val cached = submergedCoverageCache.get(key)
            if (cached != null) {
                return cached
            }

            posFromIndex(state, idx, pos)
            val coverage = getShipCellFluidCoverage(
                level,
                coverageTransform ?: return FluidCoverageSample(
                    canonicalFluid = null,
                    coverageRatio = 0.0,
                    centerSubmerged = false,
                ),
                pos,
                shipPosTmp,
                worldPosTmp,
                worldBlockPos,
                queryCache = queryCache,
            )
            submergedCoverageCache.put(key, coverage)
            return coverage
        }

        applyingInternalUpdates = true
        try {
            var idx = indices.nextSetBit(0)
            while (idx >= 0) {
                posFromIndex(state, idx, pos)

                val current = FluidStateManager.getBlockState(level, pos, queryCache)
                if (toWater) {
                    if (!state.simulationDomain.get(idx)) {
                        state.materializedWater.clear(idx)
                        idx = indices.nextSetBit(idx + 1)
                        continue
                    }
                    if (shipTransform != null) {
                        val submergedSample = shipCellFluidCoverage(idx)
                        val submergedFluid = submergedSample.canonicalFluid
                        if (!submergedSample.isIngressQualified() || submergedFluid == null || canonicalFloodSource(submergedFluid) != state.floodFluid) {
                            idx = indices.nextSetBit(idx + 1)
                            continue
                        }
                    }
                    val write = applyFloodBlockWrite(
                        level = level,
                        pos = pos,
                        current = current,
                        floodFluid = state.floodFluid,
                        toWater = true,
                        dropOnBreak = !state.brokenByFlood.get(idx),
                        setBlockFlags = flags,
                    )
                    if (write.materialized) {
                        state.externalFluid.clear(idx)
                        state.materializedWater.set(idx)
                        if (write.effect == FloodWriteEffectKind.BREAK_ON_FLOOD) {
                            state.brokenByFlood.set(idx)
                        }
                    }
                } else {
                    applyFloodBlockWrite(
                        level = level,
                        pos = pos,
                        current = current,
                        floodFluid = state.floodFluid,
                        toWater = false,
                        setBlockFlags = flags,
                    )
                    state.externalFluid.clear(idx)
                    state.materializedWater.clear(idx)
                    state.brokenByFlood.clear(idx)
                }

                idx = indices.nextSetBit(idx + 1)
            }
        } finally {
            applyingInternalUpdates = false
        }
        state.persistDirty = true
    }

    private fun isShipCellSubmergedInWorldFluid(
        level: Level,
        shipTransform: ShipTransform,
        shipBlockPos: BlockPos,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        worldBlockPos: BlockPos.MutableBlockPos,
        queryCache: FluidStateManager.QueryCache? = null,
    ): Boolean {
        return getShipCellFluidCoverage(
            level,
            shipTransform,
            shipBlockPos,
            shipPosTmp,
            worldPosTmp,
            worldBlockPos,
            queryCache = queryCache,
        )
            .isIngressQualified()
    }

    private fun getShipCellSubmergedWorldFluidType(
        level: Level,
        shipTransform: ShipTransform,
        shipBlockPos: BlockPos,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        worldBlockPos: BlockPos.MutableBlockPos,
        queryCache: FluidStateManager.QueryCache? = null,
    ): Fluid? {
        val sample = getShipCellFluidCoverage(
            level,
            shipTransform,
            shipBlockPos,
            shipPosTmp,
            worldPosTmp,
            worldBlockPos,
            queryCache = queryCache,
        )
        return if (sample.isIngressQualified()) sample.canonicalFluid else null
    }

    private fun getShipCellFluidCoverage(
        level: Level,
        shipTransform: ShipTransform,
        shipBlockPos: BlockPos,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        worldBlockPos: BlockPos.MutableBlockPos,
        queryCache: FluidStateManager.QueryCache? = null,
        currentShipState: ShipPocketState? = null,
        clientWorldQueryBounds: ClientWorldChunkQueryBounds? = null,
    ): FluidCoverageSample {
        return withBypassedFluidOverrides {
            val epsCorner = 1e-4
            val sampledFluids = arrayOfNulls<Fluid>(9)
            val sampledFluidCounts = IntArray(9)
            var sampledFluidCount = 0
            var centerCanonical: Fluid? = null
            var centerSubmerged = false
            var submergedSamples = 0

            fun sample(shipX: Double, shipY: Double, shipZ: Double): Fluid? {
                return sampleCanonicalWorldFluidAtShipPoint(
                    level = level,
                    shipTransform = shipTransform,
                    shipX = shipX,
                    shipY = shipY,
                    shipZ = shipZ,
                    shipPosTmp = shipPosTmp,
                    worldPosTmp = worldPosTmp,
                    worldBlockPos = worldBlockPos,
                    queryCache = queryCache,
                    currentShipState = currentShipState,
                    clientWorldQueryBounds = clientWorldQueryBounds,
                )
            }

            fun registerSample(fluid: Fluid?) {
                if (fluid == null) return
                submergedSamples++
                for (i in 0 until sampledFluidCount) {
                    if (sampledFluids[i] == fluid) {
                        sampledFluidCounts[i]++
                        return
                    }
                }
                if (sampledFluidCount < sampledFluids.size) {
                    sampledFluids[sampledFluidCount] = fluid
                    sampledFluidCounts[sampledFluidCount] = 1
                    sampledFluidCount++
                } else {
                    // Should never happen (9 samples max), but keep deterministic behavior.
                    sampledFluidCounts[0]++
                }
            }

            val x0 = shipBlockPos.x.toDouble()
            val y0 = shipBlockPos.y.toDouble()
            val z0 = shipBlockPos.z.toDouble()

            centerCanonical = sample(x0 + 0.5, y0 + 0.5, z0 + 0.5)
            centerSubmerged = centerCanonical != null
            registerSample(centerCanonical)

            // Near the fluid surface / with rotation, the cell center can be above fluid while a corner is submerged.
            val lo = epsCorner
            val hi = 1.0 - epsCorner
            registerSample(sample(x0 + lo, y0 + lo, z0 + lo))
            registerSample(sample(x0 + hi, y0 + lo, z0 + lo))
            registerSample(sample(x0 + lo, y0 + hi, z0 + lo))
            registerSample(sample(x0 + hi, y0 + hi, z0 + lo))
            registerSample(sample(x0 + lo, y0 + lo, z0 + hi))
            registerSample(sample(x0 + hi, y0 + lo, z0 + hi))
            registerSample(sample(x0 + lo, y0 + hi, z0 + hi))
            registerSample(sample(x0 + hi, y0 + hi, z0 + hi))

            if (submergedSamples == 0) {
                return@withBypassedFluidOverrides FluidCoverageSample(
                    canonicalFluid = null,
                    coverageRatio = 0.0,
                    centerSubmerged = false,
                )
            }

            var bestFluid: Fluid? = null
            var bestCount = 0
            for (i in 0 until sampledFluidCount) {
                val fluid = sampledFluids[i] ?: continue
                val count = sampledFluidCounts[i]
                if (count > bestCount) {
                    bestCount = count
                    bestFluid = fluid
                } else if (count == bestCount && centerCanonical != null && fluid == centerCanonical) {
                    bestFluid = fluid
                }
            }

            if (bestFluid == null || bestCount <= 0) {
                val c = coverageFallbackDiagCount.incrementAndGet()
                if (c <= 3L || c % 500L == 0L) {
                    log.debug("Fluid coverage fallback triggered (shipCell={}, samples={})", shipBlockPos, submergedSamples)
                }
                return@withBypassedFluidOverrides FluidCoverageSample(
                    canonicalFluid = null,
                    coverageRatio = 0.0,
                    centerSubmerged = false,
                )
            }

            var ratio = bestCount / 9.0
            if (!ratio.isFinite()) ratio = 0.0
            ratio = ratio.coerceIn(0.0, 1.0)

            return@withBypassedFluidOverrides FluidCoverageSample(
                canonicalFluid = bestFluid,
                coverageRatio = ratio,
                centerSubmerged = centerCanonical != null && bestFluid == centerCanonical && centerSubmerged,
            )
        }
    }

}
