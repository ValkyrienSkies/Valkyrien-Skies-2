package org.valkyrienskies.mod.common.fluid

import it.unimi.dsi.fastutil.HashCommon
import it.unimi.dsi.fastutil.longs.LongArrays
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.LongArrayTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FlowingFluid
import org.joml.Vector3d
import org.valkyrienskies.core.internal.physics.VsiFluidOutflow
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources.Companion.FULL_SOURCE
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
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

internal fun worldVolumeToVanillaLevel(worldVolume: Double): Int =
    ceil(worldVolume.coerceIn(0.0, 1.0) * 8).toInt().coerceIn(1, 8)

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
        var visualPos: BlockPos? = null,
        var visualRecipients: Set<UUID> = emptySet(),
        val metadataBytes: Long,
    ) : PlacementState() {
        var queueNext: Parcel? = null
        var visualStorageBytes = 0L
        fun blockPosition(): BlockPos =
            BlockPos.containing(worldPosition.x, worldPosition.y, worldPosition.z)
    }

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
        fun blockPosition(): BlockPos = BlockPos.containing(worldPosition.x, worldPosition.y, worldPosition.z)
    }

    private val motion = MotionState()
    private var servicing = false
    private var clearVisualsRequested = false

    private enum class ParcelMotionResult {
        MOVED,
        BLOCKED,
        MERGED,
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
    private var currentTick = 0L
    private var pressureClockNanos: Long? = null
    private var pressureAdvancedTime = false
    private var parcelsWithDueMotion = 0

    private enum class ParcelUpdateResult { MOVED, PAUSED, CONSUMED }

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
            BigDecimal.valueOf(outflow.worldVolume) * FULL_SOURCE)
    }

    internal fun enqueueExact(dimension: String, fluidName: String, position: Vector3d, velocity: Vector3d,
        units: BigDecimal): VsiFluidOutflowResult {
        require(units.signum() > 0 && units <= MAX_PARCEL_UNITS && units.scale() in -308..325 && units.precision() <= 640)
        require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite())
        val name = ResourceLocation(fluidName)
        val fluid = if (BuiltInRegistries.FLUID.containsKey(name)) BuiltInRegistries.FLUID.get(name) as? FlowingFluid else null
        return if (fluid == null) VsiFluidOutflowResult.UNSUPPORTED_FLUID
            else enqueueParcel(dimension, fluid, position, velocity, units)
    }

    private fun enqueueParcel(dimension: String, fluid: FlowingFluid, position: Vector3d, velocity: Vector3d,
        units: BigDecimal): VsiFluidOutflowResult {
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
            parcels.addLast(Parcel(
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
            ))
        } catch (error: Throwable) {
            parcelStorage.release(bytes)
            throw error
        }
        return VsiFluidOutflowResult.APPLIED
    }

    fun tick(levels: Map<String, ServerLevel>) {
        if (servicing) return
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
        try {
            var motionStepsLeft = MAX_PARCEL_MOTION_STEPS_PER_PASS
            var parcelsLeft = minOf(parcels.size, parcelLimit)
            while (parcelsLeft-- > 0 && motionStepsLeft > 0) {
                // The selected owner stays capacity-counted and saveable during every callback.
                val parcel = parcels.first
                val wasDue = parcel.lastUpdatedTick < currentTick
                var consumed = false
                try {
                    val elapsed = minOf(currentTick - parcel.lastUpdatedTick, motionStepsLeft)
                    if (elapsed <= 0 && !parcel.hasPlacement) {
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
                    restoreVisual(level, parcel)
                    val previousVolume = parcel.sourceUnits
                    val result = updateParcel(level, parcel, fluid, elapsed)
                    if (result == ParcelUpdateResult.CONSUMED) {
                        parcels.removeFirst()
                        parcel.settledSources?.let { sourceStorage.release(it.reservedBytes) }
                        parcelStorage.release(parcel.metadataBytes)
                        consumed = true
                    } else {
                        if (result == ParcelUpdateResult.PAUSED) parcel.lastUpdatedTick = currentTick
                        if (parcel.sourceUnits.signum() > 0) showVisual(level, parcel, fluid)
                        parcels.addLast(parcels.removeFirst())
                    }
                    if (result == ParcelUpdateResult.PAUSED && parcel.sourceUnits === previousVolume
                        && parcelStorage.usedBytes >= previousStorage) {
                        // Compare after visual restoration AND redisplay. Temporary credit refunds
                        // and an updated clock do not make a blocked water owner able to drain.
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
                    if (wasDue && (consumed || parcel.lastUpdatedTick == currentTick)) parcelsWithDueMotion--
                }
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

    /** Pure motion is staged; callbacks see the last committed tick plus any placement intent. */
    private fun updateParcel(level: ServerLevel, parcel: Parcel, fluid: FlowingFluid, elapsed: Long): ParcelUpdateResult {
        if (parcel.hasPlacement) {
            finishPlacement(level, parcel, fluid)
            return if (parcel.sourceUnits.signum() == 0 && !parcel.hasPlacement)
                ParcelUpdateResult.CONSUMED else ParcelUpdateResult.PAUSED
        }
        val staged = motion
        staged.worldPosition.set(parcel.worldPosition)
        staged.velocity.set(parcel.velocity)
        staged.settlementAnchor = parcel.settlementAnchor
        staged.clearPlacement(parcelStorage)
        for (tick in 0 until elapsed) {
            val result = updateOneTick(level, parcel, staged, fluid)
            parcel.worldPosition.set(staged.worldPosition)
            parcel.velocity.set(staged.velocity)
            parcel.settlementAnchor = staged.settlementAnchor
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
        when (advanceParcel(level, parcel, staged, fluid, start)) {
            ParcelMotionResult.MERGED -> return ParcelUpdateResult.CONSUMED
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
        val currentState = level.getBlockState(currentPos)
        val belowState = level.getBlockState(belowPos)
        if (sourceAmount(level, parcel, belowPos) != null && fluid.isSame(belowState.fluidState.type)) {
            prepareSettlement(level, parcel, staged, fluid, belowPos, belowState)
            return ParcelUpdateResult.PAUSED
        }
        when (
            chooseFluidParcelAction(
                currentReplaceable = canReceive(level, currentPos, currentState, fluid),
                currentIsSameSource = isOcean(level, parcel, currentPos, currentState, fluid),
                belowReplaceable =
                    belowState.fluidState.isEmpty && belowState.canBeReplaced(),
                belowIsSameSource = isOcean(level, parcel, belowPos, belowState, fluid),
            )
        ) {
            FluidParcelAction.FALL -> Unit
            FluidParcelAction.MERGE -> return ParcelUpdateResult.CONSUMED
            FluidParcelAction.WAIT -> {
                // An outlet edited closed must not destroy already accepted ownership.
                if (parcel.settledSources?.isEmpty() == false) staged.settlementAnchor = currentPos
                return ParcelUpdateResult.PAUSED
            }
            FluidParcelAction.SETTLE -> {
                if (staged.velocity.y <= 0.0) {
                    prepareSettlement(level, parcel, staged, fluid, currentPos, currentState)
                    return ParcelUpdateResult.PAUSED
                }
            }
        }
        return ParcelUpdateResult.MOVED
    }

    private fun canReceive(state: BlockState, fluid: FlowingFluid): Boolean =
        if (fluid.isSame(state.fluidState.type)) {
            // Do not replace a waterlogged solid or a source that already owns its volume.
            !state.fluidState.isSource && state == state.fluidState.createLegacyBlock()
        } else state.fluidState.isEmpty && state.canBeReplaced()

    private fun sourceAmount(level: ServerLevel, parcel: Parcel, pos: BlockPos): BigDecimal? =
        FractionalFluidSources.forLevel(level)?.amount(parcel.dimensionId, pos.x, pos.y, pos.z, parcel.fluidName.toString())

    private fun isOcean(level: ServerLevel, parcel: Parcel, pos: BlockPos, state: BlockState, fluid: FlowingFluid): Boolean =
        fluid.isSame(state.fluidState.type) && state.fluidState.isSource
            && parcel.settledSources?.contains(pos.asLong()) != true && sourceAmount(level, parcel, pos) == null

    private fun canReceive(level: ServerLevel, pos: BlockPos, state: BlockState, fluid: FlowingFluid): Boolean =
        canReceive(state, fluid) || (fluid.isSame(state.fluidState.type)
            && state == state.fluidState.createLegacyBlock() && FractionalFluidSources.owns(level, pos))

    private fun depositedState(current: BlockState, fluid: FlowingFluid, units: BigDecimal): BlockState {
        if (current.fluidState.isSource) return current
        return (if (units >= FULL_SOURCE) fluid.getSource(false)
            else fluid.getFlowing(worldVolumeToVanillaLevel(units.toDouble() / 255.0).coerceAtMost(7), false)).createLegacyBlock()
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
                sources.prepareChange(parcel.dimensionId, pos.x, pos.y, pos.z, parcel.fluidName.toString(),
                    unitsBefore, requireNotNull(parcel.placementUnitsAfter)) ?: return
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

    private fun advanceParcel(
        level: ServerLevel,
        parcel: Parcel,
        staged: MotionState,
        fluid: FlowingFluid,
        startBlockPos: BlockPos,
    ): ParcelMotionResult {
        val startX = staged.worldPosition.x; val startY = staged.worldPosition.y; val startZ = staged.worldPosition.z
        val velocityX = staged.velocity.x; val velocityY = staged.velocity.y; val velocityZ = staged.velocity.z
        val displacement = advanceFiniteVelocity(staged.velocity)
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
            val sameFluid = fluid.isSame(sampleState.fluidState.type)
            if (isOcean(level, parcel, sampleBlockPos, sampleState, fluid)) {
                return ParcelMotionResult.MERGED
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
                staged.velocity.x = 0.0
                staged.velocity.z = 0.0
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
        // Callbacks can enqueue new owners, so do not hold a deque iterator across them.
        repeat(parcels.size) {
            val parcel = parcels.first
            levels[parcel.dimensionId]?.let { restoreVisual(it, parcel) }
            parcels.addLast(parcels.removeFirst())
        }
    }

    /** Called on the game thread beside the pipeline checkpoint; visuals never own server-side fluid. */
    // Each history's live reservation includes a same-sized output array. It is sufficient for
    // toLongArray while all live histories remain owned. Returned NBT belongs to the caller.
    fun save(): CompoundTag = CompoundTag().apply {
        putInt("version", 4)
        put("parcels", ListTag().also { list ->
            parcels.forEach { parcel ->
                list.add(CompoundTag().apply {
                    putString("dimension", parcel.dimensionId)
                    putString("fluid", parcel.fluidName.toString())
                    putDouble("x", parcel.worldPosition.x); putDouble("y", parcel.worldPosition.y)
                    putDouble("z", parcel.worldPosition.z)
                    putDouble("vx", parcel.velocity.x); putDouble("vy", parcel.velocity.y)
                    putDouble("vz", parcel.velocity.z)
                    putVolume(this, "volume", parcel.sourceUnits)
                    putLong("lag", currentTick - parcel.lastUpdatedTick)
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
        require(version in 1..4) { "Unsupported world-fluid parcel checkpoint" }
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
                val x = savedCoordinate(entry, "x"); val y = savedCoordinate(entry, "y"); val z = savedCoordinate(entry, "z")
                val vx = savedCoordinate(entry, "vx"); val vy = savedCoordinate(entry, "vy"); val vz = savedCoordinate(entry, "vz")
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
                                require(placed == depositedState(previous, knownFluid, total)) { "Placement state and debit disagree" }
                            }
                        }
                    }
                    PendingPlacement(BlockPos(pos[0], pos[1], pos[2]), beforeState, afterState, deposited, scheduling, scheduleAttempted,
                        if (beforeState == null || !inlineState(beforeState)) before.copy() else null,
                        if (afterState == null || !inlineState(afterState)) after.copy() else null, placementBytes, unitsBefore, unitsAfter)
                } else null
                require(volume.signum() > 0 || placement?.scheduledOnly == true) { "Empty parcel without a pending receipt" }
                require(entry.contains("lag", Tag.TAG_LONG.toInt()))
                val lag = entry.getLong("lag")
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
                restored.addLast(Parcel(dimension, Vector3d(x, y, z),
                    Vector3d(vx, vy, vz), name, fluid, volume,
                    -lag, settledSources, anchor,
                    metadataBytes = PARCEL_STORAGE_BYTES + nameBytes).also { owner ->
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

    private fun savedCoordinate(entry: CompoundTag, key: String): Double {
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
        if (players.isEmpty()) return
        val pos = parcel.blockPosition()
        if (!level.hasChunkAt(pos)) return
        // Pay for the recipient snapshot, UUID set/table and retry scratch before constructing
        // them or sending a packet. New overlays are optional; existing receipts are never dropped.
        val bytes = (players.size.toLong() + 1) * VISUAL_RECIPIENT_BYTES
        if (!parcelStorage.fits(bytes)) return
        parcelStorage.reserve(bytes)
        var retained = false
        try {
            val recipients = players.filter {
                it.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) <= VISUAL_RANGE_SQUARED
            }
            if (recipients.isEmpty()) return
            val current = level.getBlockState(pos)
            if (!current.fluidState.isEmpty || !current.canBeReplaced()) return
            val visualLevel = worldVolumeToVanillaLevel(parcel.sourceUnits.toDouble() / 255.0).coerceAtMost(7)
            val packet = ClientboundBlockUpdatePacket(pos, fluid.getFlowing(visualLevel, true).createLegacyBlock())
            val recipientIds = HashSet<UUID>(recipients.size)
            recipients.forEach { recipientIds.add(it.uuid) }
            parcel.visualRecipients = recipientIds
            parcel.visualPos = pos
            parcel.visualStorageBytes = bytes
            retained = true // The full receipt survives partial send/restore exceptions.
            recipients.forEach { it.connection.send(packet) }
        } finally {
            if (!retained) parcelStorage.release(bytes)
        }
    }

    private fun restoreVisual(level: ServerLevel, parcel: Parcel) {
        val pos = parcel.visualPos ?: return
        if (level.hasChunkAt(pos)) {
            val recipients = level.players().filter { it.uuid in parcel.visualRecipients }
            if (recipients.isNotEmpty()) {
                val packet = ClientboundBlockUpdatePacket(pos, level.getBlockState(pos))
                recipients.forEach { it.connection.send(packet) }
            }
        }
        // On a send exception the full receipt remains, making restoration retryable.
        parcel.visualPos = null
        parcel.visualRecipients = emptySet()
        parcelStorage.release(parcel.visualStorageBytes)
        parcel.visualStorageBytes = 0L
    }

    private companion object {
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
        val SETTLEMENT_DIRECTIONS = listOf(Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, Direction.UP)
    }
}

private const val FLUID_PARCEL_TIME_STEP = 1.0 / 20.0
private const val FLUID_PARCEL_GRAVITY = -9.81
private const val MAX_FLUID_PARCEL_SPEED = 64.0
private const val MAX_FLUID_PARCEL_SUBSTEP_DISTANCE = 0.45
private const val MAX_FLUID_PARCEL_SUBSTEPS = 32
