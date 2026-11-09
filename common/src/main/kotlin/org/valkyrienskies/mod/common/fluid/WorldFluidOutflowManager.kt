package org.valkyrienskies.mod.common.fluid

import it.unimi.dsi.fastutil.HashCommon
import it.unimi.dsi.fastutil.longs.LongArrays
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ConcretePowderBlock
import net.minecraft.world.level.block.FarmBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluids
import org.joml.Vector3d
import org.valkyrienskies.core.internal.physics.VsiFluidOutflow
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.core.internal.physics.VsiFluidParcelInterception
import org.valkyrienskies.core.internal.physics.VsiFluidParcelMomentum
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources.Companion.FULL_SOURCE
import org.valkyrienskies.mod.common.networking.FluidParcelState
import org.valkyrienskies.mod.common.networking.FluidParcelUpdate
import org.valkyrienskies.mod.common.networking.PacketFluidParcel
import org.valkyrienskies.mod.common.networking.PacketFluidParcelBatch
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.vsCore
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

internal enum class FluidParcelAction {
    FALL,
    MERGE,
    SETTLE,
    WAIT,
}

internal fun chooseFluidParcelAction(
    currentReplaceable: Boolean,
    currentIsSameSource: Boolean,
    belowReplaceable: Boolean,
    belowIsSameSource: Boolean,
): FluidParcelAction = when {
    currentIsSameSource -> FluidParcelAction.MERGE
    !currentReplaceable -> FluidParcelAction.WAIT
    belowIsSameSource -> FluidParcelAction.MERGE
    belowReplaceable -> FluidParcelAction.FALL
    else -> FluidParcelAction.SETTLE
}

internal fun worldVolumeToVisualLevel(worldVolume: Double): Int =
    ceil(worldVolume.coerceIn(0.0, 1.0) * 8).toInt().coerceIn(1, 8)

private val MIN_SETTLED_UNITS = BigDecimal("31.875")
private val SPLASH_SPLIT_DIVISOR = BigDecimal.valueOf(2L)
private val MIN_SPLIT_SPLASH_UNITS = MIN_SETTLED_UNITS * SPLASH_SPLIT_DIVISOR
private val MAX_MERGED_PARCEL_UNITS = BigDecimal("63.75")
private val SPONGE_CAPACITY = BigDecimal.valueOf(64L * 255)

internal fun settledFluidLevel(units: BigDecimal): Int =
    units.divideToIntegralValue(MIN_SETTLED_UNITS).toInt().coerceIn(0, 8)

internal fun fluidParcelTickDisplacement(velocity: Vector3d): Vector3d {
    if (!velocity.x.isFinite() || !velocity.y.isFinite() || !velocity.z.isFinite()) {
        velocity.zero()
    }
    velocity.y += FLUID_PARCEL_GRAVITY * FLUID_PARCEL_TIME_STEP
    val speed = velocity.length()
    if (speed > MAX_FLUID_PARCEL_SPEED) {
        velocity.mul(MAX_FLUID_PARCEL_SPEED / speed)
    }
    return Vector3d(velocity).mul(FLUID_PARCEL_TIME_STEP)
}

internal fun fluidParcelMotionSubsteps(displacement: Vector3d): Int {
    val maximumComponent = max(abs(displacement.x), max(abs(displacement.y), abs(displacement.z)))
    return ceil(maximumComponent / MAX_FLUID_PARCEL_SUBSTEP_DISTANCE)
        .toInt()
        .coerceIn(1, MAX_FLUID_PARCEL_SUBSTEPS)
}

class WorldFluidOutflowManager @JvmOverloads constructor(
    private val maxActiveParcels: Int = DEFAULT_MAX_ACTIVE_PARCELS,
    private val maxParcelsPerTick: Int = DEFAULT_MAX_PARCELS_PER_TICK,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    // Keep the original three-argument JVM constructor and Kotlin default-argument ABI.
    constructor(maxActiveParcels: Int, maxParcelsPerTick: Int, maxSourceStorageBytes: Long,
        nanoTime: () -> Long = System::nanoTime) : this(maxActiveParcels, maxParcelsPerTick, nanoTime) {
        this.maxSourceStorageBytes = maxSourceStorageBytes
    }

    /** Backing-array payload plus prepaid checkpoint arrays; this is not a total JVM heap limit. */
    var maxSourceStorageBytes: Long
        get() = sourceStorage.limit
        set(value) {
            require(value >= sourceStorage.usedBytes && value > 0) { "Source storage limit is below retained ownership" }
            sourceStorage.limit = value
            clearSourceCapacityFailure()
        }

    constructor(maxActiveParcels: Int, maxParcelsPerTick: Int, maxSourceStorageBytes: Long,
        maxParcelStorageBytes: Long, nanoTime: () -> Long = System::nanoTime) :
        this(maxActiveParcels, maxParcelsPerTick, maxSourceStorageBytes, nanoTime) {
        this.maxParcelStorageBytes = maxParcelStorageBytes
    }

    /** Conservative owned-object envelopes and payload/copy allowances, not total JVM heap/RSS. */
    var maxParcelStorageBytes: Long
        get() = parcelStorage.limit
        set(value) {
            require(value >= parcelStorage.usedBytes && value > 0) { "Parcel storage limit is below retained ownership" }
            parcelStorage.limit = value
            clearSourceCapacityFailure()
        }

    open class StorageCapacityException internal constructor(
        val retainedBytes: Long, val requestedBytes: Long, val limitBytes: Long, internal val owner: Any,
    ) : IllegalStateException("World-fluid storage needs $requestedBytes additional bytes; " +
        "$retainedBytes/$limitBytes bytes are retained. Water and its placement intent remain owned.")

    class SourceStorageCapacityException internal constructor(
        retainedBytes: Long, requestedBytes: Long, limitBytes: Long, owner: Any,
    ) : StorageCapacityException(retainedBytes, requestedBytes, limitBytes, owner)

    private class ParcelStorage {
        var limit = DEFAULT_PARCEL_STORAGE_BYTES
        var usedBytes = MANAGER_STORAGE_BYTES
        var peakBytes = usedBytes
        fun fits(bytes: Long): Boolean = bytes >= 0 && bytes <= limit - usedBytes
        fun failure(bytes: Long) = StorageCapacityException(usedBytes, bytes, limit, this)
        fun reserve(bytes: Long) {
            if (!fits(bytes)) throw failure(bytes)
            usedBytes += bytes
            peakBytes = maxOf(peakBytes, usedBytes)
        }
        fun release(bytes: Long) { check(bytes in 0..usedBytes - MANAGER_STORAGE_BYTES); usedBytes -= bytes }
    }

    private class SourceStorage(private val parent: ParcelStorage) {
        var limit = DEFAULT_SOURCE_STORAGE_BYTES
        var usedBytes = 0L
        var peakBytes = 0L
        fun fits(bytes: Long): Boolean = bytes >= 0 && bytes <= limit - usedBytes && parent.fits(bytes)
        fun reserve(bytes: Long) {
            if (bytes < 0 || bytes > limit - usedBytes)
                throw SourceStorageCapacityException(usedBytes, bytes, limit, this)
            parent.reserve(bytes)
            usedBytes += bytes
            peakBytes = maxOf(peakBytes, usedBytes)
        }
        fun release(bytes: Long) { check(bytes in 0..usedBytes); usedBytes -= bytes; parent.release(bytes) }
    }

    private val parcelStorage = ParcelStorage()
    private val sourceStorage = SourceStorage(parcelStorage)
    private var sourceCapacityBlockedOwners = 0
    private var sourceCapacityFailure: StorageCapacityException? = null

    private fun clearSourceCapacityFailure() {
        if (sourceCapacityBlockedOwners != 0) {
            sourceCapacityBlockedOwners = 0
            sourceCapacityFailure = null
        }
    }

    private class SourceHistory(expected: Int, private val storage: SourceStorage) : LongOpenHashSet(expected) {
        val reservedBytes: Long get() = historyBytes(n)
        val arrayBytes: Long get() = key.size.toLong() * java.lang.Long.BYTES

        fun prepareToAdd(position: Long) {
            // fastutil inserts before rehashing. Admit and grow first, including for the zero key,
            // so capacity refusal cannot leave a partially inserted source or a world write.
            if (size >= maxFill && !contains(position)) rehash(HashCommon.arraySize(size + 2, f))
        }

        override fun add(position: Long): Boolean {
            prepareToAdd(position)
            return super.add(position)
        }

        override fun rehash(newN: Int) {
            val previous = reservedBytes
            val next = historyBytes(newN)
            storage.reserve(next) // Both arrays and their checkpoint allowances overlap until commit.
            try {
                super.rehash(newN)
            } catch (error: Throwable) {
                storage.release(next)
                throw error
            }
            storage.release(previous)
        }
    }

    private data class Parcel(
        val dimensionId: String,
        val worldPosition: Vector3d,
        val velocity: Vector3d,
        val fluidName: ResourceLocation,
        val fluid: FlowingFluid?,
        // Decimal subtraction preserves tails even when one block is below the double's ULP.
        var sourceUnits: BigDecimal,
        var lastUpdatedTick: Long,
        var settledSources: SourceHistory? = null,
        var settlementAnchor: BlockPos? = null,
        val visualId: UUID = UUID.randomUUID(),
        var visualRevision: Long = 0,
        var visualRecipients: Set<UUID> = emptySet(),
        val metadataBytes: Long,
        val momentum: VsiFluidParcelMomentum? = null,
    ) : PlacementState() {
        var queueNext: Parcel? = null
        var interception: ParcelInterception? = null
        var visualStorageBytes = 0L
        var consumed = false
        var collectionAgeTicks = -1L
        var surfaceMotion = SurfaceMotion.NONE
        val mergeFluid = fluid?.source
        val mergeFluidHash = mergeFluid?.let { BuiltInRegistries.FLUID.getKey(it).hashCode() } ?: 0
        var mergeNext: Parcel? = null
        var mergePrevious: Parcel? = null
        var mergeBucket = -1
        var mergeX = 0
        var mergeY = 0
        var mergeZ = 0
        fun blockPosition(): BlockPos =
            BlockPos.containing(worldPosition.x, worldPosition.y, worldPosition.z)
    }

    private class ParcelInterception(val id: UUID, val end: Vector3d, val velocity: Vector3d,
        var applied: Boolean = false, var collisionFraction: Double = 1.0)

    private data class PendingPlacement(
        val pos: BlockPos,
        val before: BlockState?,
        val after: BlockState?,
        val deposited: BigDecimal,
        var scheduledOnly: Boolean = false,
        val scheduleAttempted: Boolean = false,
        val unavailableBefore: CompoundTag? = null,
        val unavailableAfter: CompoundTag? = null,
        val storageBytes: Long = 0L,
        val unitsBefore: BigDecimal? = null,
        val unitsAfter: BigDecimal? = null,
    )

    // Journal fields live in the existing owner/scratch object, without a per-deposit allocation.
    private open class PlacementState {
        var placementStorageBytes = 0L
        var hasPlacement = false
        var placementX = 0
        var placementY = 0
        var placementZ = 0
        fun placementPosition(): BlockPos = BlockPos(placementX, placementY, placementZ)
        fun setPlacementPosition(pos: BlockPos) {
            placementX = pos.x; placementY = pos.y; placementZ = pos.z
            hasPlacement = true
        }
        var placementBefore: BlockState? = null
        var placementAfter: BlockState? = null
        var placementDeposited: BigDecimal? = null
        var placementUnitsBefore: BigDecimal? = null
        var placementUnitsAfter: BigDecimal? = null
        var placementScheduledOnly = false
        var placementScheduleAttempted = false
        var placementUnavailableBefore: CompoundTag? = null
        var placementUnavailableAfter: CompoundTag? = null

        fun clearPlacement(storage: ParcelStorage) {
            if (!hasPlacement && placementStorageBytes == 0L && placementUnavailableBefore == null
                && placementUnavailableAfter == null) return
            if (placementStorageBytes != 0L) {
                storage.release(placementStorageBytes)
                placementStorageBytes = 0L
            }
            hasPlacement = false; placementBefore = null; placementAfter = null; placementDeposited = null
            placementUnitsBefore = null; placementUnitsAfter = null
            placementScheduledOnly = false; placementScheduleAttempted = false
            placementUnavailableBefore = null; placementUnavailableAfter = null
        }

        fun takePlacement(other: PlacementState) {
            check(!hasPlacement && placementStorageBytes == 0L)
            hasPlacement = other.hasPlacement
            placementX = other.placementX; placementY = other.placementY; placementZ = other.placementZ
            placementBefore = other.placementBefore; placementAfter = other.placementAfter
            placementDeposited = other.placementDeposited; placementScheduledOnly = other.placementScheduledOnly
            placementUnitsBefore = other.placementUnitsBefore; placementUnitsAfter = other.placementUnitsAfter
            placementScheduleAttempted = other.placementScheduleAttempted
            placementUnavailableBefore = other.placementUnavailableBefore; placementUnavailableAfter = other.placementUnavailableAfter
            placementStorageBytes = other.placementStorageBytes
            other.placementStorageBytes = 0L
            other.hasPlacement = false
            other.placementUnavailableBefore = null; other.placementUnavailableAfter = null
        }
    }

    // Service cannot reenter, so one bounded scratch state isolates callbacks from an in-flight tick.
    private class MotionState : PlacementState() {
        val worldPosition = Vector3d()
        val velocity = Vector3d()
        var settlementAnchor: BlockPos? = null
        var collectionAgeTicks = -1L
        var surfaceMotion = SurfaceMotion.NONE
        fun blockPosition(): BlockPos = BlockPos.containing(worldPosition.x, worldPosition.y, worldPosition.z)
    }

    private val motion = MotionState()
    private var servicing = false
    private var clearVisualsRequested = false
    private var visualChanges = ArrayList<VisualChange>()
    private var visualBatchBytes = 0L

    private class VisualChange(
        val parcel: Parcel,
        val packet: PacketFluidParcel,
        val viewers: List<ServerPlayer>,
        val leaving: List<ServerPlayer>,
        val recipients: Set<UUID>,
    )

    private data class VisualRecipient(val player: ServerPlayer, val dimension: String)

    private enum class ParcelMotionResult {
        MOVED,
        BLOCKED,
        CONSUMED,
        RETRY_LATER,
    }

    // Links live in the charged owners. Unlike a deque backing array, capacity cannot remain
    // allocated after the owners that paid for it drain.
    private class ParcelQueue : Iterable<Parcel> {
        private var head: Parcel? = null
        private var tail: Parcel? = null
        var size = 0
            private set
        val first: Parcel get() = checkNotNull(head)
        fun addLast(parcel: Parcel) {
            check(parcel.queueNext == null)
            if (tail == null) head = parcel else tail!!.queueNext = parcel
            tail = parcel
            size++
        }
        fun removeFirst(): Parcel {
            val result = first
            head = result.queueNext
            result.queueNext = null
            if (--size == 0) tail = null
            return result
        }
        override fun iterator(): Iterator<Parcel> = object : Iterator<Parcel> {
            private var next = head
            override fun hasNext() = next != null
            override fun next(): Parcel {
                val result = next ?: throw NoSuchElementException()
                next = result.queueNext
                return result
            }
        }
    }

    private var parcels = ParcelQueue()
    private val mergeBuckets = arrayOfNulls<Parcel>(1024)
    private var currentTick = 0L
    private var pressureClockNanos: Long? = null
    private var pressureAdvancedTime = false
    private var sweepReceiverMotion = true
    private var parcelsWithDueMotion = 0

    private enum class ParcelUpdateResult { MOVED, PAUSED, CONSUMED }
    private enum class SurfaceMotion { NONE, RUNOFF, SPLASH }

    private fun canMerge(parcel: Parcel): Boolean = !parcel.consumed && !parcel.hasPlacement &&
        parcel.interception == null && parcel.momentum == null && parcel.settlementAnchor == null &&
        parcel.settledSources == null && parcel.surfaceMotion == SurfaceMotion.NONE &&
        parcel.mergeFluid != null && parcel.sourceUnits.signum() > 0 &&
        parcel.sourceUnits < MAX_MERGED_PARCEL_UNITS

    private fun mergeBucket(parcel: Parcel, x: Int, y: Int, z: Int): Int =
        HashCommon.mix(BlockPos.asLong(x, y, z) xor parcel.dimensionId.hashCode().toLong() xor
            parcel.mergeFluidHash.toLong()).toInt() and (mergeBuckets.size - 1)

    private fun indexParcel(parcel: Parcel) {
        if (parcel.mergeBucket >= 0 || !canMerge(parcel)) return
        parcel.mergeX = floor(parcel.worldPosition.x).toInt()
        parcel.mergeY = floor(parcel.worldPosition.y).toInt()
        parcel.mergeZ = floor(parcel.worldPosition.z).toInt()
        val bucket = mergeBucket(parcel, parcel.mergeX, parcel.mergeY, parcel.mergeZ)
        parcel.mergeBucket = bucket
        parcel.mergeNext = mergeBuckets[bucket]
        parcel.mergeNext?.mergePrevious = parcel
        mergeBuckets[bucket] = parcel
    }

    private fun unindexParcel(parcel: Parcel) {
        if (parcel.mergeBucket < 0) return
        val previous = parcel.mergePrevious
        if (previous == null) mergeBuckets[parcel.mergeBucket] = parcel.mergeNext
        else previous.mergeNext = parcel.mergeNext
        parcel.mergeNext?.mergePrevious = previous
        parcel.mergePrevious = null
        parcel.mergeNext = null
        parcel.mergeBucket = -1
    }

    private fun mergeParcel(level: ServerLevel, donor: Parcel, fluid: FlowingFluid): Boolean {
        if (!canMerge(donor)) return false
        val x = floor(donor.worldPosition.x).toInt()
        val y = floor(donor.worldPosition.y).toInt()
        val z = floor(donor.worldPosition.z).toInt()
        val collecting = donor.collectionAgeTicks >= 0
        val collectionLifetime = VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds.coerceIn(0, 60) * 20L
        var visited = 0
        var closest: Parcel? = null
        var distance = if (collecting) 3.0 else 0.25
        search@ for (dx in MERGE_CELL_OFFSETS) for (dy in MERGE_CELL_OFFSETS) for (dz in MERGE_CELL_OFFSETS) {
            var candidate = mergeBuckets[mergeBucket(donor, x + dx, y + dy, z + dz)]
            while (candidate != null) {
                if (++visited > 64) break@search
                val target = candidate
                candidate = target.mergeNext
                if (target.mergeX != x + dx || target.mergeY != y + dy || target.mergeZ != z + dz ||
                    target.dimensionId != donor.dimensionId || target.mergeFluid !== donor.mergeFluid ||
                    !collecting && target.lastUpdatedTick != donor.lastUpdatedTick ||
                    (target.collectionAgeTicks >= 0) != collecting ||
                    collecting && (dx != 0 || dy != 0 || dz != 0 ||
                        target.collectionAgeTicks + (currentTick - target.lastUpdatedTick) >= collectionLifetime ||
                        donor.collectionAgeTicks + (currentTick - donor.lastUpdatedTick) >= collectionLifetime) ||
                    target.velocity.distanceSquared(donor.velocity) > 4.0 ||
                    target.sourceUnits + donor.sourceUnits > MAX_MERGED_PARCEL_UNITS) continue
                val separation = target.worldPosition.distanceSquared(donor.worldPosition)
                if (separation <= distance) {
                    closest = target
                    distance = separation
                }
            }
        }
        val target = closest ?: return false
        val displacement = Vector3d(target.worldPosition).sub(donor.worldPosition)
        if (parcelTerrainLimit(level, donor, donor.worldPosition, displacement, fluid) < 1.0 ||
            !mergePathClearOfShips(level, donor, target)) return false
        val total = target.sourceUnits + donor.sourceUnits
        val weight = donor.sourceUnits.divide(total, MathContext.DECIMAL128).toDouble()
        val position = Vector3d(target.worldPosition).lerp(donor.worldPosition, weight)
        val velocity = Vector3d(target.velocity).lerp(donor.velocity, weight)
        unindexParcel(target)
        target.sourceUnits = total
        target.worldPosition.set(position)
        target.velocity.set(velocity)
        if (collecting) target.collectionAgeTicks = maxOf(target.collectionAgeTicks,
            donor.collectionAgeTicks + (target.lastUpdatedTick - donor.lastUpdatedTick))
        donor.sourceUnits = BigDecimal.ZERO
        donor.consumed = true
        indexParcel(target)
        showVisual(level, target, requireNotNull(target.fluid))
        return true
    }

    private fun mergePathClearOfShips(level: ServerLevel, donor: Parcel, target: Parcel): Boolean {
        val world = (level.server as? IShipObjectWorldServerProvider)?.shipObjectWorld ?: return true
        var reserved = 0L
        return try {
            WorldFluidParcelReceivers.collect(world, donor.dimensionId, donor.worldPosition,
                target.worldPosition, sweepReceiverMotion) { bytes ->
                parcelStorage.reserve(bytes)
                reserved += bytes
            }?.isEmpty() == true
        } catch (_: StorageCapacityException) {
            false
        } finally {
            parcelStorage.release(reserved)
        }
    }

    fun enqueue(
        level: ServerLevel?,
        outflow: VsiFluidOutflow,
    ): VsiFluidOutflowResult {
        if (level == null || !outflow.worldPositionX.isFinite() || !outflow.worldPositionY.isFinite()
            || !outflow.worldPositionZ.isFinite() || !outflow.worldVolume.isFinite()
            || outflow.worldVolume <= 0.0) {
            return VsiFluidOutflowResult.RETRY_LATER
        }
        val fluid = MassDatapackResolver.getFlowingFluid(outflow.fluidId)
            ?: return VsiFluidOutflowResult.UNSUPPORTED_FLUID
        return enqueueParcel(outflow.dimensionId, fluid,
            Vector3d(outflow.worldPositionX, outflow.worldPositionY, outflow.worldPositionZ),
            Vector3d(outflow.worldVelocityX, outflow.worldVelocityY, outflow.worldVelocityZ),
            BigDecimal.valueOf(outflow.worldVolume) * FULL_SOURCE, outflow.momentum)
    }

    internal fun enqueueExact(dimension: String, fluidName: String, position: Vector3d, velocity: Vector3d,
        units: BigDecimal): VsiFluidOutflowResult {
        require(units.signum() > 0 && units <= MAX_PARCEL_UNITS && units.scale() in -308..325 && units.precision() <= 640)
        require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite())
        val name = ResourceLocation.parse(fluidName)
        val fluid = if (BuiltInRegistries.FLUID.containsKey(name)) BuiltInRegistries.FLUID.get(name) as? FlowingFluid else null
        return if (fluid == null) VsiFluidOutflowResult.UNSUPPORTED_FLUID
            else enqueueParcel(dimension, fluid, position, velocity, units)
    }

    private fun enqueueParcel(dimension: String, fluid: FlowingFluid, position: Vector3d, velocity: Vector3d,
        units: BigDecimal, momentum: VsiFluidParcelMomentum? = null,
        surfaceMotion: SurfaceMotion = SurfaceMotion.NONE,
    ): VsiFluidOutflowResult {
        if (parcels.size >= maxActiveParcels.coerceAtLeast(1)) {
            return VsiFluidOutflowResult.RETRY_LATER
        }
        val fluidName = BuiltInRegistries.FLUID.getKey(fluid)
        val bytes = PARCEL_STORAGE_BYTES + nameStorageBytes(dimension.length.toLong(),
            fluidName.namespace.length.toLong() + fluidName.path.length + 1)
        if (!parcelStorage.fits(bytes)) {
            if (bytes > parcelStorage.limit - MANAGER_STORAGE_BYTES) throw parcelStorage.failure(bytes)
            return VsiFluidOutflowResult.RETRY_LATER
        }
        parcelStorage.reserve(bytes)
        try {
            val parcel = Parcel(
                dimensionId = dimension,
                worldPosition = Vector3d(position),
                velocity = Vector3d(velocity).also {
                    // Match the motion integrator's recovery before this owner can be saved.
                    if (!it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite()) it.zero()
                },
                fluid = fluid,
                fluidName = fluidName,
                sourceUnits = units,
                lastUpdatedTick = currentTick,
                metadataBytes = bytes,
                momentum = momentum,
            )
            parcel.surfaceMotion = surfaceMotion
            parcels.addLast(parcel)
            indexParcel(parcel)
        } catch (error: Throwable) {
            parcelStorage.release(bytes)
            throw error
        }
        return VsiFluidOutflowResult.APPLIED
    }

    fun tick(levels: Map<String, ServerLevel>) {
        if (servicing) return
        sweepReceiverMotion = !pressureAdvancedTime
        // Pressure servicing already supplied the elapsed time for this game tick.
        // Its normal tail may drain remaining work, but must not advance that time again.
        if (!pressureAdvancedTime) {
            currentTick++
            parcelsWithDueMotion = parcels.size
        }
        pressureClockNanos = null
        pressureAdvancedTime = false
        serviceParcels(levels, maxParcelsPerTick.coerceAtLeast(1))
    }

    /** Continue emitted water at wall-clock 20 Hz while the game is waiting for physics. */
    fun servicePressure(levels: Map<String, ServerLevel>): Boolean {
        if (servicing) return false
        sweepReceiverMotion = false
        val now = nanoTime()
        val previous = pressureClockNanos
        if (previous == null) {
            pressureClockNanos = now
        } else {
            val elapsed = (now - previous) / FLUID_PARCEL_TICK_NS
            if (elapsed > 0) {
                currentTick += elapsed
                parcelsWithDueMotion = parcels.size
                pressureClockNanos = previous + elapsed * FLUID_PARCEL_TICK_NS
                pressureAdvancedTime = true
            }
        }
        serviceParcels(levels, minOf(maxParcelsPerTick.coerceAtLeast(1), DEFAULT_MAX_PARCELS_PER_TICK))
        // Catch up in further bounded passes before sleeping. Counting avoids rescanning
        // the whole queue on each pass, including when due parcels follow current ones.
        return parcelsWithDueMotion > 0
    }

    private fun serviceParcels(levels: Map<String, ServerLevel>, parcelLimit: Int) {
        if (servicing) return
        servicing = true
        var failure: Throwable? = null
        var pendingRetirements = false
        try {
            var motionStepsLeft = MAX_PARCEL_MOTION_STEPS_PER_PASS
            var parcelsLeft = minOf(parcels.size, parcelLimit)
            while (parcelsLeft-- > 0 && motionStepsLeft > 0) {
                // The selected owner stays capacity-counted and saveable during every callback.
                val parcel = parcels.first
                unindexParcel(parcel)
                val wasDue = parcel.lastUpdatedTick < currentTick
                var consumed = false
                try {
                    val elapsed = minOf(currentTick - parcel.lastUpdatedTick, motionStepsLeft)
                    if (elapsed <= 0 && !parcel.hasPlacement && !parcel.consumed) {
                        clearSourceCapacityFailure() // This owner still has a future motion turn.
                        parcels.addLast(parcels.removeFirst())
                        continue
                    }
                    motionStepsLeft -= elapsed
                    val level = levels[parcel.dimensionId]
                    val fluid = parcel.fluid
                    if (level == null || fluid == null) {
                        if (sourceCapacityBlockedOwners < parcels.size) sourceCapacityBlockedOwners++
                        parcel.lastUpdatedTick = currentTick
                        parcels.addLast(parcels.removeFirst())
                        continue
                    }
                    val previousStorage = parcelStorage.usedBytes
                    val previousVolume = parcel.sourceUnits
                    var result = if (parcel.consumed) ParcelUpdateResult.CONSUMED else updateParcel(level, parcel, fluid, elapsed)
                    if (result == ParcelUpdateResult.PAUSED) parcel.lastUpdatedTick = currentTick
                    if (result != ParcelUpdateResult.CONSUMED && mergeParcel(level, parcel, fluid))
                        result = ParcelUpdateResult.CONSUMED
                    if (result == ParcelUpdateResult.CONSUMED) {
                        parcel.consumed = true
                        restoreVisual(level, parcel)
                        parcels.removeFirst()
                        if (parcel.visualRecipients.isEmpty()) {
                            releaseConsumedParcel(parcel)
                        } else {
                            parcel.lastUpdatedTick = currentTick
                            parcels.addLast(parcel)
                            pendingRetirements = true
                        }
                        consumed = true
                    } else {
                        if (result == ParcelUpdateResult.PAUSED) parcel.lastUpdatedTick = currentTick
                        if (parcel.sourceUnits.signum() > 0) showVisual(level, parcel, fluid)
                        parcels.addLast(parcels.removeFirst())
                    }
                    if (result == ParcelUpdateResult.PAUSED && parcel.sourceUnits === previousVolume
                        && parcelStorage.usedBytes >= previousStorage) {
                        // An updated clock does not make a blocked water owner able to drain.
                        if (sourceCapacityBlockedOwners < parcels.size) sourceCapacityBlockedOwners++
                    } else clearSourceCapacityFailure()
                } catch (capacity: StorageCapacityException) {
                    motion.clearPlacement(parcelStorage)
                    if (capacity.owner !== sourceStorage && capacity.owner !== parcelStorage) throw capacity
                    // A later parcel can finish and free capacity. Give it its normal turn before
                    // reporting exhaustion; the blocked owner keeps its exact pending placement.
                    if (capacity.requestedBytes < (sourceCapacityFailure?.requestedBytes ?: Long.MAX_VALUE))
                        sourceCapacityFailure = capacity
                    if (sourceCapacityBlockedOwners < parcels.size) sourceCapacityBlockedOwners++
                    parcels.addLast(parcels.removeFirst())
                } catch (error: Throwable) {
                    motion.clearPlacement(parcelStorage)
                    throw error
                } finally {
                    if (!consumed) indexParcel(parcel)
                    if (wasDue && (consumed || parcel.lastUpdatedTick == currentTick)) parcelsWithDueMotion--
                }
            }
            flushVisualChanges()
            if (pendingRetirements) {
                releaseDeliveredRetirements()
                pendingRetirements = false
            }
            sourceCapacityFailure?.let {
                val fits = if (it.owner === sourceStorage) sourceStorage.fits(it.requestedBytes)
                    else parcelStorage.fits(it.requestedBytes)
                if (fits) clearSourceCapacityFailure()
                else if (sourceCapacityBlockedOwners >= parcels.size) {
                    if (it.owner === sourceStorage) throw SourceStorageCapacityException(
                        sourceStorage.usedBytes, it.requestedBytes, sourceStorage.limit, sourceStorage)
                    throw parcelStorage.failure(it.requestedBytes)
                }
            }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            discardVisualChanges()
            if (pendingRetirements) releaseDeliveredRetirements()
            try {
                if (clearVisualsRequested) clearVisualsInternal(levels)
            } catch (cleanup: Throwable) {
                if (failure == null) throw cleanup
                if (cleanup !== failure) failure.addSuppressed(cleanup)
            } finally {
                clearVisualsRequested = false
                servicing = false
            }
        }
    }

    private fun releaseConsumedParcel(parcel: Parcel) {
        parcel.settledSources?.let { sourceStorage.release(it.reservedBytes) }
        parcelStorage.release(parcel.metadataBytes)
    }

    private fun releaseDeliveredRetirements() {
        repeat(parcels.size) {
            val parcel = parcels.removeFirst()
            if (parcel.consumed && parcel.lastUpdatedTick == currentTick && parcel.visualRecipients.isEmpty()) {
                releaseConsumedParcel(parcel)
            } else parcels.addLast(parcel)
        }
    }

    /** Pure motion is staged; callbacks see the last committed tick plus any placement intent. */
    private fun updateParcel(level: ServerLevel, parcel: Parcel, fluid: FlowingFluid, elapsed: Long): ParcelUpdateResult {
        if (parcel.hasPlacement) {
            finishPlacement(level, parcel, fluid)
            return if (parcel.sourceUnits.signum() == 0 && !parcel.hasPlacement)
                ParcelUpdateResult.CONSUMED else ParcelUpdateResult.PAUSED
        }
        if (parcel.sourceUnits.signum() == 0 && parcel.interception == null) return ParcelUpdateResult.CONSUMED
        parcel.interception?.let { pending ->
            if (!pending.applied) {
                val world = (level.server as? IShipObjectWorldServerProvider)?.shipObjectWorld
                    ?: return ParcelUpdateResult.PAUSED
                world.consumeFluidParcelInterception(pending.id) { receipt ->
                    val remaining = parcel.sourceUnits - BigDecimal.valueOf(receipt.acceptedAmount)
                    check(remaining.signum() >= 0) { "Parcel receiver accepted more water than the parcel owns" }
                    parcel.sourceUnits = remaining
                    pending.collisionFraction = receipt.collisionFraction
                    pending.applied = true
                }
                if (!pending.applied) return ParcelUpdateResult.PAUSED
            }
            if (parcel.sourceUnits.signum() == 0) return ParcelUpdateResult.CONSUMED
        }
        val staged = motion
        staged.worldPosition.set(parcel.worldPosition)
        staged.velocity.set(parcel.velocity)
        staged.settlementAnchor = parcel.settlementAnchor
        staged.collectionAgeTicks = parcel.collectionAgeTicks
        staged.surfaceMotion = parcel.surfaceMotion
        staged.clearPlacement(parcelStorage)
        if (parcel.interception?.applied == true) {
            val result = updateOneTick(level, parcel, staged, fluid)
            parcel.worldPosition.set(staged.worldPosition)
            parcel.velocity.set(staged.velocity)
            parcel.settlementAnchor = staged.settlementAnchor
            parcel.collectionAgeTicks = staged.collectionAgeTicks
            parcel.surfaceMotion = staged.surfaceMotion
            if (staged.hasPlacement) parcel.takePlacement(staged)
            parcel.interception = null
            if (parcel.hasPlacement) {
                finishPlacement(level, parcel, fluid)
                return if (parcel.sourceUnits.signum() == 0 && !parcel.hasPlacement)
                    ParcelUpdateResult.CONSUMED else ParcelUpdateResult.PAUSED
            }
            if (result != ParcelUpdateResult.MOVED) return result
        }
        for (tick in 0 until elapsed) {
            val result = updateOneTick(level, parcel, staged, fluid)
            parcel.worldPosition.set(staged.worldPosition)
            parcel.velocity.set(staged.velocity)
            parcel.settlementAnchor = staged.settlementAnchor
            parcel.collectionAgeTicks = staged.collectionAgeTicks
            parcel.surfaceMotion = staged.surfaceMotion
            if (staged.hasPlacement) parcel.takePlacement(staged)
            parcel.lastUpdatedTick++
            if (parcel.hasPlacement) {
                finishPlacement(level, parcel, fluid)
                return if (parcel.sourceUnits.signum() == 0 && !parcel.hasPlacement)
                    ParcelUpdateResult.CONSUMED else ParcelUpdateResult.PAUSED
            }
            if (result != ParcelUpdateResult.MOVED) return result
        }
        return ParcelUpdateResult.MOVED
    }

    private fun updateOneTick(level: ServerLevel, parcel: Parcel, staged: MotionState, fluid: FlowingFluid): ParcelUpdateResult {
        staged.settlementAnchor?.let { anchor ->
            // One bounded expansion, no flood-fill or extra queue slots for a large event.
            for (candidate in listOf(anchor) + SETTLEMENT_DIRECTIONS.map { anchor.relative(it) }) {
                if (candidate.y < level.minBuildHeight || candidate.y >= level.maxBuildHeight
                    || !level.hasChunkAt(candidate)) continue
                val state = level.getBlockState(candidate)
                if (isOcean(level, parcel, candidate, state, fluid)) return ParcelUpdateResult.CONSUMED
                if (!canReceive(level, candidate, state, fluid)) continue
                if (fluid.isSame(state.fluidState.type)) {
                    val units = sourceAmount(level, parcel, candidate)
                    if (units != null && units >= FULL_SOURCE || units == null && state.fluidState.amount >= 8) continue
                }
                staged.worldPosition.set(candidate.x + 0.5, candidate.y + 0.5, candidate.z + 0.5)
                staged.velocity.zero()
                staged.settlementAnchor = null
                break
            }
            if (staged.settlementAnchor != null) return ParcelUpdateResult.PAUSED
        }
        val start = staged.blockPosition()
        if (start.y < level.minBuildHeight) return ParcelUpdateResult.CONSUMED // The ordinary world void.
        if (!level.hasChunkAt(start)) return ParcelUpdateResult.PAUSED
        if (sourceAmount(level, parcel, start) != null) {
            val startState = level.getBlockState(start)
            if (fluid.isSame(startState.fluidState.type)) {
                prepareSettlement(level, parcel, staged, fluid, start, startState)
                return ParcelUpdateResult.PAUSED
            }
        }
        val surfaceMotion = staged.surfaceMotion
        val terrainMotion = advanceParcel(level, parcel, staged, fluid, start)
        when (terrainMotion) {
            ParcelMotionResult.CONSUMED -> return ParcelUpdateResult.CONSUMED
            ParcelMotionResult.RETRY_LATER -> return ParcelUpdateResult.PAUSED // Unavailable terrain pauses elapsed time.
            ParcelMotionResult.MOVED,
            ParcelMotionResult.BLOCKED -> Unit
        }
        if (staged.hasPlacement) return ParcelUpdateResult.PAUSED
        val currentPos = staged.blockPosition()
        val belowPos = currentPos.below()
        if (!level.hasChunkAt(currentPos) || !level.hasChunkAt(belowPos)) {
            return ParcelUpdateResult.PAUSED
        }
        val hitCurrent = level.getBlockState(currentPos)
        val fromCurrent = if (staged.velocity.y < 0.0) currentPos.above() else currentPos
        val currentState = if (reactToFluidImpact(level, parcel, fluid, fromCurrent, currentPos, hitCurrent)) {
            if (parcel.sourceUnits.signum() == 0) return ParcelUpdateResult.CONSUMED
            level.getBlockState(currentPos)
        } else hitCurrent
        val hitBelow = level.getBlockState(belowPos)
        val belowState = if (staged.velocity.y <= 0.0 && reactToFluidImpact(level, parcel, fluid, currentPos, belowPos, hitBelow)) {
            if (parcel.sourceUnits.signum() == 0) return ParcelUpdateResult.CONSUMED
            level.getBlockState(belowPos)
        } else hitBelow
        if (sourceAmount(level, parcel, belowPos) != null && fluid.isSame(belowState.fluidState.type)) {
            prepareSettlement(level, parcel, staged, fluid, belowPos, belowState)
            return ParcelUpdateResult.PAUSED
        }
        when (
            chooseFluidParcelAction(
                currentReplaceable = canReceive(level, currentPos, currentState, fluid),
                currentIsSameSource = isOcean(level, parcel, currentPos, currentState, fluid),
                belowReplaceable =
                    belowState.fluidState.isEmpty && canReceive(level, belowPos, belowState, fluid),
                belowIsSameSource = isOcean(level, parcel, belowPos, belowState, fluid),
            )
        ) {
            FluidParcelAction.FALL -> {
                if (surfaceMotion == SurfaceMotion.RUNOFF) {
                    staged.velocity.x = 0.0
                    staged.velocity.z = 0.0
                }
                staged.surfaceMotion = SurfaceMotion.NONE
                staged.collectionAgeTicks = -1
            }
            FluidParcelAction.MERGE -> return ParcelUpdateResult.CONSUMED
            FluidParcelAction.WAIT -> {
                for (direction in SETTLEMENT_DIRECTIONS) {
                    val adjacent = currentPos.relative(direction)
                    if (adjacent.y < level.minBuildHeight || adjacent.y >= level.maxBuildHeight ||
                        !level.hasChunkAt(adjacent)) continue
                    if (isOcean(level, parcel, adjacent, level.getBlockState(adjacent), fluid)) {
                        return ParcelUpdateResult.CONSUMED
                    }
                }
                // An outlet edited closed must not destroy already accepted ownership.
                if (parcel.settledSources?.isEmpty() == false) staged.settlementAnchor = currentPos
                return ParcelUpdateResult.PAUSED
            }
            FluidParcelAction.SETTLE -> {
                if (staged.velocity.y <= 0.0) {
                    staged.surfaceMotion = SurfaceMotion.NONE
                    if (staged.collectionAgeTicks < 0 && isImpactWater(fluid) && currentState.isAir &&
                        continueSurfaceMotion(level, parcel, staged, fluid, currentPos, surfaceMotion, terrainMotion)) {
                        return ParcelUpdateResult.MOVED
                    }
                    if (currentState.fluidState.isEmpty && parcel.sourceUnits < MIN_SETTLED_UNITS) {
                        val lifetime = VSGameConfig.SERVER.SpilledWater.parcelCollectionSeconds.coerceIn(0, 60) * 20L
                        staged.velocity.zero()
                        if (staged.collectionAgeTicks >= 0) staged.worldPosition.set(parcel.worldPosition)
                        staged.collectionAgeTicks++
                        return if (staged.collectionAgeTicks >= lifetime) ParcelUpdateResult.CONSUMED
                            else ParcelUpdateResult.MOVED
                    }
                    staged.collectionAgeTicks = -1
                    prepareSettlement(level, parcel, staged, fluid, currentPos, currentState)
                    return ParcelUpdateResult.PAUSED
                }
            }
        }
        return ParcelUpdateResult.MOVED
    }

    private fun continueSurfaceMotion(
        level: ServerLevel, parcel: Parcel, staged: MotionState, fluid: FlowingFluid, position: BlockPos,
        previous: SurfaceMotion, terrainMotion: ParcelMotionResult,
    ): Boolean {
        val aboveSurface = terrainMotion == ParcelMotionResult.MOVED &&
            staged.worldPosition.y > position.y + SURFACE_HEIGHT
        val canSplash = parcel.sourceUnits >= MIN_SETTLED_UNITS
        if (canSplash && previous == SurfaceMotion.NONE && aboveSurface &&
            (staged.velocity.y < -MIN_VERTICAL_SPLASH_SPEED ||
                Math.hypot(staged.velocity.x, staged.velocity.z) >= MIN_HORIZONTAL_SPLASH_SPEED)) {
            return true
        }
        if (previous != SurfaceMotion.RUNOFF || terrainMotion != ParcelMotionResult.BLOCKED) {
            val runoff = runoffDirection(level, position, fluid, staged.velocity)
            if (runoff != null) {
                if (previous == SurfaceMotion.NONE && aboveSurface) return true
                startSurfaceMotion(staged, position, SurfaceMotion.RUNOFF,
                    runoff.stepX * RUNOFF_SPEED, runoff.stepZ * RUNOFF_SPEED)
                return true
            }
        }
        if (!canSplash || previous == SurfaceMotion.RUNOFF) return false
        if (previous == SurfaceMotion.SPLASH) {
            if (terrainMotion == ParcelMotionResult.BLOCKED) return false
            val x = staged.velocity.x * SPLASH_DAMPING
            val z = staged.velocity.z * SPLASH_DAMPING
            if (Math.hypot(x, z) < MIN_SURFACE_SPEED) return false
            startSurfaceMotion(staged, position, SurfaceMotion.SPLASH, x, z)
            return true
        }
        val horizontalSpeed = Math.hypot(staged.velocity.x, staged.velocity.z)
        val verticalSpeed = -staged.velocity.y
        if (horizontalSpeed < MIN_HORIZONTAL_SPLASH_SPEED && verticalSpeed < MIN_VERTICAL_SPLASH_SPEED) return false
        val speed = minOf(MAX_SPLASH_SPEED, horizontalSpeed * 0.55 + verticalSpeed * 0.35)
        if (horizontalSpeed >= MIN_SURFACE_SPEED) {
            startSurfaceMotion(staged, position, SurfaceMotion.SPLASH,
                staged.velocity.x * (speed / horizontalSpeed), staged.velocity.z * (speed / horizontalSpeed))
            return true
        }
        val offsetX = staged.worldPosition.x - (position.x + 0.5)
        val offsetZ = staged.worldPosition.z - (position.z + 0.5)
        val directions = RUNOFF_DIRECTIONS.filter {
            val adjacent = position.relative(it)
            level.hasChunkAt(adjacent) && level.getBlockState(adjacent).isAir
        }
        val direction = directions.maxByOrNull { it.stepX * offsetX + it.stepZ * offsetZ } ?: return false
        startSurfaceMotion(staged, position, SurfaceMotion.SPLASH, direction.stepX * speed, direction.stepZ * speed)
        if (verticalSpeed >= MIN_SPLIT_IMPACT_SPEED && direction.opposite in directions &&
            parcel.sourceUnits >= MIN_SPLIT_SPLASH_UNITS) {
            val share = parcel.sourceUnits.divide(SPLASH_SPLIT_DIVISOR, maxOf(3, parcel.sourceUnits.scale()),
                RoundingMode.DOWN)
            val oppositeVelocity = Vector3d(-staged.velocity.x, 0.0, -staged.velocity.z)
            if (enqueueParcel(parcel.dimensionId, fluid, staged.worldPosition, oppositeVelocity, share,
                    parcel.momentum, SurfaceMotion.SPLASH) == VsiFluidOutflowResult.APPLIED) {
                parcel.sourceUnits -= share
            }
        }
        return true
    }

    private fun runoffDirection(
        level: ServerLevel, position: BlockPos, fluid: FlowingFluid, velocity: Vector3d,
    ): Direction? {
        if (position.y <= level.minBuildHeight) return null
        var selected: Direction? = null
        var alignment = Double.NEGATIVE_INFINITY
        for (direction in RUNOFF_DIRECTIONS) {
            val adjacent = position.relative(direction)
            if (!level.hasChunkAt(adjacent) || !level.getBlockState(adjacent).isAir) continue
            val lower = adjacent.below()
            val state = level.getBlockState(lower)
            if (state.isAir || fluid.isSame(state.fluidState.type) && state.fluidState.isSource &&
                state == state.fluidState.createLegacyBlock() && !FractionalFluidSources.owns(level, lower)) {
                val score = direction.stepX * velocity.x + direction.stepZ * velocity.z
                if (score > alignment) {
                    selected = direction
                    alignment = score
                }
            }
        }
        return selected
    }

    private fun startSurfaceMotion(staged: MotionState, position: BlockPos, motion: SurfaceMotion, x: Double, z: Double) {
        staged.surfaceMotion = motion
        staged.collectionAgeTicks = -1
        staged.worldPosition.y = position.y + SURFACE_HEIGHT
        staged.velocity.set(x, 0.0, z)
    }

    private fun canReceive(state: BlockState, fluid: FlowingFluid): Boolean =
        if (fluid.isSame(state.fluidState.type)) {
            // Do not replace a waterlogged solid or a source that already owns its volume.
            !state.fluidState.isSource && state == state.fluidState.createLegacyBlock()
        } else state.fluidState.isEmpty && state.canBeReplaced()

    private fun sourceAmount(level: ServerLevel, parcel: Parcel, pos: BlockPos): BigDecimal? =
        FractionalFluidSources.forLevel(level)?.amount(parcel.dimensionId, pos.x, pos.y, pos.z,
            FractionalFluidSources.fluidName(requireNotNull(parcel.fluid)))

    private fun isOcean(level: ServerLevel, parcel: Parcel, pos: BlockPos, state: BlockState, fluid: FlowingFluid): Boolean =
        fluid.isSame(state.fluidState.type) && state.fluidState.isSource
            && parcel.settledSources?.contains(pos.asLong()) != true && sourceAmount(level, parcel, pos) == null

    private fun canReceive(level: ServerLevel, pos: BlockPos, state: BlockState, fluid: FlowingFluid): Boolean {
        if (isImpactWater(fluid) && !state.isAir && state.fluidState.isEmpty) return false
        return canReceive(state, fluid) || (fluid.isSame(state.fluidState.type)
            && state == state.fluidState.createLegacyBlock() && FractionalFluidSources.owns(level, pos))
    }

    private fun isImpactWater(fluid: FlowingFluid): Boolean =
        fluid.isSame(Fluids.WATER) || fluid.defaultFluidState().`is`(FluidTags.WATER)

    private fun reactToFluidImpact(level: ServerLevel, parcel: Parcel, fluid: FlowingFluid, from: BlockPos,
        pos: BlockPos, state: BlockState): Boolean {
        if (state.block is LiquidBlock) {
            val target = state.fluidState
            val waterHitsLava = isImpactWater(fluid) && (target.`is`(FluidTags.LAVA) || Fluids.LAVA.isSame(target.type))
            val lavaHitsWater = (fluid.isSame(Fluids.LAVA) || fluid.defaultFluidState().`is`(FluidTags.LAVA)) &&
                (target.`is`(FluidTags.WATER) || Fluids.WATER.isSame(target.type))
            if (waterHitsLava || lavaHitsWater) {
                val sources = FractionalFluidSources.forLevel(level)
                val fluidName = requireNotNull(FractionalFluidSources.fluidName(state))
                val key = FractionalFluidSources.Position(parcel.dimensionId, pos.x, pos.y, pos.z)
                if (sources?.transfers?.isLocked(key) == true) return false
                val exactUnits = sources?.amount(parcel.dimensionId, pos.x, pos.y, pos.z, fluidName)
                val units = exactUnits
                    ?: MIN_SETTLED_UNITS * target.amount.toBigDecimal()
                val downward = lavaHitsWater && from.y > pos.y
                if (waterHitsLava || downward) {
                    if (units < MIN_SETTLED_UNITS) return false
                    val product = when {
                        downward -> Blocks.STONE
                        exactUnits?.let { it >= FULL_SOURCE } ?: target.isSource -> Blocks.OBSIDIAN
                        else -> Blocks.COBBLESTONE
                    }
                    val debit = if (downward) minOf(parcel.sourceUnits, FULL_SOURCE) else BigDecimal.ZERO
                    if (!applyParcelImpact(level, parcel, pos, state, debit) {
                            level.setBlock(pos, product.defaultBlockState(), Block.UPDATE_ALL)
                        }) return false
                    sources?.reconcileFluid(parcel.dimensionId, pos.x, pos.y, pos.z, null)
                    level.levelEvent(1501, pos, 0)
                    return true
                }
                if (parcel.sourceUnits < MIN_SETTLED_UNITS || !level.hasChunkAt(from)) return false
                val previous = level.getBlockState(from)
                if (!previous.isAir || !applyParcelImpact(level, parcel, from, previous, minOf(parcel.sourceUnits, FULL_SOURCE)) {
                        level.setBlock(from, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL)
                    }) return false
                level.levelEvent(1501, from, 0)
                return true
            }
        }
        return reactToWaterImpact(level, parcel, fluid, pos, state, from.y < pos.y)
    }

    private fun reactToWaterImpact(level: ServerLevel, parcel: Parcel, fluid: FlowingFluid,
        pos: BlockPos, state: BlockState, fromBelow: Boolean = false): Boolean {
        if (!isImpactWater(fluid) || state.isAir) return false
        val shallow = parcel.sourceUnits < MIN_SETTLED_UNITS
        val available = parcel.sourceUnits
        val absorption = if (shallow) available else BigDecimal.ZERO
        return when {
            state.`is`(Blocks.SPONGE) -> {
                if (!applyParcelImpact(level, parcel, pos, state, minOf(available, SPONGE_CAPACITY)) {
                        level.setBlock(pos, Blocks.WET_SPONGE.defaultBlockState(), Block.UPDATE_ALL)
                    }) return false
                level.levelEvent(2001, pos, Block.getId(Blocks.WATER.defaultBlockState()))
                true
            }
            state.`is`(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) < 7 ->
                applyParcelImpact(level, parcel, pos, state,
                    if (state.getValue(FarmBlock.MOISTURE) == 0) absorption else BigDecimal.ZERO) {
                    level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 7), Block.UPDATE_CLIENTS)
                }
            RetainedWaterReactions.acceptsWater(state) -> {
                if (fromBelow && state.block is ConcretePowderBlock) return false
                applyParcelImpact(level, parcel, pos, state, absorption) {
                    RetainedWaterReactions.apply(level, pos, state)
                }
            }
            !shallow && RetainedFluidBlockInteractions.isWaterBreakable(state) -> level.destroyBlock(pos, true)
            else -> false
        }
    }

    private inline fun applyParcelImpact(level: ServerLevel, parcel: Parcel, pos: BlockPos, before: BlockState,
        absorbed: BigDecimal, apply: () -> Boolean): Boolean {
        if (absorbed.signum() == 0) return apply()
        val previous = parcel.sourceUnits
        parcel.sourceUnits -= absorbed
        var applied = false
        try {
            applied = apply()
            return applied
        } finally {
            if (!applied && level.getBlockState(pos) == before) parcel.sourceUnits = previous
        }
    }

    private fun depositedState(current: BlockState, fluid: FlowingFluid, units: BigDecimal,
        legacyRounding: Boolean = false): BlockState {
        if (current.fluidState.isSource) return current
        val level = if (legacyRounding) worldVolumeToVisualLevel(units.toDouble() / 255.0) else settledFluidLevel(units)
        return (if (units >= FULL_SOURCE) fluid.getSource(false)
            else fluid.getFlowing(level.coerceIn(1, 7), false)).createLegacyBlock()
    }

    private fun prepareSettlement(level: ServerLevel, parcel: Parcel, staged: MotionState, fluid: FlowingFluid, pos: BlockPos,
        current: BlockState) {
        if (pos.y >= level.maxBuildHeight) return
        val sources = FractionalFluidSources.forLevel(level)
        val existing = sourceAmount(level, parcel, pos) ?: if (fluid.isSame(current.fluidState.type))
            BigDecimal.valueOf(current.fluidState.amount.coerceIn(0, 8) / 8.0) * FULL_SOURCE else BigDecimal.ZERO
        if (existing >= FULL_SOURCE) {
            staged.settlementAnchor = pos
            staged.velocity.zero()
            return
        }
        val deposited = minOf(parcel.sourceUnits, FULL_SOURCE - existing)
        val total = existing + deposited
        val after = depositedState(current, fluid, total)
        preparePlacementStorage(staged, current, after)
        if (sources != null) {
            parcelStorage.reserve(QUANTITY_PLACEMENT_BYTES)
            staged.placementStorageBytes += QUANTITY_PLACEMENT_BYTES
        }
        staged.setPlacementPosition(pos)
        staged.placementBefore = current
        staged.placementAfter = after
        staged.placementDeposited = deposited
        staged.placementUnitsBefore = existing.takeIf { sources != null }
        staged.placementUnitsAfter = total.takeIf { sources != null }
    }

    /** Retain uncertain writes; expected before/after observations determine whether a debit is due. */
    private fun finishPlacement(level: ServerLevel, parcel: Parcel, fluid: FlowingFluid) {
        if (!parcel.hasPlacement) return
        val pos = parcel.placementPosition()
        if (!level.hasChunkAt(pos)) return
        val before = placementState(parcel.placementBefore, parcel.placementUnavailableBefore) ?: return
        val after = placementState(parcel.placementAfter, parcel.placementUnavailableAfter) ?: return
        if (!parcel.placementScheduledOnly) {
            val remaining = parcel.sourceUnits - requireNotNull(parcel.placementDeposited)
            if (remaining.signum() > 0) prepareSourceHistory(parcel, pos.asLong())
            // Intervening world callbacks may have changed terrain even on the first attempt.
            val current = level.getBlockState(pos)
            if (current != before && current != after) return
            val unitsBefore = parcel.placementUnitsBefore
            val change = if (unitsBefore != null) {
                val sources = FractionalFluidSources.forLevel(level) ?: return
                if (current != before && sourceAmount(level, parcel, pos) == null) return
                sources.prepareChange(parcel.dimensionId, pos.x, pos.y, pos.z, FractionalFluidSources.fluidName(fluid),
                    unitsBefore, requireNotNull(parcel.placementUnitsAfter),
                    if (after.block === Blocks.WATER) after.getValue(LiquidBlock.LEVEL) else null) ?: return
            } else null
            if (current != after) {
                if (current != before) return // Keep ambiguous ownership until a consistent observation exists.
                val applied = level.setBlock(pos, after, Block.UPDATE_ALL)
                if (!applied || change != null) {
                    val observed = level.getBlockState(pos)
                    if (observed != after) {
                        if (observed == before) {
                            change?.cancel()
                            parcel.clearPlacement(parcelStorage)
                        }
                        return
                    }
                }
            }
            if (change != null && !change.commit()) return
            // Capacity was admitted before the write; committing this debit cannot grow the history.
            if (remaining.signum() > 0) requireNotNull(parcel.settledSources).add(pos.asLong())
            parcel.sourceUnits = remaining
            parcel.placementScheduledOnly = true
            if (remaining.signum() > 0) {
                parcel.settlementAnchor = pos
                parcel.surfaceMotion = SurfaceMotion.NONE
                parcel.velocity.zero()
            }
        }
        if (parcel.placementUnitsAfter != null) {
            val state = level.getBlockState(pos)
            if (state == after) {
                val amount = sourceAmount(level, parcel, pos)?.toInt() ?: 255
                BlockStateInfo.onSourceAmountChanged(level, pos, state, amount)
            }
        }
        if (!after.fluidState.isSource || parcel.placementUnitsAfter != null) finishScheduling(level, parcel, fluid, pos)
        parcel.clearPlacement(parcelStorage)
    }

    private fun finishScheduling(level: ServerLevel, parcel: Parcel, fluid: FlowingFluid, pos: BlockPos) {
        // Only an interrupted scheduling callback has an unknown enqueue outcome.
        val alreadyScheduled = if (parcel.placementScheduleAttempted) {
            val ticks = level.getFluidTicks()
            ticks.hasScheduledTick(pos, fluid) || ticks.willTickThisTick(pos, fluid)
        } else false
        if (!alreadyScheduled) {
            parcel.placementScheduleAttempted = true
            level.scheduleTick(pos, fluid, fluid.getTickDelay(level))
        }
    }

    private fun prepareSourceHistory(parcel: Parcel, position: Long) {
        var history = parcel.settledSources
        if (history == null) {
            val bytes = historyBytes(HashCommon.arraySize(0, 0.75f))
            sourceStorage.reserve(bytes)
            try {
                history = SourceHistory(0, sourceStorage)
                parcel.settledSources = history
            } catch (error: Throwable) {
                sourceStorage.release(bytes)
                throw error
            }
        }
        history.prepareToAdd(position)
    }

    private fun inlineState(state: BlockState): Boolean {
        val block = state.block
        return block === Blocks.AIR || block === Blocks.CAVE_AIR || block === Blocks.VOID_AIR
            || block === Blocks.WATER || block === Blocks.LAVA
    }

    // Four copies cover retained data, one save output and decode/validation scratch. All sizes
    // use Long arithmetic; Tag.sizeInBytes() uses Int and can overflow on a large nested value.
    private fun placementStorageBytes(before: CompoundTag, after: CompoundTag): Long {
        var payload = 0L
        fun add(bytes: Long) {
            payload = Math.addExact(payload, bytes)
            val extra = maxOf(0L, Math.multiplyExact(payload, 4L) - INLINE_PLACEMENT_BYTES)
            if (!parcelStorage.fits(extra)) throw parcelStorage.failure(extra)
        }
        fun visit(tag: Tag, depth: Int) {
            require(depth <= Tag.MAX_DEPTH) { "Placement NBT exceeds the format depth limit" }
            when (tag) {
                is CompoundTag -> {
                    add(64)
                    for (key in tag.allKeys) {
                        add(96L + key.length.toLong() * 2)
                        visit(requireNotNull(tag.get(key)), depth + 1)
                    }
                }
                is ListTag -> {
                    add(64L + tag.size.toLong() * 16)
                    for (value in tag) visit(value, depth + 1)
                }
                is StringTag -> add(48L + tag.asString.length.toLong() * 2)
                is ByteArrayTag -> add(48L + tag.asByteArray.size.toLong())
                is IntArrayTag -> add(48L + tag.asIntArray.size.toLong() * 4)
                is LongArrayTag -> add(48L + tag.asLongArray.size.toLong() * 8)
                else -> {
                    require(tag.id in Tag.TAG_END..Tag.TAG_DOUBLE) { "Unknown placement NBT type" }
                    add(32)
                }
            }
        }
        // Root -> parcels list -> parcel -> placement -> before/after state.
        visit(before, 4); visit(after, 4)
        return maxOf(0L, payload * 4 - INLINE_PLACEMENT_BYTES)
    }

    private inner class PlacementEncoder(private val journal: PlacementState) {
        private var payload = 0L
        private fun admit(bytes: Long) {
            payload = Math.addExact(payload, bytes)
            val extra = maxOf(0L, Math.multiplyExact(payload, 4L) - INLINE_PLACEMENT_BYTES)
            val growth = extra - journal.placementStorageBytes
            if (growth > 0) {
                // Servicing refunds partially encoded scratch before checking progress. Report
                // the whole encoding requirement so that refund cannot hide an impossible retry.
                if (!parcelStorage.fits(growth)) throw parcelStorage.failure(extra)
                parcelStorage.reserve(growth)
                journal.placementStorageBytes = extra
            }
        }
        private fun entryBytes(keyLength: Int) = 96L + keyLength.toLong() * 2
        private fun stringEntry(target: CompoundTag, key: String, value: String) {
            admit(entryBytes(key.length) + 48L + value.length.toLong() * 2)
            target.putString(key, value)
        }
        private fun <T : Comparable<T>> property(target: CompoundTag, property: Property<T>, state: BlockState) {
            // The property's returned name is borrowed; admit our retained tag before creating it.
            stringEntry(target, property.name, property.getName(state.getValue(property)))
        }
        fun encode(state: BlockState): CompoundTag {
            admit(64)
            val result = CompoundTag()
            val name = BuiltInRegistries.BLOCK.getKey(state.block)
            admit(entryBytes(4) + 48L + (name.namespace.length.toLong() + name.path.length + 1) * 2)
            result.putString("Name", name.toString())
            if (state.properties.isNotEmpty()) {
                admit(entryBytes(10) + 64)
                val properties = CompoundTag()
                for (property in state.properties) property(properties, property, state)
                result.put("Properties", properties)
            }
            return result
        }
    }

    private fun preparePlacementStorage(journal: PlacementState, before: BlockState, after: BlockState) {
        if (inlineState(before) && inlineState(after)) return
        // Freeze custom state data once. Admission precedes every retained tag and the world write;
        // an exception refunds scratch in serviceParcels, or travels with the committed journal.
        val encoder = PlacementEncoder(journal)
        journal.placementUnavailableBefore = encoder.encode(before)
        journal.placementUnavailableAfter = encoder.encode(after)
    }

    private fun placementState(state: BlockState?, unavailable: CompoundTag?): BlockState? =
        state ?: resolveSavedState(requireNotNull(unavailable))

    private fun resolveSavedState(tag: CompoundTag): BlockState? {
        val encodedName = tag.getString("Name")
        val name = ResourceLocation.tryParse(encodedName) ?: return null
        if (!BuiltInRegistries.BLOCK.containsKey(name)) return null
        require(encodedName.length == name.namespace.length + name.path.length + 1
            && encodedName[name.namespace.length] == ':') { "Noncanonical placement block name" }
        var state = BuiltInRegistries.BLOCK.get(name).defaultBlockState()
        val properties = state.properties
        require(tag.size() == if (properties.isEmpty()) 1 else 2) { "Invalid placement block state" }
        if (properties.isEmpty()) return state
        require(tag.contains("Properties", Tag.TAG_COMPOUND.toInt())) { "Missing placement properties" }
        val savedProperties = tag.getCompound("Properties")
        require(savedProperties.size() == properties.size) { "Invalid placement properties" }
        for (property in properties) state = readSavedProperty(state, property, savedProperties)
        return state
    }

    private fun <T : Comparable<T>> readSavedProperty(state: BlockState, property: Property<T>, tag: CompoundTag): BlockState {
        require(tag.contains(property.name, Tag.TAG_STRING.toInt())) { "Missing placement property" }
        val encoded = tag.getString(property.name)
        val value = property.getValue(encoded).orElseThrow { IllegalArgumentException("Invalid placement property") }
        require(property.getName(value) == encoded) { "Noncanonical placement property" }
        return state.setValue(property, value)
    }

    // Enqueue normalizes nonfinite input; load rejects it. Private motion only adds finite gravity,
    // clamps finite components (including overflowed length) and writes finite collision velocities.
    private fun advanceFiniteVelocity(velocity: Vector3d): Vector3d {
        velocity.y += FLUID_PARCEL_GRAVITY * FLUID_PARCEL_TIME_STEP
        val speed = velocity.length()
        if (speed > MAX_FLUID_PARCEL_SPEED) velocity.mul(MAX_FLUID_PARCEL_SPEED / speed)
        return Vector3d(velocity).mul(FLUID_PARCEL_TIME_STEP)
    }

    private fun submitInterception(level: ServerLevel, parcel: Parcel, staged: MotionState,
        displacement: Vector3d, fluid: FlowingFluid): Boolean {
        val world = (level.server as? IShipObjectWorldServerProvider)?.shipObjectWorld ?: return false
        if (!world.supportsFluidParcelInterception) return false
        val end = Vector3d(staged.worldPosition).add(displacement)
        var preparationBytes = 0L
        try {
            val receivers = WorldFluidParcelReceivers.collect(world, parcel.dimensionId, staged.worldPosition, end,
                sweepReceiverMotion && parcel.lastUpdatedTick == currentTick - 1) {
                parcelStorage.reserve(it)
                preparationBytes += it
            } ?: return true
            if (receivers.isEmpty()) return false
            val fluidId = MassDatapackResolver.getLiquidStateId(fluid.getSource(false).createLegacyBlock()) ?: return true
            var amount = parcel.sourceUnits.min(BigDecimal.valueOf(Double.MAX_VALUE)).toDouble()
            if (BigDecimal.valueOf(amount) > parcel.sourceUnits) amount = Math.nextDown(amount)
            if (amount <= 0) return false
            val limit = parcelTerrainLimit(level, parcel, staged.worldPosition, displacement, fluid)
            if (limit == 0.0) return false
            val pending = ParcelInterception(UUID.randomUUID(), end, Vector3d(staged.velocity))
            val start = staged.worldPosition
            val request = VsiFluidParcelInterception(pending.id, parcel.dimensionId, fluidId, amount,
                start.x, start.y, start.z, end.x, end.y, end.z, limit, receivers,
                staged.velocity.x, staged.velocity.y, staged.velocity.z, parcel.momentum)
            if (world.submitFluidParcelInterception(request)) parcel.interception = pending
            return true
        } finally {
            parcelStorage.release(preparationBytes)
        }
    }

    private fun parcelTerrainLimit(level: ServerLevel, parcel: Parcel, start: Vector3d,
        displacement: Vector3d, fluid: FlowingFluid): Double {
        var cell = BlockPos.containing(start.x, start.y, start.z)
        fun blocked(position: BlockPos): Boolean {
            if (!level.hasChunkAt(position)) return true
            val state = level.getBlockState(position)
            return isOcean(level, parcel, position, state, fluid) || !canReceive(level, position, state, fluid) ||
                fluid.isSame(state.fluidState.type) && sourceAmount(level, parcel, position) != null
        }
        if (blocked(cell)) return 0.0
        repeat(32) {
            val x = if (displacement.x == 0.0) Double.POSITIVE_INFINITY else
                (cell.x.toDouble() + if (displacement.x > 0) 1.0 else 0.0).minus(start.x) / displacement.x
            val y = if (displacement.y == 0.0) Double.POSITIVE_INFINITY else
                (cell.y.toDouble() + if (displacement.y > 0) 1.0 else 0.0).minus(start.y) / displacement.y
            val z = if (displacement.z == 0.0) Double.POSITIVE_INFINITY else
                (cell.z.toDouble() + if (displacement.z > 0) 1.0 else 0.0).minus(start.z) / displacement.z
            val next = minOf(x, y, z)
            if (next > 1.0) return 1.0
            val axes = (if (abs(x - next) <= 1e-12) 1 else 0) or
                (if (abs(y - next) <= 1e-12) 2 else 0) or (if (abs(z - next) <= 1e-12) 4 else 0)
            val dx = displacement.x.compareTo(0.0)
            val dy = displacement.y.compareTo(0.0)
            val dz = displacement.z.compareTo(0.0)
            for (mask in 1..7) if (mask and axes == mask) {
                val touched = cell.offset(if (mask and 1 != 0) dx else 0,
                    if (mask and 2 != 0) dy else 0, if (mask and 4 != 0) dz else 0)
                if (blocked(touched)) return next.coerceIn(0.0, 1.0)
            }
            cell = cell.offset(if (axes and 1 != 0) dx else 0, if (axes and 2 != 0) dy else 0,
                if (axes and 4 != 0) dz else 0)
        }
        return 0.0
    }

    private fun advanceParcel(
        level: ServerLevel,
        parcel: Parcel,
        staged: MotionState,
        fluid: FlowingFluid,
        startBlockPos: BlockPos,
    ): ParcelMotionResult {
        val startX = staged.worldPosition.x; val startY = staged.worldPosition.y; val startZ = staged.worldPosition.z
        val velocityX = staged.velocity.x; val velocityY = staged.velocity.y; val velocityZ = staged.velocity.z
        val interception = parcel.interception?.takeIf { it.applied }
        val displacement = if (interception == null) advanceFiniteVelocity(staged.velocity) else {
            staged.velocity.set(interception.velocity)
            Vector3d(interception.end).sub(staged.worldPosition).mul(
                if (interception.collisionFraction < 1.0) maxOf(0.0, interception.collisionFraction - 1e-7)
                else 1.0)
        }
        if (interception == null && submitInterception(level, parcel, staged, displacement, fluid)) {
            staged.velocity.set(velocityX, velocityY, velocityZ)
            return ParcelMotionResult.RETRY_LATER
        }
        if (interception != null && interception.collisionFraction < 1.0) staged.velocity.zero()
        val endX = displacement.x + startX
        val endY = displacement.y + startY
        val endZ = displacement.z + startZ
        // A straight segment with both ends in one cell cannot enter another cell.
        // The existing sweep skips all terrain callbacks for samples in the starting cell.
        // Convert before adding one so full-width integer coordinates cannot overflow.
        if (endX >= startBlockPos.x.toDouble() && endX < startBlockPos.x.toDouble() + 1.0 &&
            endY >= startBlockPos.y.toDouble() && endY < startBlockPos.y.toDouble() + 1.0 &&
            endZ >= startBlockPos.z.toDouble() && endZ < startBlockPos.z.toDouble() + 1.0) {
            staged.worldPosition.set(endX, endY, endZ)
            return ParcelMotionResult.MOVED
        }
        val substeps = fluidParcelMotionSubsteps(displacement)
        var freeX = startX; var freeY = startY; var freeZ = startZ
        var lastBlockPos = startBlockPos

        for (step in 1..substeps) {
            val fraction = step.toDouble() / substeps.toDouble()
            val sampleX = displacement.x * fraction + startX
            val sampleY = displacement.y * fraction + startY
            val sampleZ = displacement.z * fraction + startZ
            val sampleBlockPos = BlockPos.containing(sampleX, sampleY, sampleZ)
            if (sampleBlockPos == lastBlockPos) {
                freeX = sampleX; freeY = sampleY; freeZ = sampleZ
                continue
            }
            if (!level.hasChunkAt(sampleBlockPos)) {
                staged.velocity.set(velocityX, velocityY, velocityZ)
                return ParcelMotionResult.RETRY_LATER
            }

            val sampleState = level.getBlockState(sampleBlockPos)
            if (reactToFluidImpact(level, parcel, fluid, lastBlockPos, sampleBlockPos, sampleState)) {
                if (parcel.sourceUnits.signum() == 0) return ParcelMotionResult.CONSUMED
                staged.worldPosition.set(freeX, freeY, freeZ)
                if (level.getBlockState(lastBlockPos).blocksMotion()) staged.settlementAnchor = lastBlockPos
                return ParcelMotionResult.BLOCKED
            }
            val sameFluid = fluid.isSame(sampleState.fluidState.type)
            if (isOcean(level, parcel, sampleBlockPos, sampleState, fluid)) {
                return ParcelMotionResult.CONSUMED
            }
            if (sameFluid && sourceAmount(level, parcel, sampleBlockPos) != null) {
                staged.worldPosition.set(freeX, freeY, freeZ)
                staged.velocity.zero()
                prepareSettlement(level, parcel, staged, fluid, sampleBlockPos, sampleState)
                return ParcelMotionResult.BLOCKED
            }
            val replaceable = canReceive(level, sampleBlockPos, sampleState, fluid)
            if (!replaceable) {
                staged.worldPosition.set(freeX, freeY, freeZ)
                val hitFloor = sampleBlockPos.y < lastBlockPos.y && sampleBlockPos.x == lastBlockPos.x &&
                    sampleBlockPos.z == lastBlockPos.z
                if (!hitFloor || !isImpactWater(fluid)) {
                    staged.velocity.x = 0.0
                    staged.velocity.z = 0.0
                }
                if (staged.velocity.y > 0.0) {
                    staged.velocity.y = 0.0
                }
                return ParcelMotionResult.BLOCKED
            }

            freeX = sampleX; freeY = sampleY; freeZ = sampleZ
            lastBlockPos = sampleBlockPos
        }

        staged.worldPosition.add(displacement)
        return ParcelMotionResult.MOVED
    }

    fun clearVisuals(levels: Map<String, ServerLevel>) {
        if (servicing) {
            clearVisualsRequested = true
            return
        }
        servicing = true
        try {
            clearVisualsInternal(levels)
        } finally {
            clearVisualsRequested = false
            servicing = false
        }
    }

    private fun clearVisualsInternal(levels: Map<String, ServerLevel>) {
        try {
            // Callbacks can enqueue new owners, so do not hold a deque iterator across them.
            repeat(parcels.size) {
                val parcel = parcels.first
                levels[parcel.dimensionId]?.let { restoreVisual(it, parcel) }
                parcels.addLast(parcels.removeFirst())
            }
            flushVisualChanges()
        } finally {
            discardVisualChanges()
        }
    }

    /** Called on the game thread beside the pipeline checkpoint; visuals never own server-side fluid. */
    // Each history's live reservation includes a same-sized output array. It is sufficient for
    // toLongArray while all live histories remain owned. Returned NBT belongs to the caller.
    fun save(): CompoundTag = CompoundTag().apply {
        putInt("version", when {
            parcels.any { it.surfaceMotion != SurfaceMotion.NONE } -> 9
            parcels.any { it.collectionAgeTicks >= 0 } -> 8
            parcels.any { it.momentum != null } -> 7
            else -> 6
        })
        put("parcels", ListTag().also { list ->
            parcels.forEach { parcel ->
                if (parcel.consumed || (parcel.sourceUnits.signum() == 0 && !parcel.hasPlacement && parcel.interception == null)) return@forEach
                list.add(CompoundTag().apply {
                    putString("dimension", parcel.dimensionId)
                    putString("fluid", parcel.fluidName.toString())
                    putDouble("x", parcel.worldPosition.x); putDouble("y", parcel.worldPosition.y)
                    putDouble("z", parcel.worldPosition.z)
                    putDouble("vx", parcel.velocity.x); putDouble("vy", parcel.velocity.y)
                    putDouble("vz", parcel.velocity.z)
                    parcel.momentum?.let { motion ->
                        put("momentum", CompoundTag().apply {
                            putDouble("density", motion.massDensity)
                            putDouble("lx", motion.angularMomentumPerMassX)
                            putDouble("ly", motion.angularMomentumPerMassY)
                            putDouble("lz", motion.angularMomentumPerMassZ)
                            putDouble("ixx", motion.inertiaPerMassXX); putDouble("iyy", motion.inertiaPerMassYY)
                            putDouble("izz", motion.inertiaPerMassZZ); putDouble("ixy", motion.inertiaPerMassXY)
                            putDouble("ixz", motion.inertiaPerMassXZ); putDouble("iyz", motion.inertiaPerMassYZ)
                        })
                    }
                    putVolume(this, "volume", parcel.sourceUnits)
                    putLong("lag", currentTick - parcel.lastUpdatedTick)
                    if (parcel.collectionAgeTicks >= 0) putLong("collection_age", parcel.collectionAgeTicks)
                    if (parcel.surfaceMotion != SurfaceMotion.NONE) putString("surface_motion", parcel.surfaceMotion.name)
                    parcel.interception?.let { pending ->
                        put("interception", CompoundTag().apply {
                            putUUID("id", pending.id)
                            putDouble("x", pending.end.x); putDouble("y", pending.end.y); putDouble("z", pending.end.z)
                            putDouble("vx", pending.velocity.x); putDouble("vy", pending.velocity.y); putDouble("vz", pending.velocity.z)
                            putBoolean("applied", pending.applied)
                            putDouble("collision", pending.collisionFraction)
                        })
                    }
                    if (parcel.hasPlacement) {
                        val pos = parcel.placementPosition()
                        put("placement", CompoundTag().apply {
                            putIntArray("pos", intArrayOf(pos.x, pos.y, pos.z))
                            put("before", parcel.placementUnavailableBefore?.copy() ?: NbtUtils.writeBlockState(requireNotNull(parcel.placementBefore)))
                            put("after", parcel.placementUnavailableAfter?.copy() ?: NbtUtils.writeBlockState(requireNotNull(parcel.placementAfter)))
                            putVolume(this, "deposited", requireNotNull(parcel.placementDeposited))
                            parcel.placementUnitsBefore?.let { putString("units_before", it.toString()) }
                            parcel.placementUnitsAfter?.let { putString("units_after", it.toString()) }
                            putBoolean("scheduled_only", parcel.placementScheduledOnly)
                            putBoolean("schedule_attempted", parcel.placementScheduleAttempted)
                        })
                    }
                    // Arrays.sort may allocate a second long[] when it merges runs. Keep this
                    // checkpoint within its single prepaid output array on every ordering.
                    putLongArray("settled_sources", parcel.settledSources?.toLongArray()?.apply { LongArrays.quickSort(this) }
                        ?: LongArrays.EMPTY_ARRAY)
                    parcel.settlementAnchor?.let { pos ->
                        putIntArray("settlement", intArrayOf(pos.x, pos.y, pos.z))
                    }
                })
            }
        })
    }

    /** Validate the entire checkpoint before changing ownership. Missing mod fluids remain paused and saveable. */
    fun load(tag: CompoundTag) {
        check(!servicing) { "Cannot replace parcel ownership during servicing" }
        val version = tag.getInt("version")
        require(version in 1..9) { "Unsupported world-fluid parcel checkpoint" }
        require(tag.contains("parcels", Tag.TAG_LIST.toInt())) { "Missing world-fluid parcels" }
        val list = tag.getList("parcels", Tag.TAG_COMPOUND.toInt())
        require((tag.get("parcels") as ListTag).size == list.size) { "Invalid parcel list" }
        require(list.size <= maxActiveParcels.coerceAtLeast(1)) { "World-fluid parcel capacity exceeded" }
        var restoreBytes = 0L
        var restoreMetadata = 0L
        val previousBytes = sourceStorage.usedBytes
        val previousMetadata = parcelStorage.usedBytes - MANAGER_STORAGE_BYTES - previousBytes
        var committed = false
        try {
            val fixedBytes = list.size.toLong() * PARCEL_STORAGE_BYTES
            parcelStorage.reserve(fixedBytes)
            restoreMetadata = fixedBytes
            val restored = ParcelQueue()
            var due = 0
            for (i in 0 until list.size) {
                val entry = list.getCompound(i)
                require(entry.contains("settled_sources", Tag.TAG_LONG_ARRAY.toInt()))
                val positions = entry.getLongArray("settled_sources")
                if (positions.isNotEmpty()) {
                    val bytes = historyBytes(HashCommon.arraySize(positions.size, 0.75f))
                    sourceStorage.reserve(bytes) // Old owners and the decoded prefix remain charged.
                    restoreBytes += bytes
                }
                require(entry.contains("dimension", Tag.TAG_STRING.toInt()) && entry.getString("dimension").isNotBlank())
                require(entry.contains("fluid", Tag.TAG_STRING.toInt()))
                val dimension = entry.getString("dimension")
                val encodedName = entry.getString("fluid")
                // Bare names acquire the minecraft namespace when decoded. Account before parsing.
                val nameBytes = nameStorageBytes(dimension.length.toLong(),
                    encodedName.length.toLong() + if (':' in encodedName) 0 else 10)
                parcelStorage.reserve(nameBytes)
                restoreMetadata += nameBytes
                val name = ResourceLocation.tryParse(encodedName)
                    ?: throw IllegalArgumentException("Invalid saved parcel fluid name")
                val x = savedDouble(entry, "x"); val y = savedDouble(entry, "y"); val z = savedDouble(entry, "z")
                val vx = savedDouble(entry, "vx"); val vy = savedDouble(entry, "vy"); val vz = savedDouble(entry, "vz")
                val volume = if (version == 1) {
                    require(entry.contains("fill", Tag.TAG_INT.toInt()) && entry.getInt("fill") > 0)
                    BigDecimal.valueOf(entry.getInt("fill") / 255.0) * FULL_SOURCE
                } else {
                    savedVolume(entry, "volume", version)
                }
                require(volume.signum() >= 0 && volume <= MAX_PARCEL_UNITS) { "Invalid parcel volume" }
                val placement = if (version >= 3 && entry.contains("placement")) {
                    require(entry.contains("placement", Tag.TAG_COMPOUND.toInt()))
                    val intent = entry.getCompound("placement")
                    require(intent.contains("pos", Tag.TAG_INT_ARRAY.toInt()))
                    val pos = intent.getIntArray("pos"); require(pos.size == 3)
                    require(intent.contains("before", Tag.TAG_COMPOUND.toInt()) && intent.contains("after", Tag.TAG_COMPOUND.toInt()))
                    val before = intent.getCompound("before"); val after = intent.getCompound("after")
                    val placementBytes = placementStorageBytes(before, after) +
                        if (version >= 4 && (intent.contains("units_before") || intent.contains("units_after"))) QUANTITY_PLACEMENT_BYTES else 0L
                    parcelStorage.reserve(placementBytes)
                    restoreMetadata += placementBytes
                    for (state in listOf(before, after)) {
                        require(state.contains("Name", Tag.TAG_STRING.toInt()))
                        require(ResourceLocation.tryParse(state.getString("Name")) != null)
                    }
                    val beforeState = resolveSavedState(before)
                    val afterState = resolveSavedState(after)
                    val deposited = savedVolume(intent, "deposited", version)
                    require(deposited.signum() > 0 && deposited <= FULL_SOURCE)
                    val unitsBefore = if (version >= 4 && intent.contains("units_before")) savedDecimal(intent, "units_before") else null
                    val unitsAfter = if (version >= 4 && intent.contains("units_after")) savedDecimal(intent, "units_after") else null
                    require((unitsBefore == null) == (unitsAfter == null))
                    if (unitsBefore != null) {
                        require(unitsBefore.signum() >= 0 && unitsAfter!! <= FULL_SOURCE)
                        require((unitsBefore + deposited).compareTo(unitsAfter) == 0)
                    }
                    require(intent.contains("scheduled_only", Tag.TAG_BYTE.toInt()))
                    val scheduling = intent.getBoolean("scheduled_only")
                    // Earlier version3 receipts did not distinguish an unattempted scheduling phase.
                    val scheduleAttempted = if (intent.contains("schedule_attempted")) {
                        require(intent.contains("schedule_attempted", Tag.TAG_BYTE.toInt()))
                        intent.getBoolean("schedule_attempted")
                    } else scheduling
                    require(!scheduleAttempted || scheduling)
                    require(scheduling || deposited <= volume)
                    val knownFluid = if (BuiltInRegistries.FLUID.containsKey(name))
                        BuiltInRegistries.FLUID.get(name) as? FlowingFluid else null
                    if (knownFluid != null) {
                        afterState?.let { placed ->
                            require(knownFluid.isSame(placed.fluidState.type)) { "Placement fluid does not match owner" }
                            require(placed == placed.fluidState.createLegacyBlock()
                                || (unitsBefore != null && placed == beforeState && placed.fluidState.isSource)) { "Invalid parcel placement block" }
                            beforeState?.let { previous ->
                                require(canReceive(previous, knownFluid) || (unitsBefore != null
                                    && knownFluid.isSame(previous.fluidState.type))) { "Invalid parcel placement origin" }
                                val existing = unitsBefore ?: if (knownFluid.isSame(previous.fluidState.type))
                                    BigDecimal.valueOf(previous.fluidState.amount.coerceIn(0, 8) / 8.0) * FULL_SOURCE else BigDecimal.ZERO
                                val total = existing + deposited
                                require(total <= FULL_SOURCE)
                                require(placed == depositedState(previous, knownFluid, total)
                                    || placed == depositedState(previous, knownFluid, total, true)) { "Placement state and debit disagree" }
                            }
                        }
                    }
                    PendingPlacement(BlockPos(pos[0], pos[1], pos[2]), beforeState, afterState, deposited, scheduling, scheduleAttempted,
                        if (beforeState == null || !inlineState(beforeState)) before.copy() else null,
                        if (afterState == null || !inlineState(afterState)) after.copy() else null, placementBytes, unitsBefore, unitsAfter)
                } else null
                val interception = if (entry.contains("interception")) {
                    require(version >= 6 && placement == null && entry.contains("interception", Tag.TAG_COMPOUND.toInt()))
                    val pending = entry.getCompound("interception")
                    require(pending.hasUUID("id") && pending.contains("applied", Tag.TAG_BYTE.toInt()))
                    for (key in listOf("x", "y", "z", "vx", "vy", "vz", "collision"))
                        require(pending.contains(key, Tag.TAG_DOUBLE.toInt()) && pending.getDouble(key).isFinite())
                    val collision = pending.getDouble("collision")
                    require(collision in 0.0..1.0)
                    ParcelInterception(pending.getUUID("id"),
                        Vector3d(pending.getDouble("x"), pending.getDouble("y"), pending.getDouble("z")),
                        Vector3d(pending.getDouble("vx"), pending.getDouble("vy"), pending.getDouble("vz")),
                        pending.getBoolean("applied"), collision)
                } else null
                require(volume.signum() > 0 || placement?.scheduledOnly == true || interception?.applied == true) {
                    "Empty parcel without a pending receipt"
                }
                require(entry.contains("lag", Tag.TAG_LONG.toInt()))
                val lag = entry.getLong("lag")
                val collectionAge = if (entry.contains("collection_age")) {
                    require(version >= 8 && entry.contains("collection_age", Tag.TAG_LONG.toInt()))
                    entry.getLong("collection_age").also { require(it in 0..1200) }
                } else -1L
                val surfaceMotion = if (entry.contains("surface_motion")) {
                    require(version >= 9 && entry.contains("surface_motion", Tag.TAG_STRING.toInt()))
                    SurfaceMotion.valueOf(entry.getString("surface_motion"))
                } else SurfaceMotion.NONE
                // A long pressure wait can retain more catch-up than one scheduling rotation.
                // Leave arithmetic headroom; processing is bounded independently of this debt.
                require(lag in 0..Long.MAX_VALUE / 2) { "Invalid parcel scheduling lag" }
                val settledSources = if (positions.isEmpty()) null else SourceHistory(positions.size, sourceStorage).also {
                    history -> positions.forEach { history.add(it) }
                }
                val anchor = if (entry.contains("settlement")) {
                    require(entry.contains("settlement", Tag.TAG_INT_ARRAY.toInt()))
                    val coordinates = entry.getIntArray("settlement")
                    require(coordinates.size == 3)
                    BlockPos(coordinates[0], coordinates[1], coordinates[2])
                } else null
                val fluid = if (BuiltInRegistries.FLUID.containsKey(name))
                    BuiltInRegistries.FLUID.get(name) as? FlowingFluid else null
                if (lag > 0) due++
                val momentum = if (entry.contains("momentum")) {
                    require(version >= 7 && entry.contains("momentum", Tag.TAG_COMPOUND.toInt()))
                    val motion = entry.getCompound("momentum")
                    VsiFluidParcelMomentum(savedDouble(motion, "density"),
                        savedDouble(motion, "lx"), savedDouble(motion, "ly"), savedDouble(motion, "lz"),
                        savedDouble(motion, "ixx"), savedDouble(motion, "iyy"), savedDouble(motion, "izz"),
                        savedDouble(motion, "ixy"), savedDouble(motion, "ixz"), savedDouble(motion, "iyz"))
                } else null
                restored.addLast(Parcel(dimension, Vector3d(x, y, z),
                    Vector3d(vx, vy, vz), name, fluid, volume,
                    -lag, settledSources, anchor,
                    metadataBytes = PARCEL_STORAGE_BYTES + nameBytes, momentum = momentum).also { owner ->
                    owner.interception = interception
                    owner.collectionAgeTicks = collectionAge
                    owner.surfaceMotion = surfaceMotion
                    placement?.let {
                        owner.setPlacementPosition(it.pos); owner.placementBefore = it.before; owner.placementAfter = it.after
                        owner.placementDeposited = it.deposited; owner.placementScheduledOnly = it.scheduledOnly
                        owner.placementUnitsBefore = it.unitsBefore; owner.placementUnitsAfter = it.unitsAfter
                        owner.placementScheduleAttempted = it.scheduleAttempted
                        owner.placementUnavailableBefore = it.unavailableBefore; owner.placementUnavailableAfter = it.unavailableAfter
                        owner.placementStorageBytes = it.storageBytes
                    }
                })
            }
            // All allocation and validation precedes this reference/scalar publication.
            parcels = restored
            mergeBuckets.fill(null)
            parcels.forEach { indexParcel(it) }
            currentTick = 0
            parcelsWithDueMotion = due
            pressureClockNanos = null
            pressureAdvancedTime = false
            clearSourceCapacityFailure()
            sourceStorage.release(previousBytes)
            parcelStorage.release(previousMetadata)
            committed = true
        } finally {
            if (!committed) {
                sourceStorage.release(restoreBytes)
                parcelStorage.release(restoreMetadata)
            }
        }
    }

    private fun savedDouble(entry: CompoundTag, key: String): Double {
        require(entry.contains(key, Tag.TAG_DOUBLE.toInt())) { "Missing parcel $key" }
        return entry.getDouble(key).also { require(it.isFinite()) { "Invalid parcel $key" } }
    }

    private fun savedDecimal(entry: CompoundTag, key: String): BigDecimal {
        require(entry.contains(key, Tag.TAG_STRING.toInt())) { "Missing parcel $key" }
        val encoded = entry.getString(key)
        require(encoded.length in 1..MAX_SAVED_DECIMAL_LENGTH) { "Oversized parcel $key" }
        val value = requireNotNull(encoded.toBigDecimalOrNull()) { "Invalid parcel $key" }
        // A compact exponent can otherwise pass the finite-double check by underflowing to zero,
        // then require enormous scale alignment when a later placement adds or subtracts a debit.
        require(value.scale() in MIN_SAVED_DECIMAL_SCALE..MAX_SAVED_DECIMAL_SCALE) { "Invalid parcel $key scale" }
        return value
    }

    private fun putVolume(tag: CompoundTag, key: String, units: BigDecimal) {
        val volume = try { units.divide(FULL_SOURCE) } catch (_: ArithmeticException) { null }
        if (volume == null) tag.putString(key + "_units", units.toString()) else tag.putString(key, volume.toString())
    }

    private fun savedVolume(tag: CompoundTag, key: String, version: Int): BigDecimal {
        if (version >= 4 && tag.contains(key + "_units")) {
            require(!tag.contains(key)) { "Duplicate parcel $key representation" }
            return savedDecimal(tag, key + "_units")
        }
        return savedDecimal(tag, key) * FULL_SOURCE
    }

    internal fun parcelStorageBytes(): Long = parcelStorage.usedBytes
    internal fun peakParcelStorageBytes(): Long = parcelStorage.peakBytes

    internal fun activeParcelCount(): Int = parcels.size
    internal fun sourceStorageBytes(): Long = sourceStorage.usedBytes
    internal fun peakSourceStorageBytes(): Long = sourceStorage.peakBytes
    internal fun allocatedSourceHistoryBytes(): Long = parcels.sumOf { it.settledSources?.arrayBytes ?: 0L }

    private fun showVisual(level: ServerLevel, parcel: Parcel, fluid: FlowingFluid) {
        val players = level.players()
        val pos = parcel.blockPosition()
        if (players.isEmpty() || !level.hasChunkAt(pos)) {
            restoreVisual(level, parcel)
            return
        }
        // Pay for the recipient snapshot, UUID set/table and retry scratch before constructing
        // them or sending a packet. New overlays are optional; existing receipts are never dropped.
        val bytes = (players.size.toLong() + parcel.visualRecipients.size + 1) * VISUAL_RECIPIENT_BYTES
        if (!parcelStorage.fits(bytes)) flushVisualChanges()
        if (!parcelStorage.fits(bytes)) return
        parcelStorage.reserve(bytes)
        var retained = false
        try {
            val recipients = players.filter {
                it.distanceToSqr(parcel.worldPosition.x, parcel.worldPosition.y, parcel.worldPosition.z) <= VISUAL_RANGE_SQUARED
            }
            val state = FluidParcelState(BuiltInRegistries.FLUID.getKey(fluid.source).toString(),
                parcel.worldPosition.x, parcel.worldPosition.y, parcel.worldPosition.z,
                parcel.velocity.x, parcel.velocity.y, parcel.velocity.z,
                parcel.sourceUnits.divide(FULL_SOURCE, MathContext.DECIMAL128).toDouble())
            val packet = PacketFluidParcel(parcel.visualId, level.dimension().location().toString(), ++parcel.visualRevision, state)
            val recipientIds = HashSet<UUID>(recipients.size)
            recipients.forEach { recipientIds.add(it.uuid) }
            val previousRecipients = parcel.visualRecipients
            parcel.visualRecipients = previousRecipients + recipientIds
            parcelStorage.release(parcel.visualStorageBytes)
            parcel.visualStorageBytes = bytes
            retained = true // The full receipt survives partial send/restore exceptions.
            queueVisualChange(VisualChange(parcel, packet, recipients,
                players.filter { it.uuid in previousRecipients && it.uuid !in recipientIds }, recipientIds))
        } finally {
            if (!retained) parcelStorage.release(bytes)
        }
    }

    private fun restoreVisual(level: ServerLevel, parcel: Parcel) {
        if (parcel.visualRecipients.isEmpty()) return
        val packet = PacketFluidParcel(parcel.visualId, level.dimension().location().toString(), ++parcel.visualRevision, null)
        queueVisualChange(VisualChange(parcel, packet,
            level.players().filter { it.uuid in parcel.visualRecipients }, emptyList(), emptySet()))
    }

    private fun queueVisualChange(change: VisualChange) {
        val viewers = change.viewers.size.toLong() + change.leaving.size
        if (viewers == 0L) {
            finishVisualChange(change)
            return
        }
        val bytes = (viewers + 1) * VISUAL_RECIPIENT_BYTES
        if (visualBatchBytes + bytes > MAX_VISUAL_BATCH_BYTES || !parcelStorage.fits(bytes)) flushVisualChanges()
        if (bytes > MAX_VISUAL_BATCH_BYTES || !parcelStorage.fits(bytes)) {
            val removal = change.packet.copy(state = null)
            change.leaving.forEach { vsCore.simplePacketNetworking.sendToClient(removal, it.playerWrapper) }
            change.viewers.forEach { vsCore.simplePacketNetworking.sendToClient(change.packet, it.playerWrapper) }
            finishVisualChange(change)
            return
        }
        visualChanges.add(change)
        parcelStorage.reserve(bytes)
        visualBatchBytes += bytes
    }

    private fun flushVisualChanges() {
        if (visualChanges.isEmpty()) return
        try {
            val outgoing = LinkedHashMap<VisualRecipient, MutableList<PacketFluidParcel>>()
            for (change in visualChanges) {
                val packet = change.packet
                if (change.leaving.isNotEmpty()) {
                    val removal = packet.copy(state = null)
                    change.leaving.forEach {
                        outgoing.getOrPut(VisualRecipient(it, packet.dimension), ::ArrayList).add(removal)
                    }
                }
                change.viewers.forEach {
                    outgoing.getOrPut(VisualRecipient(it, packet.dimension), ::ArrayList).add(packet)
                }
            }
            for ((recipient, packets) in outgoing) {
                val player = recipient.player.playerWrapper
                for (start in packets.indices step PacketFluidParcelBatch.MAX_UPDATES) {
                    val end = minOf(start + PacketFluidParcelBatch.MAX_UPDATES, packets.size)
                    val packet = if (end - start == 1) packets[start] else PacketFluidParcelBatch(
                        recipient.dimension, packets.subList(start, end).map { FluidParcelUpdate(it.id, it.revision, it.state) })
                    vsCore.simplePacketNetworking.sendToClient(packet, player)
                }
            }
            visualChanges.forEach(::finishVisualChange)
        } finally {
            discardVisualChanges()
        }
    }

    private fun finishVisualChange(change: VisualChange) {
        val parcel = change.parcel
        if (parcel.visualRevision != change.packet.revision) return
        parcel.visualRecipients = change.recipients
        if (change.recipients.isEmpty()) {
            parcelStorage.release(parcel.visualStorageBytes)
            parcel.visualStorageBytes = 0
        }
    }

    private fun discardVisualChanges() {
        if (visualBatchBytes == 0L) return
        visualChanges = ArrayList()
        parcelStorage.release(visualBatchBytes)
        visualBatchBytes = 0
    }

    private companion object {
        private val MERGE_CELL_OFFSETS = intArrayOf(0, -1, 1)
        val MAX_PARCEL_UNITS = BigDecimal.valueOf(Double.MAX_VALUE) * FULL_SOURCE
        const val QUANTITY_PLACEMENT_BYTES = 16L * 1024
        const val DEFAULT_PARCEL_STORAGE_BYTES = 256L * 1024 * 1024
        // Fixed envelopes include bounded decimals, owner/journal/vector/link objects, checkpoint
        // tags/list slots, and one save output. Manager scratch includes depth-limited NBT traversal.
        const val MANAGER_STORAGE_BYTES = 64L * 1024
        const val PARCEL_STORAGE_BYTES = 16L * 1024
        const val INLINE_PLACEMENT_BYTES = 4096L
        const val INLINE_NAME_CHARS = 256L
        const val VISUAL_RECIPIENT_BYTES = 256L
        const val MAX_VISUAL_BATCH_BYTES = 1024L * 1024
        fun nameStorageBytes(dimensionChars: Long, fluidChars: Long): Long =
            maxOf(0L, dimensionChars + fluidChars - INLINE_NAME_CHARS) * 8
        const val DEFAULT_SOURCE_STORAGE_BYTES = 64L * 1024 * 1024
        // Exact primitive-array payload, plus one prepaid array of the same length for save().
        fun historyBytes(capacity: Int): Long = (capacity.toLong() + 1) * java.lang.Long.BYTES * 2
        // Finite Double inputs use at most scale 325 (4.9E-324); deposits in eighths add at most
        // scale 3. Exact subtraction retains that scale. Even 309 integer + 325 fractional digits
        // fit in 635 characters, so these bounds preserve long exact remainders without rounding.
        const val MIN_SAVED_DECIMAL_SCALE = -308
        const val MAX_SAVED_DECIMAL_SCALE = 325
        const val MAX_SAVED_DECIMAL_LENGTH = 640
        const val DEFAULT_MAX_ACTIVE_PARCELS = 4096
        const val DEFAULT_MAX_PARCELS_PER_TICK = 256
        const val MAX_PARCEL_MOTION_STEPS_PER_PASS = 4096L
        const val FLUID_PARCEL_TICK_NS = 50_000_000L
        const val VISUAL_RANGE_SQUARED = 128.0 * 128.0
        val RUNOFF_DIRECTIONS = arrayOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)
        val SETTLEMENT_DIRECTIONS = listOf(Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, Direction.UP)
    }
}

private const val FLUID_PARCEL_TIME_STEP = 1.0 / 20.0
private const val FLUID_PARCEL_GRAVITY = -9.81
private const val MAX_FLUID_PARCEL_SPEED = 64.0
private const val MAX_FLUID_PARCEL_SUBSTEP_DISTANCE = 0.45
private const val MAX_FLUID_PARCEL_SUBSTEPS = 32
private const val SURFACE_HEIGHT = 0.05
private const val RUNOFF_SPEED = 6.0
private const val MIN_HORIZONTAL_SPLASH_SPEED = 1.5
private const val MIN_VERTICAL_SPLASH_SPEED = 3.0
private const val MIN_SPLIT_IMPACT_SPEED = 10.0
private const val MAX_SPLASH_SPEED = 7.0
private const val MIN_SURFACE_SPEED = 0.45
private const val SPLASH_DAMPING = 0.76
