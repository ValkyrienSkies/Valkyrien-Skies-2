package org.valkyrienskies.mod.common.fluid.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Bounded off-thread pool for client fluid render jobs.
 *
 * <p>Submission is best-effort: once {@link #MAX_PENDING_JOBS} work items are in flight
 * {@link #trySubmit} returns {@code null} rather than queueing, so a stalled pool degrades into
 * skipped rebuilds instead of unbounded memory growth. Callers are expected to keep using their
 * previous result when submission is refused.</p>
 */
public final class ShipFluidAsyncRuntime {

    private static final int MAX_PENDING_JOBS = 64;

    private static final AtomicInteger PENDING_JOBS = new AtomicInteger(0);
    private static final AtomicLong THREAD_COUNTER = new AtomicLong(0);

    private static final AtomicLong SUBMITTED = new AtomicLong(0);
    private static final AtomicLong COMPLETED = new AtomicLong(0);
    private static final AtomicLong FAILED = new AtomicLong(0);
    private static final AtomicLong DISCARDED = new AtomicLong(0);

    private ShipFluidAsyncRuntime() {
    }

    private static final class Holder {
        private static final ExecutorService EXECUTOR = create();

        private static ExecutorService create() {
            final int processors = Runtime.getRuntime().availableProcessors();
            final int threads = Math.max(1, Math.min(2, processors - 1));
            return Executors.newFixedThreadPool(threads, runnable -> {
                final Thread thread =
                    new Thread(runnable, "VSFluidRender-Async-" + THREAD_COUNTER.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    public static ExecutorService executor() {
        return Holder.EXECUTOR;
    }

    private static boolean tryAcquirePendingSlot() {
        while (true) {
            final int current = PENDING_JOBS.get();
            if (current >= MAX_PENDING_JOBS) return false;
            if (PENDING_JOBS.compareAndSet(current, current + 1)) return true;
        }
    }

    public static int pendingJobCount() {
        return PENDING_JOBS.get();
    }

    public static int maxPendingJobs() {
        return MAX_PENDING_JOBS;
    }

    public static void noteDiscard() {
        DISCARDED.incrementAndGet();
    }

    public static long submitted() {
        return SUBMITTED.get();
    }

    public static long completed() {
        return COMPLETED.get();
    }

    public static long failed() {
        return FAILED.get();
    }

    public static long discarded() {
        return DISCARDED.get();
    }

    /** Returns {@code null} when the pool is saturated; the caller should keep its previous result. */
    public static <T> @Nullable CompletableFuture<T> trySubmit(final Supplier<T> task) {
        if (!tryAcquirePendingSlot()) return null;

        SUBMITTED.incrementAndGet();

        final CompletableFuture<T> sourceFuture = CompletableFuture.supplyAsync(task, executor());

        // Slot accounting stays bound to the source task, not the view handed back to callers.
        sourceFuture.whenComplete((result, throwable) -> {
            PENDING_JOBS.decrementAndGet();
            if (throwable == null) {
                COMPLETED.incrementAndGet();
            } else {
                FAILED.incrementAndGet();
            }
        });

        // A distinct stage, so caller-side cancellation cannot suppress the accounting callback.
        return sourceFuture.thenApply(result -> result);
    }
}
