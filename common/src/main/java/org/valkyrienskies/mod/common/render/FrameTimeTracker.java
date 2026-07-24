package org.valkyrienskies.mod.common.render;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight render-thread frame-time sampler. Benchmarks arm/disarm it
 * around a spawn event and report frame-time distribution (p50/p90/p99),
 * max-hitch, and total hitch time to catch user-visible freezes that
 * aren't captured by any server-side timer.
 *
 * <p>Off in production: {@link #isArmed()} is a volatile read of a
 * {@link AtomicLong} that returns 0 when unarmed. The render-frame hook
 * takes ~10ns per frame to update, essentially free at typical
 * frame rates.
 */
public final class FrameTimeTracker {

    /**
     * Max frames we record per armed window. Sized for ~5 minutes at 60 FPS.
     * Additional frames past this are counted toward totalFrames/max/hitch
     * stats but don't contribute to percentile calculation.
     */
    private static final int MAX_SAMPLES = 20_000;

    private static final AtomicLong ARMED_AT_NS = new AtomicLong(0L);
    private static volatile long lastFrameStartNs = 0L;
    private static volatile long maxFrameNs = 0L;
    private static volatile long totalHitchNs = 0L;
    private static volatile int hitchedFrames = 0;
    private static volatile int totalFrames = 0;
    private static volatile long hitchThresholdNs = 33_000_000L; // 30 FPS = 33ms
    private static final long[] sampleBufferNs = new long[MAX_SAMPLES];
    private static volatile int sampleCount = 0;
    // Snapshot of cumulative GC time across all collectors, updated per
    // frame boundary. When a big hitch fires, we compare now vs last to
    // see how much of the frame was GC pause. Delta > 50 ms ≈ GC.
    private static volatile long lastGcCountTotal = 0L;
    private static volatile long lastGcTimeMsTotal = 0L;

    private FrameTimeTracker() {}

    /**
     * Arms the tracker and resets counters. Subsequent calls to
     * {@link #onFrameBoundary()} will record frame durations until
     * {@link #disarm()} is called.
     */
    public static void arm() {
        ARMED_AT_NS.set(System.nanoTime());
        lastFrameStartNs = 0L;
        maxFrameNs = 0L;
        totalHitchNs = 0L;
        hitchedFrames = 0;
        totalFrames = 0;
        sampleCount = 0;
    }

    public static boolean isArmed() {
        return ARMED_AT_NS.get() != 0L;
    }

    /** Called by a render-thread mixin at the boundary of each frame. */
    public static void onFrameBoundary() {
        final long armedAt = ARMED_AT_NS.get();
        if (armedAt == 0L) return;
        final long now = System.nanoTime();
        final long last = lastFrameStartNs;
        lastFrameStartNs = now;
        if (last == 0L) {
            // first sample — capture initial GC totals so the first
            // genuine frame reports a correct delta, not a huge spike.
            lastGcCountTotal = gcCountTotal();
            lastGcTimeMsTotal = gcTimeMsTotal();
            return;
        }
        final long frameNs = now - last;
        totalFrames++;
        if (frameNs > maxFrameNs) maxFrameNs = frameNs;
        if (frameNs > hitchThresholdNs) {
            hitchedFrames++;
            totalHitchNs += frameNs - hitchThresholdNs;
        }
        // Log any >500 ms frame with a timestamp relative to arming and
        // a GC delta so we can tell whether the hitch was GC pause or
        // actual code work.
        if (frameNs > 500_000_000L) {
            final long sinceArmMs = (last - armedAt) / 1_000_000L;
            final long gcCountNow = gcCountTotal();
            final long gcTimeMsNow = gcTimeMsTotal();
            final long gcCountDelta = gcCountNow - lastGcCountTotal;
            final long gcTimeDelta = gcTimeMsNow - lastGcTimeMsTotal;
            lastGcCountTotal = gcCountNow;
            lastGcTimeMsTotal = gcTimeMsNow;
            org.slf4j.LoggerFactory.getLogger("FrameTimeTracker").info(
                "[big-hitch] +{}ms into window: frame took {}ms "
                    + "(GC: +{}ms over {} collections)",
                sinceArmMs, frameNs / 1_000_000L, gcTimeDelta, gcCountDelta);
        } else {
            // Update GC counters periodically so the next big-hitch
            // report has an accurate delta window.
            lastGcCountTotal = gcCountTotal();
            lastGcTimeMsTotal = gcTimeMsTotal();
        }
        final int idx = sampleCount;
        if (idx < MAX_SAMPLES) {
            sampleBufferNs[idx] = frameNs;
            sampleCount = idx + 1;
        }
    }

    public static Result disarm() {
        final long armedAt = ARMED_AT_NS.getAndSet(0L);
        if (armedAt == 0L) return new Result(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        final long windowMs = (System.nanoTime() - armedAt) / 1_000_000L;
        final int n = sampleCount;
        long p50Ms = 0, p75Ms = 0, p90Ms = 0, p99Ms = 0, avgMs = 0;
        // Bucket histogram. Bimodal distributions (e.g. smooth render
        // frames interleaved with heavy tick frames) hide in percentile
        // numbers — p50 stays low if most frames are smooth, even when
        // 30% of frames are chop. Buckets show the real picture.
        int bucketUnder16 = 0;   // 60+ FPS frames
        int bucket16to33 = 0;    // 30-60 FPS
        int bucket33to66 = 0;    // 15-30 FPS (noticeable chop)
        int bucket66to150 = 0;   // 6-15 FPS (visible stutter)
        int bucketOver150 = 0;   // hitch territory
        if (n > 0) {
            final long[] sorted = Arrays.copyOf(sampleBufferNs, n);
            Arrays.sort(sorted);
            p50Ms = sorted[(int) (n * 0.50)] / 1_000_000L;
            p75Ms = sorted[(int) (n * 0.75)] / 1_000_000L;
            p90Ms = sorted[(int) (n * 0.90)] / 1_000_000L;
            p99Ms = sorted[Math.min(n - 1, (int) (n * 0.99))] / 1_000_000L;
            long sum = 0L;
            for (int i = 0; i < n; i++) {
                sum += sorted[i];
                final long ms = sorted[i] / 1_000_000L;
                if (ms < 16) bucketUnder16++;
                else if (ms < 33) bucket16to33++;
                else if (ms < 66) bucket33to66++;
                else if (ms < 150) bucket66to150++;
                else bucketOver150++;
            }
            avgMs = (sum / n) / 1_000_000L;
        }
        return new Result(totalFrames, hitchedFrames,
            maxFrameNs / 1_000_000L,
            totalHitchNs / 1_000_000L,
            windowMs,
            p50Ms, p75Ms, p90Ms, p99Ms, avgMs,
            bucketUnder16, bucket16to33, bucket33to66, bucket66to150, bucketOver150);
    }

    private static long gcCountTotal() {
        long total = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            final long c = b.getCollectionCount();
            if (c >= 0) total += c;
        }
        return total;
    }

    private static long gcTimeMsTotal() {
        long total = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            final long t = b.getCollectionTime();
            if (t >= 0) total += t;
        }
        return total;
    }

    public record Result(
        int totalFrames,
        int hitchedFrames,
        long maxFrameMs,
        long totalHitchMs,
        long windowMs,
        long p50Ms,
        long p75Ms,
        long p90Ms,
        long p99Ms,
        long avgMs,
        int bucketUnder16,
        int bucket16to33,
        int bucket33to66,
        int bucket66to150,
        int bucketOver150
    ) {}
}
