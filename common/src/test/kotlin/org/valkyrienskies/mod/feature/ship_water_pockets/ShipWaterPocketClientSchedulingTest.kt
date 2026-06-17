package org.valkyrienskies.mod.feature.ship_water_pockets

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.ShipPocketState
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager.ClientWaterSolveSkipReason

class ShipWaterPocketClientSchedulingTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun volumeTiersMapToCadenceBuckets() {
        assertEquals(4L, ShipWaterPocketManager.clientWaterSolveCadenceTicksForVolume(32_768L))
        assertEquals(6L, ShipWaterPocketManager.clientWaterSolveCadenceTicksForVolume(32_769L))
        assertEquals(6L, ShipWaterPocketManager.clientWaterSolveCadenceTicksForVolume(131_072L))
        assertEquals(10L, ShipWaterPocketManager.clientWaterSolveCadenceTicksForVolume(131_073L))
        assertEquals(10L, ShipWaterPocketManager.clientWaterSolveCadenceTicksForVolume(524_288L))
        assertEquals(16L, ShipWaterPocketManager.clientWaterSolveCadenceTicksForVolume(524_289L))
    }

    @Test
    fun largeClientShipsUseNearbyChunkQueryRadius() {
        assertEquals(null, ShipWaterPocketManager.clientWaterSolveNearbyQueryChunkRadiusForVolume(131_072L))
        assertEquals(12, ShipWaterPocketManager.clientWaterSolveNearbyQueryChunkRadiusForVolume(131_073L))
        assertEquals(12, ShipWaterPocketManager.clientWaterSolveNearbyQueryChunkRadiusForVolume(524_288L))
        assertEquals(8, ShipWaterPocketManager.clientWaterSolveNearbyQueryChunkRadiusForVolume(524_289L))
    }

    @Test
    fun geometryChangeBypassesCadenceThrottle() {
        val state = ShipPocketState(
            lastClientWaterSolveApplyTick = 40L,
            lastClientWaterSolveAppliedTransformKey = 7L,
            lastClientWaterSolveSubmittedTransformKey = 7L,
            lastClientDemandTick = 50L,
            lastWaterSolveSubmitTick = 50L,
        )

        val decision = ShipWaterPocketManager.decideClientWaterSolveSubmission(
            state = state,
            volume = 300_000L,
            geometryApplied = true,
            currentTransformKey = 7L,
            nowTick = 51L,
        )

        assertTrue(decision.shouldSubmit)
        assertFalse(decision.forcedRefresh)
        assertEquals(null, decision.skipReason)
    }

    @Test
    fun unchangedTransformSkipsSubmission() {
        val state = ShipPocketState(
            lastClientWaterSolveApplyTick = 40L,
            lastClientWaterSolveAppliedTransformKey = 11L,
            lastClientWaterSolveSubmittedTransformKey = 11L,
            lastClientDemandTick = 50L,
            lastWaterSolveSubmitTick = 50L,
        )

        val decision = ShipWaterPocketManager.decideClientWaterSolveSubmission(
            state = state,
            volume = 20_000L,
            geometryApplied = false,
            currentTransformKey = 11L,
            nowTick = 51L,
        )

        assertFalse(decision.shouldSubmit)
        assertFalse(decision.forcedRefresh)
        assertEquals(ClientWaterSolveSkipReason.UNCHANGED_TRANSFORM, decision.skipReason)
    }

    @Test
    fun cadenceSkipsChangedTransformUntilTierDelayPasses() {
        val state = ShipPocketState(
            lastClientWaterSolveApplyTick = 40L,
            lastClientWaterSolveAppliedTransformKey = 11L,
            lastClientWaterSolveSubmittedTransformKey = 11L,
            lastClientDemandTick = 60L,
            lastWaterSolveSubmitTick = 60L,
        )

        val decision = ShipWaterPocketManager.decideClientWaterSolveSubmission(
            state = state,
            volume = 300_000L,
            geometryApplied = false,
            currentTransformKey = 12L,
            nowTick = 62L,
        )

        assertFalse(decision.shouldSubmit)
        assertFalse(decision.forcedRefresh)
        assertEquals(ClientWaterSolveSkipReason.CADENCE, decision.skipReason)
        assertEquals(10L, decision.cadenceTicks)
    }

    @Test
    fun undemandedShipKeepsLastClientResult() {
        val state = ShipPocketState(
            lastClientWaterSolveApplyTick = 40L,
            lastClientWaterSolveAppliedTransformKey = 11L,
            lastClientWaterSolveSubmittedTransformKey = 11L,
            lastClientDemandTick = 29L,
            lastWaterSolveSubmitTick = 40L,
        )

        val decision = ShipWaterPocketManager.decideClientWaterSolveSubmission(
            state = state,
            volume = 20_000L,
            geometryApplied = false,
            currentTransformKey = 12L,
            nowTick = 40L,
        )

        assertFalse(decision.shouldSubmit)
        assertFalse(decision.forcedRefresh)
        assertEquals(ClientWaterSolveSkipReason.NOT_DEMANDED_RECENTLY, decision.skipReason)
    }

    @Test
    fun staleUndemandedChangedTransformAllowsForcedRefresh() {
        val state = ShipPocketState(
            lastClientWaterSolveApplyTick = 10L,
            lastClientWaterSolveAppliedTransformKey = 21L,
            lastClientWaterSolveSubmittedTransformKey = 21L,
            lastClientDemandTick = 0L,
            lastWaterSolveSubmitTick = 10L,
        )

        val decision = ShipWaterPocketManager.decideClientWaterSolveSubmission(
            state = state,
            volume = 900_000L,
            geometryApplied = false,
            currentTransformKey = 22L,
            nowTick = 51L,
        )

        assertTrue(decision.shouldSubmit)
        assertTrue(decision.forcedRefresh)
        assertEquals(null, decision.skipReason)
        assertEquals(16L, decision.cadenceTicks)
    }
}
