package org.valkyrienskies.mod.feature.ship_water_pockets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.ShipPocketAsyncRuntime
import org.valkyrienskies.mod.common.air_pockets.ShipPocketAsyncSubsystem
import java.util.concurrent.CompletableFuture

class ShipWaterPocketAsyncRuntimeTest {

    @Test
    fun cancellingReturnedFutureDoesNotLeakPendingSlot() {
        val baseline = pendingJobCount()
        assertTrue(waitForPendingCount(baseline, timeoutMs = 5_000))

        val submitted = trySubmit("WATER_SOLVER") {
            Thread.sleep(60)
            1
        }
        assertNotNull(submitted)
        submitted!!.cancel(true)

        assertTrue(
            waitForPendingCount(baseline, timeoutMs = 5_000),
            "Pending slots failed to recover after cancellation (baseline=$baseline, current=${pendingJobCount()})",
        )
    }

    @Test
    fun cancellationChurnStillAllowsFreshSubmission() {
        val baseline = pendingJobCount()
        assertTrue(waitForPendingCount(baseline, timeoutMs = 5_000))

        val churnIterations = maxPendingJobs() + 8
        for (i in 0 until churnIterations) {
            val submitted = trySubmit("CLIENT_CULL") {
                Thread.sleep(5)
                i
            } ?: fail("Submission rejected during cancellation churn at iteration=$i pending=${pendingJobCount()}")
            submitted.cancel(true)
            Thread.sleep(10)
        }

        assertTrue(waitForPendingCount(baseline, timeoutMs = 5_000))

        val fresh = trySubmit("GEOMETRY") { 42 }
            ?: fail("Fresh submission was rejected after churn")
        assertEquals(42, fresh.join())
        assertTrue(waitForPendingCount(baseline, timeoutMs = 5_000))
    }

    private fun trySubmit(subsystemName: String, task: () -> Any?): CompletableFuture<Any?>? {
        return ShipPocketAsyncRuntime.trySubmit(subsystem(subsystemName), task)
    }

    private fun pendingJobCount(): Int {
        return ShipPocketAsyncRuntime.pendingJobCount()
    }

    private fun maxPendingJobs(): Int {
        return ShipPocketAsyncRuntime.maxPendingJobs()
    }

    private fun subsystem(name: String): ShipPocketAsyncSubsystem {
        return ShipPocketAsyncSubsystem.valueOf(name)
    }

    private fun waitForPendingCount(expected: Int, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (pendingJobCount() == expected) return true
            Thread.sleep(5)
        }
        return pendingJobCount() == expected
    }
}
