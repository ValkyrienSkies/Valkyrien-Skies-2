package org.valkyrienskies.mod.feature.ship_water_pockets

import it.unimi.dsi.fastutil.ints.IntArrayList
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.FloodWriteEffectKind
import org.valkyrienskies.mod.common.air_pockets.ShipPocketState
import org.valkyrienskies.mod.common.air_pockets.applyFloodBlockWrite
import org.valkyrienskies.mod.common.air_pockets.flushFloodWriteQueue
import org.valkyrienskies.mod.common.air_pockets.floodCanonicalSource

class ShipWaterPocketFloodQueueParityTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            ShipWaterPocketTestSupport.bootstrapMinecraft()
        }
    }

    private val shipTransform = IdentityShipTransform()

    @Test
    fun queuedFragileBlockMatchesDirectRoundTrip() {
        val direct = runDirectRoundTrip(Blocks.TORCH.defaultBlockState())
        val queued = runQueuedRoundTrip(Blocks.TORCH.defaultBlockState())

        assertEquals(FloodWriteEffectKind.BREAK_ON_FLOOD, direct.floodEffect)
        assertEquals(FloodWriteEffectKind.SOURCE, direct.drainEffect)
        assertEquals(direct.floodState, queued.floodState)
        assertEquals(direct.drainState, queued.drainState)
        assertTrue(queued.brokenAfterFlood)
        assertFalse(queued.brokenAfterDrain)
    }

    @Test
    fun queuedWaterloggableBlockMatchesDirectRoundTrip() {
        val initial = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false)
        val direct = runDirectRoundTrip(initial)
        val queued = runQueuedRoundTrip(initial)

        assertEquals(FloodWriteEffectKind.WATERLOG, direct.floodEffect)
        assertEquals(FloodWriteEffectKind.WATERLOG, direct.drainEffect)
        assertEquals(direct.floodState, queued.floodState)
        assertEquals(direct.drainState, queued.drainState)
        assertFalse(queued.brokenAfterFlood)
        assertFalse(queued.brokenAfterDrain)
    }

    @Test
    fun queuedContainerBlockMatchesDirectRoundTrip() {
        val container = newTestFloodContainerFixture()
        val direct = runDirectRoundTrip(container.emptyState)
        val queued = runQueuedRoundTrip(container.emptyState)

        assertEquals(FloodWriteEffectKind.CONTAINER, direct.floodEffect)
        assertEquals(FloodWriteEffectKind.CONTAINER, direct.drainEffect)
        assertEquals(container.filledState, direct.floodState)
        assertEquals(container.emptyState, direct.drainState)
        assertEquals(direct.floodState, queued.floodState)
        assertEquals(direct.drainState, queued.drainState)
        assertFalse(queued.brokenAfterFlood)
        assertFalse(queued.brokenAfterDrain)
    }

    private fun runDirectRoundTrip(initialState: BlockState): DirectRoundTripResult {
        val states = mutableMapOf(blockKey(0, 0, 0) to initialState)
        val level = createTrackingLevel(states)
        val pos = BlockPos.MutableBlockPos(0, 0, 0)

        val flood = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = level.getBlockState(pos),
            floodFluid = Fluids.WATER,
            toWater = true,
        )
        val floodState = level.getBlockState(pos)

        val drain = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = floodState,
            floodFluid = Fluids.WATER,
            toWater = false,
        )
        val drainState = level.getBlockState(pos)

        return DirectRoundTripResult(
            floodState = floodState,
            drainState = drainState,
            floodEffect = flood.effect,
            drainEffect = drain.effect,
        )
    }

    private fun runQueuedRoundTrip(initialState: BlockState): QueuedRoundTripResult {
        val states = mutableMapOf(
            blockKey(0, 0, 0) to initialState,
            blockKey(0, 1, 0) to Blocks.AIR.defaultBlockState(),
        )
        val level = createTrackingLevel(states)
        val state = singleCellQueueState()

        state.queuedFloodAdds.set(0)
        state.queuedFloodAddOrder = IntArrayList().apply { add(0) }

        val floodResult = flushFloodWriteQueue(
            level = level,
            state = state,
            shipTransform = shipTransform,
            addCap = 1,
            setApplyingInternalUpdates = {},
            isFloodFluidType = { fluid -> floodCanonicalSource(fluid) == Fluids.WATER },
            isExteriorFloodSeedReady = { pos, _, _, _, _ -> pos.y == 1 },
        )

        assertEquals(1, floodResult.added)
        assertEquals(0, floodResult.remainingQueued)
        val floodState = level.getBlockState(BlockPos(0, 0, 0))
        val brokenAfterFlood = state.brokenByFlood.get(0)

        state.queuedFloodRemoves.set(0)
        val drainResult = flushFloodWriteQueue(
            level = level,
            state = state,
            shipTransform = shipTransform,
            removeCap = 1,
            addCap = 0,
            setApplyingInternalUpdates = {},
            isFloodFluidType = { fluid -> floodCanonicalSource(fluid) == Fluids.WATER },
            isExteriorFloodSeedReady = { _, _, _, _, _ -> false },
        )

        assertEquals(1, drainResult.removed)
        assertEquals(0, drainResult.remainingQueued)
        val drainState = level.getBlockState(BlockPos(0, 0, 0))

        return QueuedRoundTripResult(
            floodState = floodState,
            drainState = drainState,
            brokenAfterFlood = brokenAfterFlood,
            brokenAfterDrain = state.brokenByFlood.get(0),
        )
    }

    private fun singleCellQueueState(): ShipPocketState {
        return verticalPocketState(
            sizeY = 2,
            simulationIndices = intArrayOf(0),
            exteriorIndices = intArrayOf(1),
        )
    }

    private data class DirectRoundTripResult(
        val floodState: BlockState,
        val drainState: BlockState,
        val floodEffect: FloodWriteEffectKind,
        val drainEffect: FloodWriteEffectKind,
    )

    private data class QueuedRoundTripResult(
        val floodState: BlockState,
        val drainState: BlockState,
        val brokenAfterFlood: Boolean,
        val brokenAfterDrain: Boolean,
    )
}
