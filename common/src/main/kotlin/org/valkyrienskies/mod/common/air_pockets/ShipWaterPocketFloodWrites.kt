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

private fun processQueuedAddIndices(
    state: ShipPocketState,
    budget: Int,
    handle: (Int) -> Unit,
): Pair<Int, Int> {
    if (budget <= 0 || state.queuedFloodAdds.isEmpty) return 0 to state.nextQueuedAddIdx

    var processed = 0
    val ordered = state.queuedFloodAddOrder
    var orderedCursor = state.nextQueuedAddOrderIdx.coerceAtLeast(0)

    while (orderedCursor < ordered.size && processed < budget) {
        val idx = ordered.getInt(orderedCursor++)
        if (!state.queuedFloodAdds.get(idx)) continue
        handle(idx)
        state.queuedFloodAdds.clear(idx)
        processed++
    }

    state.nextQueuedAddOrderIdx = orderedCursor

    if (orderedCursor > 2048 && orderedCursor * 2 >= ordered.size) {
        ordered.removeElements(0, orderedCursor)
        state.nextQueuedAddOrderIdx = 0
    }

    if (!state.queuedFloodAdds.isEmpty && processed < budget) {
        val fallback = processQueuedIndices(
            queue = state.queuedFloodAdds,
            startCursor = state.nextQueuedAddIdx,
            budget = budget - processed,
            handle = handle,
        )
        state.nextQueuedAddIdx = fallback.second
        processed += fallback.first
    }

    if (state.queuedFloodAdds.isEmpty) {
        ordered.clear()
        state.nextQueuedAddOrderIdx = 0
        state.nextQueuedAddIdx = 0
    }

    return processed to state.nextQueuedAddIdx
}

internal fun flushFloodWriteQueue(
    level: ServerLevel,
    state: ShipPocketState,
    shipTransform: ShipTransform,
    removeCap: Int = FLOOD_QUEUE_REMOVE_CAP_PER_TICK,
    addCap: Int = FLOOD_QUEUE_ADD_CAP_PER_TICK,
    setApplyingInternalUpdates: (Boolean) -> Unit,
    isFloodFluidType: (Fluid) -> Boolean,
    isIngressQualifiedForAdd: (
        pos: BlockPos.MutableBlockPos,
        shipTransform: ShipTransform,
        shipPosTmp: Vector3d,
        worldPosTmp: Vector3d,
        worldBlockPos: BlockPos.MutableBlockPos,
    ) -> Boolean,
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
    val worldPosTmp = Vector3d()
    val shipPosTmp = Vector3d()
    val worldBlockPos = BlockPos.MutableBlockPos()
    val sourceBlockState = state.floodFluid.defaultFluidState().createLegacyBlock()
    val floodCanonical = canonicalFloodSource(state.floodFluid)

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

    setApplyingInternalUpdates(true)
    try {
        val removeResult = processQueuedIndices(
            queue = state.queuedFloodRemoves,
            startCursor = state.nextQueuedRemoveIdx,
            budget = removeCap,
        ) { idx ->
            if (idx < 0 || idx >= volume) return@processQueuedIndices
            posFromIndex(state, idx, pos)
            val current = level.getBlockState(pos)
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
            if (idx < 0 || idx >= volume) return@processQueuedAddIndices
            posFromIndex(state, idx, pos)
            val current = level.getBlockState(pos)
            val currentFluid = current.fluidState
            val isFlowingFloodFluid =
                !currentFluid.isEmpty && isFloodFluidType(currentFluid.type) && !currentFluid.isSource

            if (!currentFluid.isEmpty && isFloodFluidType(currentFluid.type) && currentFluid.isSource) {
                state.materializedWater.set(idx)
                return@processQueuedAddIndices
            }

            val ingressQualified = isIngressQualifiedForAdd(pos, shipTransform, shipPosTmp, worldPosTmp, worldBlockPos)
            if (!ingressQualified) {
                rejectedAdds++
                return@processQueuedAddIndices
            }

            // Flowing flood-fluid should behave like clear air for this fill step.
            if (!current.isAir && !isFlowingFloodFluid) {
                if (tryPlaceFluidInContainer(level, pos, current, floodCanonical)) {
                    addedApplied++
                    state.materializedWater.set(idx)
                    recordAddedSample(idx)
                    return@processQueuedAddIndices
                }

                if (isWaterloggableForFlood(current, floodCanonical)) {
                    if (!current.getValue(BlockStateProperties.WATERLOGGED)) {
                        val waterlogged = current.setValue(BlockStateProperties.WATERLOGGED, true)
                        level.setBlock(pos, waterlogged, FLOOD_QUEUE_SETBLOCK_FLAGS)
                        level.scheduleTick(pos, Fluids.WATER, 1)
                        addedApplied++
                    }
                    state.materializedWater.set(idx)
                    recordAddedSample(idx)
                }
                return@processQueuedAddIndices
            }

            level.setBlock(pos, sourceBlockState, FLOOD_QUEUE_SETBLOCK_FLAGS)
            level.scheduleTick(pos, state.floodFluid, 1)
            state.materializedWater.set(idx)
            addedApplied++
            recordAddedSample(idx)
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
