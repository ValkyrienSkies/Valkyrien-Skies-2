package org.valkyrienskies.mod.feature.ship_water_pockets

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.FloodWriteEffectKind
import org.valkyrienskies.mod.common.air_pockets.applyFloodBlockWrite

class ShipWaterPocketFloodBlockWriteTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun torchBreaksOnceAndDrainDoesNotRestoreIt() {
        val pos = BlockPos.MutableBlockPos(0, 64, 0)
        var currentState = Blocks.TORCH.defaultBlockState()
        val level = createTrackingLevel(readState = { currentState }) { currentState = it }

        val flooded = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = true,
        )

        assertTrue(flooded.applied)
        assertTrue(flooded.materialized)
        assertEquals(FloodWriteEffectKind.BREAK_ON_FLOOD, flooded.effect)
        assertEquals(Blocks.WATER.defaultBlockState(), currentState)

        val drained = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = false,
        )

        assertTrue(drained.applied)
        assertFalse(drained.materialized)
        assertEquals(FloodWriteEffectKind.SOURCE, drained.effect)
        assertEquals(Blocks.AIR.defaultBlockState(), currentState)

        verify(exactly = 1) { level.destroyBlock(any<BlockPos>(), true) }
        verify(exactly = 1) { level.scheduleTick(any<BlockPos>(), Fluids.WATER, 1) }
    }

    @Test
    fun repeatedTorchFloodCanSuppressDuplicateDrops() {
        val pos = BlockPos.MutableBlockPos(0, 64, 1)
        var currentState = Blocks.TORCH.defaultBlockState()
        val level = createTrackingLevel(readState = { currentState }) { currentState = it }

        val firstFlood = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = true,
        )

        assertTrue(firstFlood.applied)
        assertEquals(Blocks.WATER.defaultBlockState(), currentState)

        // Simulate an unexpected retry against the same breakable block state: the second pass must not drop again.
        currentState = Blocks.TORCH.defaultBlockState()
        val retriedFlood = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = true,
            dropOnBreak = false,
        )

        assertTrue(retriedFlood.applied)
        assertEquals(FloodWriteEffectKind.BREAK_ON_FLOOD, retriedFlood.effect)
        assertEquals(Blocks.WATER.defaultBlockState(), currentState)

        verify(exactly = 1) { level.destroyBlock(any<BlockPos>(), true) }
        verify(exactly = 1) { level.destroyBlock(any<BlockPos>(), false) }
    }

    @Test
    fun trapdoorWaterlogsAndUnwaterlogsWithoutBreaking() {
        val pos = BlockPos.MutableBlockPos(1, 64, 1)
        var currentState = Blocks.OAK_TRAPDOOR.defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, false)
        val level = createTrackingLevel(readState = { currentState }) { currentState = it }

        val flooded = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = true,
        )

        assertTrue(flooded.applied)
        assertTrue(flooded.materialized)
        assertEquals(FloodWriteEffectKind.WATERLOG, flooded.effect)
        assertTrue(currentState.getValue(BlockStateProperties.WATERLOGGED))

        val drained = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = false,
        )

        assertTrue(drained.applied)
        assertFalse(drained.materialized)
        assertEquals(FloodWriteEffectKind.WATERLOG, drained.effect)
        assertFalse(currentState.getValue(BlockStateProperties.WATERLOGGED))

        verify(exactly = 0) { level.destroyBlock(any<BlockPos>(), any()) }
        verify(exactly = 1) { level.scheduleTick(any<BlockPos>(), Fluids.WATER, 1) }
    }

    @Test
    fun containerDrainDoesNotScheduleWaterToRefillTheCell() {
        val pos = BlockPos.MutableBlockPos(3, 64, 3)
        val fixture = newTestFloodContainerFixture()
        var currentState = fixture.emptyState
        val level = createTrackingLevel(readState = { currentState }) { currentState = it }

        val flooded = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = true,
        )

        assertTrue(flooded.applied)
        assertEquals(fixture.filledState, currentState)

        val drained = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = false,
        )

        assertTrue(drained.applied)
        assertEquals(fixture.emptyState, currentState)
        verify(exactly = 1) { level.scheduleTick(any<BlockPos>(), Fluids.WATER, 1) }
    }

    @Test
    fun doorsAreNotBrokenOrMaterializedByFloodWrites() {
        val pos = BlockPos.MutableBlockPos(2, 64, 2)
        var currentState = Blocks.OAK_DOOR.defaultBlockState()
        val level = createTrackingLevel(readState = { currentState }) { currentState = it }

        val flooded = applyFloodBlockWrite(
            level = level,
            pos = pos,
            current = currentState,
            floodFluid = Fluids.WATER,
            toWater = true,
        )

        assertFalse(flooded.applied)
        assertFalse(flooded.materialized)
        assertEquals(FloodWriteEffectKind.NONE, flooded.effect)
        assertEquals(Blocks.OAK_DOOR.defaultBlockState(), currentState)

        verify(exactly = 0) { level.destroyBlock(any<BlockPos>(), any()) }
        verify(exactly = 0) { level.setBlock(any<BlockPos>(), any(), any()) }
    }

    private fun createTrackingLevel(
        readState: () -> BlockState,
        updateState: (BlockState) -> Unit,
    ): ServerLevel {
        val level = mockk<ServerLevel>(relaxed = true)
        every { level.getBlockState(any<BlockPos>()) } answers { readState() }
        every { level.setBlock(any<BlockPos>(), any(), any()) } answers {
            updateState(secondArg<BlockState>())
            true
        }
        every { level.destroyBlock(any<BlockPos>(), any()) } answers {
            updateState(Blocks.AIR.defaultBlockState())
            true
        }
        every { level.scheduleTick(any<BlockPos>(), any<Fluid>(), any()) } just runs
        return level
    }
}
