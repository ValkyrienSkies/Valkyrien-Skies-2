package org.valkyrienskies.valkyrienair.feature.ship_water_pockets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.jvm.functions.Function0

class ShipWaterPocketAsyncRuntimeTest {
    private val runtimeClass =
        Class.forName("org.valkyrienskies.mod.common.air_pockets.ShipPocketAsyncRuntime")
    private val subsystemClass =
        Class.forName("org.valkyrienskies.mod.common.air_pockets.ShipPocketAsyncSubsystem")
    private val trySubmitMethod =
        runtimeClass.getMethod("trySubmit", subsystemClass, Function0::class.java)

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
        val taskFn = object : Function0<Any?> {
            override fun invoke(): Any? = task()
        }
        @Suppress("UNCHECKED_CAST")
        return trySubmitMethod.invoke(null, subsystem(subsystemName), taskFn) as CompletableFuture<Any?>?
    }

    private fun pendingJobCount(): Int {
        return runtimeClass.getMethod("pendingJobCount").invoke(null) as Int
    }

    private fun maxPendingJobs(): Int {
        return runtimeClass.getMethod("maxPendingJobs").invoke(null) as Int
    }

    private fun subsystem(name: String): Any {
        return subsystemClass.enumConstants.first { (it as Enum<*>).name == name }
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
