package org.valkyrienskies.mod.common.air_pockets

import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.LiquidBlockContainer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.FlowingFluid
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.mod.util.FluidStateManager
import java.util.BitSet

internal const val FLOOD_QUEUE_REMOVE_CAP_PER_TICK: Int = 512
internal const val FLOOD_QUEUE_ADD_CAP_PER_TICK: Int = 512

private const val FLOOD_QUEUE_SETBLOCK_FLAGS: Int = 3 // UPDATE_NEIGHBORS | UPDATE_CLIENTS

private fun canonicalFloodSource(fluid: Fluid): Fluid {
    return if (fluid is FlowingFluid) fluid.source else fluid
}

private fun isWaterloggableForFlood(state: BlockState, floodFluid: Fluid): Boolean {
    return canonicalFloodSource(floodFluid) == Fluids.WATER && state.hasProperty(BlockStateProperties.WATERLOGGED)
}

private fun tryPlaceFluidInContainer(
    level: ServerLevel,
    pos: BlockPos.MutableBlockPos,
    current: BlockState,
    floodFluid: Fluid,
): Boolean {
    val canonical = canonicalFloodSource(floodFluid)
    val flowing = canonical as? FlowingFluid ?: return false
    val block = current.block
    if (block !is LiquidBlockContainer) return false
    return try {
        if (!block.canPlaceLiquid(level, pos, current, canonical)) return false
        if (!block.placeLiquid(level, pos, current, flowing.source.defaultFluidState())) return false
        level.scheduleTick(pos, canonical, 1)
        true
    } catch (_: Throwable) {
        false
    }
}

private fun tryDrainFluidFromContainer(
    level: ServerLevel,
    pos: BlockPos.MutableBlockPos,
    current: BlockState,
    floodFluid: Fluid,
): Boolean {
    val canonical = canonicalFloodSource(floodFluid)
    val currentFluid = current.fluidState
    if (currentFluid.isEmpty || canonicalFloodSource(currentFluid.type) != canonical) return false
    val block = current.block
    if (block !is BucketPickup) return false
    return try {
        val picked = block.pickupBlock(level, pos, current)
        if (picked.isEmpty) return false
        level.scheduleTick(pos, canonical, 1)
        true
    } catch (_: Throwable) {
        false
    }
}

internal data class FloodWriteFlushResult(
    val removed: Int,
    val added: Int,
    val rejectedAdds: Int,
    val blockedExteriorWaterlogs: Int,
    val addedSampleIndices: IntArray,
    val remainingQueued: Int,
)

internal enum class FloodWriteAddDisposition {
    APPLIED,
    DEFERRED,
    REJECTED,
}

private fun compactQueuedAddOrder(state: ShipPocketState) {
    val ordered = state.queuedFloodAddOrder
    if (ordered.isEmpty) {
        state.nextQueuedAddOrderIdx = 0
        return
    }

    val cursor = state.nextQueuedAddOrderIdx.coerceIn(0, ordered.size)
    val compacted = IntArrayList(state.queuedFloodAdds.cardinality())
    var nextCursor = 0
    for (i in 0 until ordered.size) {
        val idx = ordered.getInt(i)
        if (!state.queuedFloodAdds.get(idx)) continue
        if (i < cursor) nextCursor++
        compacted.add(idx)
    }

    state.queuedFloodAddOrder = compacted
    state.nextQueuedAddOrderIdx = nextCursor.coerceIn(0, compacted.size)
    if (compacted.isEmpty()) {
        state.nextQueuedAddIdx = 0
    }
}

internal fun isFloodAddFrontierReady(
    state: ShipPocketState,
    idx: Int,
    addedThisFlush: BitSet,
    isExteriorFloodSeedReady: (Int) -> Boolean,
): Boolean {
    val sizeX = state.sizeX
    val sizeY = state.sizeY
    val sizeZ = state.sizeZ
    val volume = sizeX * sizeY * sizeZ
    if (idx !in 0 until volume) return false
    if (!state.open.get(idx) || !state.simulationDomain.get(idx)) return false
    if (state.materializedWater.get(idx) || addedThisFlush.get(idx)) return true

    val strideY = sizeX
    val strideZ = sizeX * sizeY
    val lx = idx % sizeX
    val t = idx / sizeX
    val ly = t % sizeY
    val lz = t / sizeY
    val hasComponentConnectivity = hasComponentTraversalSupport(state)
    val curMask = if (hasComponentConnectivity) simulationComponentMaskAt(state, idx) else -1L

    fun neighborReady(neighborIdx: Int, dirCode: Int): Boolean {
        if (neighborIdx !in 0 until volume) return false
        if (!state.open.get(neighborIdx)) return false

        val conductance = if (hasComponentConnectivity) {
            val neighborMask = if (state.simulationDomain.get(neighborIdx)) {
                simulationComponentMaskAt(state, neighborIdx)
            } else {
                exteriorComponentMaskAt(state, neighborIdx)
            }
            computeFilteredFaceConductance(
                state = state,
                idxA = idx,
                idxB = neighborIdx,
                dirCode = dirCode,
                componentMaskA = curMask,
                componentMaskB = neighborMask,
            )
        } else {
            edgeConductance(state, idx, lx, ly, lz, dirCode)
        }
        if (conductance <= 0) return false

        return if (state.simulationDomain.get(neighborIdx)) {
            state.materializedWater.get(neighborIdx) || addedThisFlush.get(neighborIdx)
        } else {
            state.outsideVoid.get(neighborIdx) && isExteriorFloodSeedReady(neighborIdx)
        }
    }

    return (lx > 0 && neighborReady(idx - 1, 0)) ||
        (lx + 1 < sizeX && neighborReady(idx + 1, 1)) ||
        (ly > 0 && neighborReady(idx - strideY, 2)) ||
        (ly + 1 < sizeY && neighborReady(idx + strideY, 3)) ||
        (lz > 0 && neighborReady(idx - strideZ, 4)) ||
        (lz + 1 < sizeZ && neighborReady(idx + strideZ, 5))
}

internal fun enqueueFloodWriteDiffs(
    state: ShipPocketState,
    toAdd: BitSet,
    toRemove: BitSet,
    orderedAdds: IntArrayList? = null,
) {
    if (!toRemove.isEmpty) {
        state.queuedFloodRemoves.or(toRemove)
        state.queuedFloodAdds.andNot(toRemove)
    }
    if (!toAdd.isEmpty) {
        if (orderedAdds != null && !orderedAdds.isEmpty) {
            for (i in 0 until orderedAdds.size) {
                val idx = orderedAdds.getInt(i)
                if (idx < 0 || !toAdd.get(idx)) continue
                if (!state.queuedFloodAdds.get(idx)) {
                    state.queuedFloodAddOrder.add(idx)
                }
                state.queuedFloodAdds.set(idx)
                state.queuedFloodRemoves.clear(idx)
            }
        }

        var idx = toAdd.nextSetBit(0)
        while (idx >= 0) {
            if (!state.queuedFloodAdds.get(idx)) {
                state.queuedFloodAddOrder.add(idx)
            }
            state.queuedFloodAdds.set(idx)
            state.queuedFloodRemoves.clear(idx)
            idx = toAdd.nextSetBit(idx + 1)
        }
    }
}

internal fun clearFloodWriteQueues(state: ShipPocketState) {
    state.queuedFloodAdds.clear()
    state.queuedFloodRemoves.clear()
    state.queuedFloodAddOrder.clear()
    state.nextQueuedAddOrderIdx = 0
    state.nextQueuedAddIdx = 0
    state.nextQueuedRemoveIdx = 0
}

private fun processQueuedIndices(
    queue: BitSet,
    startCursor: Int,
    budget: Int,
    handle: (Int) -> Unit,
): Pair<Int, Int> {
    if (budget <= 0 || queue.isEmpty) return 0 to 0

    var processed = 0
    val start = startCursor.coerceAtLeast(0)
    var idx = queue.nextSetBit(start)
    if (idx < 0 && start > 0) {
        idx = queue.nextSetBit(0)
    }

    while (idx >= 0 && processed < budget) {
        val current = idx
        idx = queue.nextSetBit(current + 1)
        handle(current)
        queue.clear(current)
        processed++
        if (idx < 0 && processed < budget) {
            idx = queue.nextSetBit(0)
        }
    }

    return processed to if (idx >= 0) idx else 0
}

internal fun processQueuedAddIndices(
    state: ShipPocketState,
    budget: Int,
    handle: (Int) -> FloodWriteAddDisposition,
): Pair<Int, Int> {
    if (budget <= 0 || state.queuedFloodAdds.isEmpty) return 0 to state.nextQueuedAddIdx

    var applied = 0
    var orderedCursor = state.nextQueuedAddOrderIdx.coerceAtLeast(0)
    var fallbackCursor = state.nextQueuedAddIdx.coerceAtLeast(0)
    var passCount = 0

    do {
        var progressed = false
        val ordered = state.queuedFloodAddOrder
        val orderedSize = ordered.size
        if (orderedSize > 0) {
            if (orderedCursor >= orderedSize) orderedCursor %= orderedSize

            var visited = 0
            var cursor = orderedCursor
            while (visited < orderedSize && applied < budget) {
                if (cursor >= orderedSize) cursor = 0
                val idx = ordered.getInt(cursor)
                cursor++
                visited++
                if (!state.queuedFloodAdds.get(idx)) continue
                when (handle(idx)) {
                    FloodWriteAddDisposition.APPLIED -> {
                        state.queuedFloodAdds.clear(idx)
                        applied++
                        progressed = true
                    }

                    FloodWriteAddDisposition.REJECTED -> {
                        state.queuedFloodAdds.clear(idx)
                        progressed = true
                    }

                    FloodWriteAddDisposition.DEFERRED -> Unit
                }
            }
            orderedCursor = if (orderedSize > 0) cursor % orderedSize else 0
        }

        if (!state.queuedFloodAdds.isEmpty && applied < budget) {
            val start = fallbackCursor.coerceAtLeast(0)
            var idx = state.queuedFloodAdds.nextSetBit(start)
            if (idx < 0 && start > 0) {
                idx = state.queuedFloodAdds.nextSetBit(0)
            }

            val initialQueued = state.queuedFloodAdds.cardinality()
            var inspected = 0
            while (idx >= 0 && inspected < initialQueued && applied < budget) {
                val current = idx
                idx = state.queuedFloodAdds.nextSetBit(current + 1)
                if (idx < 0 && inspected + 1 < initialQueued) {
                    idx = state.queuedFloodAdds.nextSetBit(0)
                }

                when (handle(current)) {
                    FloodWriteAddDisposition.APPLIED -> {
                        state.queuedFloodAdds.clear(current)
                        applied++
                        progressed = true
                    }

                    FloodWriteAddDisposition.REJECTED -> {
                        state.queuedFloodAdds.clear(current)
                        progressed = true
                    }

                    FloodWriteAddDisposition.DEFERRED -> Unit
                }
                inspected++
            }
            fallbackCursor = if (idx >= 0) idx else 0
        }

        passCount++
        if (!progressed) break
    } while (!state.queuedFloodAdds.isEmpty && applied < budget && passCount <= budget)

    state.nextQueuedAddOrderIdx = orderedCursor
    state.nextQueuedAddIdx = fallbackCursor

    if (state.queuedFloodAdds.isEmpty) {
        state.queuedFloodAddOrder.clear()
        state.nextQueuedAddOrderIdx = 0
        state.nextQueuedAddIdx = 0
    } else if (
        state.queuedFloodAddOrder.size > 2048 ||
        state.queuedFloodAddOrder.size > state.queuedFloodAdds.cardinality() * 2
    ) {
        compactQueuedAddOrder(state)
    }

    return applied to state.nextQueuedAddIdx
}

internal fun flushFloodWriteQueue(
    level: ServerLevel,
    state: ShipPocketState,
    shipTransform: ShipTransform,
    removeCap: Int = FLOOD_QUEUE_REMOVE_CAP_PER_TICK,
    addCap: Int = FLOOD_QUEUE_ADD_CAP_PER_TICK,
    setApplyingInternalUpdates: (Boolean) -> Unit,
    isFloodFluidType: (Fluid) -> Boolean,
    isExteriorFloodSeedReady: (
        pos: BlockPos.MutableBlockPos,
        shipTransform: ShipTransform,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        worldBlockPos: BlockPos.MutableBlockPos,
    ) -> Boolean,
    queryCache: FluidStateManager.QueryCache? = null,
): FloodWriteFlushResult {
    if (state.queuedFloodAdds.isEmpty && state.queuedFloodRemoves.isEmpty) {
        return FloodWriteFlushResult(
            removed = 0,
            added = 0,
            rejectedAdds = 0,
            blockedExteriorWaterlogs = 0,
            addedSampleIndices = IntArray(0),
            remainingQueued = 0,
        )
    }

    val volume = state.sizeX * state.sizeY * state.sizeZ
    if (volume <= 0) {
        clearFloodWriteQueues(state)
        return FloodWriteFlushResult(
            removed = 0,
            added = 0,
            rejectedAdds = 0,
            blockedExteriorWaterlogs = 0,
            addedSampleIndices = IntArray(0),
            remainingQueued = 0,
        )
    }

    val pos = BlockPos.MutableBlockPos()
    val exteriorSeedPos = BlockPos.MutableBlockPos()
    val worldPosTmp = Vector3d()
    val shipPosTmp = Vector3d()
    val worldBlockPos = BlockPos.MutableBlockPos()
    val sourceBlockState = state.floodFluid.defaultFluidState().createLegacyBlock()
    val floodCanonical = canonicalFloodSource(state.floodFluid)
    val addedThisFlush = BitSet(volume)
    val exteriorSeedEvaluated = BitSet(volume)
    val exteriorSeedReady = BitSet(volume)

    var removedApplied = 0
    var addedApplied = 0
    var rejectedAdds = 0
    var blockedExteriorWaterlogs = 0
    val addedSamplesMax = 48
    val addedSampleIndices = IntArray(addedSamplesMax)
    var addedSampleCount = 0

    fun recordAddedSample(idx: Int) {
        if (addedSampleCount >= addedSamplesMax) return
        addedSampleIndices[addedSampleCount++] = idx
    }

    fun isExteriorSeedReadyAt(exteriorIdx: Int): Boolean {
        if (exteriorIdx !in 0 until volume) return false
        if (exteriorSeedEvaluated.get(exteriorIdx)) {
            return exteriorSeedReady.get(exteriorIdx)
        }

        exteriorSeedEvaluated.set(exteriorIdx)
        posFromIndex(state, exteriorIdx, exteriorSeedPos)
        val ready = isExteriorFloodSeedReady(exteriorSeedPos, shipTransform, shipPosTmp, worldPosTmp, worldBlockPos)
        if (ready) {
            exteriorSeedReady.set(exteriorIdx)
        }
        return ready
    }

    setApplyingInternalUpdates(true)
    try {
        val removeResult = processQueuedIndices(
            queue = state.queuedFloodRemoves,
            startCursor = state.nextQueuedRemoveIdx,
            budget = removeCap,
        ) { idx ->
            if (idx < 0 || idx >= volume) return@processQueuedIndices
            posFromIndex(state, idx, pos)
            val current = FluidStateManager.getBlockState(level, pos, queryCache)
            val currentFluid = current.fluidState

            if (current.block is LiquidBlock && !currentFluid.isEmpty && isFloodFluidType(currentFluid.type)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLOOD_QUEUE_SETBLOCK_FLAGS)
                level.scheduleTick(pos, state.floodFluid, 1)
                removedApplied++
            } else if (tryDrainFluidFromContainer(level, pos, current, floodCanonical)) {
                removedApplied++
            } else if (isWaterloggableForFlood(current, floodCanonical) &&
                current.getValue(BlockStateProperties.WATERLOGGED)
            ) {
                val drained = current.setValue(BlockStateProperties.WATERLOGGED, false)
                level.setBlock(pos, drained, FLOOD_QUEUE_SETBLOCK_FLAGS)
                level.scheduleTick(pos, Fluids.WATER, 1)
                removedApplied++
            }
            state.materializedWater.clear(idx)
        }
        state.nextQueuedRemoveIdx = removeResult.second

        val addResult = processQueuedAddIndices(
            state = state,
            budget = addCap,
        ) { idx ->
            if (idx < 0 || idx >= volume) return@processQueuedAddIndices FloodWriteAddDisposition.REJECTED
            if (!state.simulationDomain.get(idx) || !state.open.get(idx)) {
                state.materializedWater.clear(idx)
                rejectedAdds++
                return@processQueuedAddIndices FloodWriteAddDisposition.REJECTED
            }
            posFromIndex(state, idx, pos)
            val current = FluidStateManager.getBlockState(level, pos, queryCache)
            val currentFluid = current.fluidState
            val isFlowingFloodFluid =
                !currentFluid.isEmpty && isFloodFluidType(currentFluid.type) && !currentFluid.isSource

            if (!currentFluid.isEmpty && isFloodFluidType(currentFluid.type) && currentFluid.isSource) {
                state.materializedWater.set(idx)
                addedThisFlush.set(idx)
                return@processQueuedAddIndices FloodWriteAddDisposition.APPLIED
            }

            if (!isFloodAddFrontierReady(state, idx, addedThisFlush, ::isExteriorSeedReadyAt)) {
                return@processQueuedAddIndices FloodWriteAddDisposition.DEFERRED
            }

            // Flowing flood-fluid should behave like clear air for this fill step.
            if (!current.isAir && !isFlowingFloodFluid) {
                if (tryPlaceFluidInContainer(level, pos, current, floodCanonical)) {
                    addedApplied++
                    state.materializedWater.set(idx)
                    addedThisFlush.set(idx)
                    recordAddedSample(idx)
                    return@processQueuedAddIndices FloodWriteAddDisposition.APPLIED
                }

                if (isWaterloggableForFlood(current, floodCanonical)) {
                    if (!current.getValue(BlockStateProperties.WATERLOGGED)) {
                        val waterlogged = current.setValue(BlockStateProperties.WATERLOGGED, true)
                        level.setBlock(pos, waterlogged, FLOOD_QUEUE_SETBLOCK_FLAGS)
                        level.scheduleTick(pos, Fluids.WATER, 1)
                        addedApplied++
                    }
                    state.materializedWater.set(idx)
                    addedThisFlush.set(idx)
                    recordAddedSample(idx)
                    return@processQueuedAddIndices FloodWriteAddDisposition.APPLIED
                }

                rejectedAdds++
                return@processQueuedAddIndices FloodWriteAddDisposition.REJECTED
            }

            level.setBlock(pos, sourceBlockState, FLOOD_QUEUE_SETBLOCK_FLAGS)
            level.scheduleTick(pos, state.floodFluid, 1)
            state.materializedWater.set(idx)
            addedThisFlush.set(idx)
            addedApplied++
            recordAddedSample(idx)
            FloodWriteAddDisposition.APPLIED
        }
        state.nextQueuedAddIdx = addResult.second
        if (state.queuedFloodAdds.isEmpty) {
            state.queuedFloodAddOrder.clear()
            state.nextQueuedAddOrderIdx = 0
            state.nextQueuedAddIdx = 0
        }
    } finally {
        setApplyingInternalUpdates(false)
    }

    return FloodWriteFlushResult(
        removed = removedApplied,
        added = addedApplied,
        rejectedAdds = rejectedAdds,
        blockedExteriorWaterlogs = blockedExteriorWaterlogs,
        addedSampleIndices = addedSampleIndices.copyOf(addedSampleCount),
        remainingQueued = state.queuedFloodAdds.cardinality() + state.queuedFloodRemoves.cardinality(),
    )
}
