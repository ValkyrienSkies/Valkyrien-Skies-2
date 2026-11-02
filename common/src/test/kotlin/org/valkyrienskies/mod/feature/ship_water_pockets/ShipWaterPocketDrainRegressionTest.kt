package org.valkyrienskies.mod.feature.ship_water_pockets

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.util.BitSet
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.flushFloodWriteQueue
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.air_pockets.floodCanonicalSource

class ShipWaterPocketDrainRegressionTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            ShipWaterPocketTestSupport.bootstrapMinecraft()
        }
    }

    private val shipTransform = IdentityShipTransform()

    @Test
    fun drainSelectionSkipsProtectedCellsWithinFloodedComponent() {
        val states = mutableMapOf(
            blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState(),
            blockKey(0, 1, 0) to Blocks.WATER.defaultBlockState(),
            blockKey(0, 2, 0) to Blocks.WATER.defaultBlockState(),
        )
        val level = createTrackingLevel(states, gameTime = 0L)
        val state = verticalPocketState(
            sizeY = 3,
            simulationIndices = intArrayOf(1, 2),
            exteriorIndices = intArrayOf(0),
        )
        state.materializedWater = bitSetOf(1, 2)
        state.flooded = bitSetOf(1, 2)

        val toRemoveAll = BitSet()
        withAirPocketWorldQueriesDisabled {
            invokeDrainFloodedInteriorToOutsideAir(
                level = level,
                state = state,
                shipTransform = shipTransform,
                protectedInterior = bitSetOf(1),
                newPlanesOut = Int2DoubleOpenHashMap(),
                toRemoveAll = toRemoveAll,
            )
        }

        assertFalse(toRemoveAll.get(1))
        assertTrue(toRemoveAll.get(2))
        assertEquals(1, toRemoveAll.cardinality())
    }

    @Test
    fun drainingClearedCellBlocksVanillaRefillPlacement() {
        val level = createTrackingLevel(
            states = mutableMapOf(blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState()),
            gameTime = 0L,
        )
        val state = verticalPocketState(
            sizeY = 1,
            simulationIndices = intArrayOf(0),
            exteriorIndices = intArrayOf(),
        ).apply {
            dirty = false
            drainSuppressed = bitSetOf(0)
            materializedWater = BitSet()
        }

        withRegisteredServerState(level, shipId = 42L, state = state) {
            assertTrue(ShipWaterPocketManager.shouldBlockShipyardWaterPlacement(level, 42L, BlockPos(0, 0, 0)))

            state.materializedWater.set(0)
            assertFalse(ShipWaterPocketManager.shouldBlockShipyardWaterPlacement(level, 42L, BlockPos(0, 0, 0)))
        }
    }

    @Test
    fun staleFloodedCellDoesNotBlockVanillaPlacementAfterDrainStops() {
        val level = createTrackingLevel(
            states = mutableMapOf(blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState()),
            gameTime = 0L,
        )
        val state = verticalPocketState(
            sizeY = 1,
            simulationIndices = intArrayOf(0),
            exteriorIndices = intArrayOf(),
        ).apply {
            dirty = false
            flooded = bitSetOf(0)
            materializedWater = BitSet()
        }

        withRegisteredServerState(level, shipId = 43L, state = state) {
            assertFalse(ShipWaterPocketManager.shouldBlockShipyardWaterPlacement(level, 43L, BlockPos(0, 0, 0)))
        }
    }

    @Test
    fun queuedDrainRemovalBlocksVanillaRefillPlacement() {
        val level = createTrackingLevel(
            states = mutableMapOf(blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState()),
            gameTime = 0L,
        )
        val state = verticalPocketState(
            sizeY = 1,
            simulationIndices = intArrayOf(0),
            exteriorIndices = intArrayOf(),
        ).apply {
            dirty = false
            flooded = BitSet()
            materializedWater = BitSet()
            queuedFloodRemoves = bitSetOf(0)
        }

        withRegisteredServerState(level, shipId = 44L, state = state) {
            assertTrue(ShipWaterPocketManager.shouldBlockShipyardWaterPlacement(level, 44L, BlockPos(0, 0, 0)))
        }
    }

    @Test
    fun bottomHoleDrainsAlongGravityAtIdentityRotation() {
        val level = createTrackingLevel(
            states = mutableMapOf(
                blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState(),
                blockKey(0, 1, 0) to Blocks.WATER.defaultBlockState(),
                blockKey(0, 2, 0) to Blocks.WATER.defaultBlockState(),
            ),
            gameTime = 0L,
        )
        val state = verticalPocketState(
            sizeY = 3,
            simulationIndices = intArrayOf(1, 2),
            exteriorIndices = intArrayOf(0),
        ).apply {
            materializedWater = bitSetOf(1, 2)
            flooded = bitSetOf(1, 2)
        }

        val toRemoveAll = BitSet()
        val drainSuppressedOut = BitSet()
        withAirPocketWorldQueriesDisabled {
            invokeDrainFloodedInteriorToOutsideAir(
                level = level,
                state = state,
                shipTransform = shipTransform,
                protectedInterior = null,
                newPlanesOut = Int2DoubleOpenHashMap(),
                toRemoveAll = toRemoveAll,
                drainSuppressedOut = drainSuppressedOut,
            )
        }

        assertTrue(drainSuppressedOut.get(2))
        assertTrue(toRemoveAll.get(2))
        assertFalse(toRemoveAll.get(1))
        assertEquals(1, toRemoveAll.cardinality())
    }

    @Test
    fun sourceConnectedToBottomHoleByFlowingWaterStillDrains() {
        val level = createTrackingLevel(
            states = mutableMapOf(
                blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState(),
                blockKey(0, 1, 0) to Fluids.FLOWING_WATER.defaultFluidState().createLegacyBlock(),
                blockKey(0, 2, 0) to Blocks.WATER.defaultBlockState(),
            ),
            gameTime = 0L,
        )
        val state = verticalPocketState(
            sizeY = 3,
            simulationIndices = intArrayOf(1, 2),
            exteriorIndices = intArrayOf(0),
        ).apply {
            flooded = bitSetOf(2)
            materializedWater = bitSetOf(2)
        }

        invokeSyncMaterializedFloodFluidFromWorld(level, state)
        assertTrue(state.materializedWater.get(1))
        assertTrue(state.materializedWater.get(2))

        val toRemoveAll = BitSet()
        withAirPocketWorldQueriesDisabled {
            invokeDrainFloodedInteriorToOutsideAir(
                level = level,
                state = state,
                shipTransform = shipTransform,
                protectedInterior = null,
                newPlanesOut = Int2DoubleOpenHashMap(),
                toRemoveAll = toRemoveAll,
            )
        }

        assertTrue(toRemoveAll.get(2))
        assertFalse(toRemoveAll.get(1))
        assertEquals(1, toRemoveAll.cardinality())
    }

    @Test
    fun rotatedBottomHoleStillDrainsAlongGravity() {
        val level = createTrackingLevel(
            states = mutableMapOf(
                blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState(),
                blockKey(1, 0, 0) to Blocks.WATER.defaultBlockState(),
                blockKey(2, 0, 0) to Blocks.WATER.defaultBlockState(),
            ),
            gameTime = 0L,
        )
        val state = horizontalPocketStateX(
            sizeX = 3,
            simulationIndices = intArrayOf(1, 2),
            exteriorIndices = intArrayOf(0),
        ).apply {
            materializedWater = bitSetOf(1, 2)
            flooded = bitSetOf(1, 2)
        }
        val rotatedTransform = TestShipTransform(
            position = Vector3d(100.0, 100.0, 100.0),
            rotation = Quaterniond().rotateZ(Math.PI / 2.0),
        )

        val toRemoveAll = BitSet()
        withAirPocketWorldQueriesDisabled {
            invokeDrainFloodedInteriorToOutsideAir(
                level = level,
                state = state,
                shipTransform = rotatedTransform,
                protectedInterior = null,
                newPlanesOut = Int2DoubleOpenHashMap(),
                toRemoveAll = toRemoveAll,
            )
        }

        assertFalse(toRemoveAll.get(1))
        assertTrue(toRemoveAll.get(2))
        assertEquals(1, toRemoveAll.cardinality())
    }

    @Test
    fun queueFloodProgressionThenDrainPreservesProtectedBrokenCell() {
        val states = mutableMapOf(
            blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState(),
            blockKey(0, 1, 0) to Blocks.TORCH.defaultBlockState(),
            blockKey(0, 2, 0) to Blocks.TORCH.defaultBlockState(),
            blockKey(0, 3, 0) to Blocks.AIR.defaultBlockState(),
        )
        val level = createTrackingLevel(states, gameTime = 0L)
        val state = verticalPocketState(
            sizeY = 4,
            simulationIndices = intArrayOf(1, 2),
            exteriorIndices = intArrayOf(0, 3),
        )
        state.queuedFloodAdds.set(1)
        state.queuedFloodAdds.set(2)
        state.queuedFloodAddOrder = IntArrayList().apply {
            add(1)
            add(2)
        }

        val firstFloodFlush = flushFloodWriteQueue(
            level = level,
            state = state,
            shipTransform = shipTransform,
            addCap = 1,
            setApplyingInternalUpdates = {},
            isFloodFluidType = { fluid -> floodCanonicalSource(fluid) == Fluids.WATER },
            isExteriorFloodSeedReady = { pos, _, _, _, _ -> pos.y == 3 },
        )

        assertEquals(1, firstFloodFlush.added)
        assertEquals(1, firstFloodFlush.remainingQueued)
        assertEquals(Blocks.TORCH.defaultBlockState(), level.getBlockState(BlockPos(0, 1, 0)))
        assertEquals(Blocks.WATER.defaultBlockState(), level.getBlockState(BlockPos(0, 2, 0)))
        assertFalse(state.brokenByFlood.get(1))
        assertTrue(state.brokenByFlood.get(2))

        val secondFloodFlush = flushFloodWriteQueue(
            level = level,
            state = state,
            shipTransform = shipTransform,
            addCap = 1,
            setApplyingInternalUpdates = {},
            isFloodFluidType = { fluid -> floodCanonicalSource(fluid) == Fluids.WATER },
            isExteriorFloodSeedReady = { pos, _, _, _, _ -> pos.y == 3 },
        )

        assertEquals(1, secondFloodFlush.added)
        assertEquals(0, secondFloodFlush.remainingQueued)
        assertEquals(Blocks.WATER.defaultBlockState(), level.getBlockState(BlockPos(0, 1, 0)))
        assertEquals(Blocks.WATER.defaultBlockState(), level.getBlockState(BlockPos(0, 2, 0)))
        assertTrue(state.brokenByFlood.get(1))
        assertTrue(state.brokenByFlood.get(2))

        val toRemoveAll = BitSet()
        withAirPocketWorldQueriesDisabled {
            invokeDrainFloodedInteriorToOutsideAir(
                level = level,
                state = state,
                shipTransform = shipTransform,
                protectedInterior = bitSetOf(1),
                newPlanesOut = Int2DoubleOpenHashMap(),
                toRemoveAll = toRemoveAll,
            )
        }

        assertFalse(toRemoveAll.get(1))
        assertTrue(toRemoveAll.get(2))

        state.queuedFloodRemoves.or(toRemoveAll)
        val drainFlush = flushFloodWriteQueue(
            level = level,
            state = state,
            shipTransform = shipTransform,
            removeCap = 1,
            addCap = 0,
            setApplyingInternalUpdates = {},
            isFloodFluidType = { fluid -> floodCanonicalSource(fluid) == Fluids.WATER },
            isExteriorFloodSeedReady = { _, _, _, _, _ -> false },
        )

        assertEquals(1, drainFlush.removed)
        assertEquals(Blocks.WATER.defaultBlockState(), level.getBlockState(BlockPos(0, 1, 0)))
        assertEquals(Blocks.AIR.defaultBlockState(), level.getBlockState(BlockPos(0, 2, 0)))
        assertTrue(state.materializedWater.get(1))
        assertFalse(state.materializedWater.get(2))
        assertTrue(state.brokenByFlood.get(1))
        assertFalse(state.brokenByFlood.get(2))
    }

    private fun withAirPocketWorldQueriesDisabled(block: () -> Unit) {
        val previous = VSGameConfig.COMMON.enableAirPockets
        VSGameConfig.COMMON.enableAirPockets = false
        try {
            block()
        } finally {
            VSGameConfig.COMMON.enableAirPockets = previous
        }
    }
}
