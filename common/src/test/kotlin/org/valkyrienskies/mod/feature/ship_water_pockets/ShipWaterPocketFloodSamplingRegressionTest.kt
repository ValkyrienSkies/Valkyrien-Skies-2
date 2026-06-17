package org.valkyrienskies.mod.feature.ship_water_pockets

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import java.util.BitSet
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.BuoyancyMetrics
import org.valkyrienskies.mod.common.air_pockets.computeWaterSolveAsync
import org.valkyrienskies.mod.common.config.VSGameConfig

class ShipWaterPocketFloodSamplingRegressionTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            ShipWaterPocketTestSupport.bootstrapMinecraft()
        }
    }

    private val shipTransform = IdentityShipTransform()

    @Test
    fun stackedFluidColumnSurfaceEstimateMatchesTopFluidSection() {
        val states = mutableMapOf(
            blockKey(0, 0, 0) to Blocks.WATER.defaultBlockState(),
            blockKey(0, 1, 0) to Blocks.WATER.defaultBlockState(),
            blockKey(0, 2, 0) to Fluids.FLOWING_WATER.defaultFluidState().createLegacyBlock(),
        )
        val level = createTrackingLevel(states)

        val topFluidHeight = states.getValue(blockKey(0, 2, 0)).fluidState.ownHeight.toDouble()

        val surfaceY = invokeEstimateExteriorFluidSurfaceYAtShipPoint(
            level = level,
            shipTransform = shipTransform,
            shipX = 0.5,
            shipY = 0.5,
            shipZ = 0.5,
            sampleFluid = Fluids.WATER,
        )

        assertNotNull(surfaceY)
        assertEquals(
            2.0 + topFluidHeight,
            surfaceY!!,
            1.0e-9,
        )
    }

    @Test
    fun preparedWaterSolveMatchesLiveSolveForFloodIngress() {
        val states = mutableMapOf(
            blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState(),
            blockKey(0, 1, 0) to Blocks.WATER.defaultBlockState(),
        )
        val level = createTrackingLevel(states, gameTime = 0L)
        val snapshotState = ingressPocketState()
        val snapshot = invokeCaptureWaterSolveSnapshot(level, snapshotState, shipTransform)

        assertNotNull(snapshot)
        assertFalse(snapshot!!.openingFaceSamples.isEmpty())

        val prepared = computeWaterSolveAsync(snapshot)

        val liveState = ingressPocketState()
        val liveReachable = invokeComputeWaterReachable(level, liveState, shipTransform)

        assertEquals(liveReachable, prepared.waterReachable)
        assertEquals(liveState.unreachableVoid, prepared.unreachableVoid)
        assertEquals(liveState.floodFluid, prepared.floodFluid)
        assertBuoyancyEquals(liveState.buoyancy, prepared.buoyancy)
    }

    @Test
    fun partiallySubmergedSideOpeningStillCountsAsFloodIngress() {
        val states = mutableMapOf(
            blockKey(0, 0, 0) to Blocks.AIR.defaultBlockState(),
            blockKey(1, 0, 0) to Fluids.FLOWING_WATER.defaultFluidState().createLegacyBlock(),
        )
        val level = createTrackingLevel(states, gameTime = 0L)
        val state = horizontalPocketStateX(
            sizeX = 2,
            simulationIndices = intArrayOf(0),
            exteriorIndices = intArrayOf(1),
        ).apply {
            exterior = bitSetOf(1)
            floodFluid = Fluids.WATER
        }
        val snapshot = invokeCaptureWaterSolveSnapshot(level, state, shipTransform)
        val openingSample = snapshot!!.openingFaceSamples.get(1L)
        assertNotNull(openingSample)
        assertEquals(Fluids.WATER, openingSample!!.canonicalFluid)
        assertTrue(openingSample.coverageRatio > 0.0)

        val reachable = invokeComputeWaterReachable(level, state, shipTransform)

        assertTrue(reachable.get(0))
    }

    @Test
    fun drainPassProducesStableResultAcrossRepeatedRuns() {
        val first = runDrainPass()
        val second = runDrainPass()

        assertEquals(first.first, second.first)
        assertEquals(first.second, second.second)
        assertTrue(first.first.get(2))
        assertFalse(first.first.get(1))
        assertTrue(first.second.get(2))
    }

    private fun ingressPocketState() = verticalPocketState(
        sizeY = 2,
        simulationIndices = intArrayOf(0),
        exteriorIndices = intArrayOf(1),
    ).apply {
        exterior = bitSetOf(1)
        floodFluid = Fluids.WATER
    }

    private fun runDrainPass(): Pair<BitSet, BitSet> {
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
        return toRemoveAll to drainSuppressedOut
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

    private fun assertBuoyancyEquals(expected: BuoyancyMetrics, actual: BuoyancyMetrics) {
        assertEquals(expected.submergedAirVolume, actual.submergedAirVolume, 1.0e-9)
        assertEquals(expected.submergedAirSumX, actual.submergedAirSumX, 1.0e-9)
        assertEquals(expected.submergedAirSumY, actual.submergedAirSumY, 1.0e-9)
        assertEquals(expected.submergedAirSumZ, actual.submergedAirSumZ, 1.0e-9)
    }
}
