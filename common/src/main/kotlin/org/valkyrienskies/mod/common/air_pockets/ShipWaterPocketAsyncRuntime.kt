package org.valkyrienskies.mod.common.air_pockets

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

internal enum class ShipPocketAsyncSubsystem {
    GEOMETRY,
    WATER_SOLVER,
    CLIENT_CULL,
}

internal object ShipPocketAsyncRuntime {
    private const val MAX_PENDING_JOBS = 64

    private val pendingJobs = AtomicInteger(0)
    private val threadCounter = AtomicLong(0)

    private val submittedBySubsystem =
        Array(ShipPocketAsyncSubsystem.entries.size) { AtomicLong(0) }
    private val completedBySubsystem =
        Array(ShipPocketAsyncSubsystem.entries.size) { AtomicLong(0) }
    private val failedBySubsystem =
        Array(ShipPocketAsyncSubsystem.entries.size) { AtomicLong(0) }
    private val discardedBySubsystem =
        Array(ShipPocketAsyncSubsystem.entries.size) { AtomicLong(0) }

    val executor: ExecutorService by lazy {
        val processors = Runtime.getRuntime().availableProcessors()
        val threads = maxOf(1, minOf(2, processors - 1))
        Executors.newFixedThreadPool(threads) { runnable ->
            Thread(runnable, "ValkyrienAir-Async-${threadCounter.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
    }

    private fun subsystemIdx(subsystem: ShipPocketAsyncSubsystem): Int = subsystem.ordinal

    private fun tryAcquirePendingSlot(): Boolean {
        while (true) {
            val current = pendingJobs.get()
            if (current >= MAX_PENDING_JOBS) return false
            if (pendingJobs.compareAndSet(current, current + 1)) return true
        }
    }

    @JvmStatic
    fun pendingJobCount(): Int = pendingJobs.get()

    @JvmStatic
    fun maxPendingJobs(): Int = MAX_PENDING_JOBS

    @JvmStatic
    fun noteDiscard(subsystem: ShipPocketAsyncSubsystem) {
        discardedBySubsystem[subsystemIdx(subsystem)].incrementAndGet()
    }

    @JvmStatic
    fun submitted(subsystem: ShipPocketAsyncSubsystem): Long =
        submittedBySubsystem[subsystemIdx(subsystem)].get()

    @JvmStatic
    fun completed(subsystem: ShipPocketAsyncSubsystem): Long =
        completedBySubsystem[subsystemIdx(subsystem)].get()

    @JvmStatic
    fun failed(subsystem: ShipPocketAsyncSubsystem): Long =
        failedBySubsystem[subsystemIdx(subsystem)].get()

    @JvmStatic
    fun discarded(subsystem: ShipPocketAsyncSubsystem): Long =
        discardedBySubsystem[subsystemIdx(subsystem)].get()

    @JvmStatic
    fun <T> trySubmit(
        subsystem: ShipPocketAsyncSubsystem,
        task: () -> T,
    ): CompletableFuture<T>? {
        if (!tryAcquirePendingSlot()) return null

        val subsystemIndex = subsystemIdx(subsystem)
        submittedBySubsystem[subsystemIndex].incrementAndGet()

        val sourceFuture = CompletableFuture.supplyAsync(
            {
                task()
            },
            executor,
        )

        // Keep slot accounting bound to the source task, not the cancelable view we return to callers.
        sourceFuture.whenComplete { _, throwable ->
            pendingJobs.decrementAndGet()
            if (throwable == null) {
                completedBySubsystem[subsystemIndex].incrementAndGet()
            } else {
                failedBySubsystem[subsystemIndex].incrementAndGet()
            }
        }

        // Return a distinct stage so caller-side cancellation cannot suppress internal accounting callbacks.
        return sourceFuture.thenApply { it }
    }

    @JvmStatic
    fun <T> trySubmitJava(
        subsystem: ShipPocketAsyncSubsystem,
        supplier: Supplier<T>,
    ): CompletableFuture<T>? {
        return trySubmit(subsystem = subsystem, task = { supplier.get() })
    }
}
