package org.valkyrienskies.mod.air_pockets.client;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the camera origin Embeddium/Sodium chunk rendering uses for camera-relative chunk coordinates.
 *
 * <p>Embeddium computes chunk-space positions relative to {@code (intPos + fracPos)} where {@code fracPos} is reduced
 * precision to avoid seams at region boundaries.
 */
public final class EmbeddiumChunkRenderContext {

    private EmbeddiumChunkRenderContext() {}

    // Matches Embeddium's CameraTransform PRECISION_MODIFIER (RenderRegion.REGION_WIDTH * 16 == 8 * 16 == 128).
    private static final float PRECISION_MODIFIER = 128.0f;
    private static final int MAX_STACK_DEPTH = 8;

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private static final class State {
        private boolean active;
        private double chunkCamX;
        private double chunkCamY;
        private double chunkCamZ;
        private double rawX;
        private double rawY;
        private double rawZ;
        private int depth;

        private final boolean[] activeStack = new boolean[MAX_STACK_DEPTH];
        private final double[] chunkCamXStack = new double[MAX_STACK_DEPTH];
        private final double[] chunkCamYStack = new double[MAX_STACK_DEPTH];
        private final double[] chunkCamZStack = new double[MAX_STACK_DEPTH];
        private final double[] rawXStack = new double[MAX_STACK_DEPTH];
        private final double[] rawYStack = new double[MAX_STACK_DEPTH];
        private final double[] rawZStack = new double[MAX_STACK_DEPTH];
    }

    public static void pushChunkCameraWorldPos(final double rawX, final double rawY, final double rawZ) {
        final State s = STATE.get();
        if (s.depth < MAX_STACK_DEPTH) {
            final int idx = s.depth;
            s.activeStack[idx] = s.active;
            s.chunkCamXStack[idx] = s.chunkCamX;
            s.chunkCamYStack[idx] = s.chunkCamY;
            s.chunkCamZStack[idx] = s.chunkCamZ;
            s.rawXStack[idx] = s.rawX;
            s.rawYStack[idx] = s.rawY;
            s.rawZStack[idx] = s.rawZ;
        }
        s.depth++;

        s.active = true;
        s.rawX = rawX;
        s.rawY = rawY;
        s.rawZ = rawZ;
        s.chunkCamX = computeChunkCameraOrigin(rawX);
        s.chunkCamY = computeChunkCameraOrigin(rawY);
        s.chunkCamZ = computeChunkCameraOrigin(rawZ);
    }

    public static void pop() {
        final State s = STATE.get();
        s.depth--;
        if (s.depth <= 0) {
            s.depth = 0;
            s.active = false;
            s.chunkCamX = 0.0;
            s.chunkCamY = 0.0;
            s.chunkCamZ = 0.0;
            s.rawX = 0.0;
            s.rawY = 0.0;
            s.rawZ = 0.0;
            return;
        }

        final int idx = Math.min(s.depth - 1, MAX_STACK_DEPTH - 1);
        s.active = s.activeStack[idx];
        s.chunkCamX = s.chunkCamXStack[idx];
        s.chunkCamY = s.chunkCamYStack[idx];
        s.chunkCamZ = s.chunkCamZStack[idx];
        s.rawX = s.rawXStack[idx];
        s.rawY = s.rawYStack[idx];
        s.rawZ = s.rawZStack[idx];
    }

    public static void clear() {
        final State s = STATE.get();
        s.depth = 0;
        s.active = false;
        s.chunkCamX = 0.0;
        s.chunkCamY = 0.0;
        s.chunkCamZ = 0.0;
        s.rawX = 0.0;
        s.rawY = 0.0;
        s.rawZ = 0.0;
    }

    public static @Nullable Vec3 getChunkCameraWorldPosOrNull() {
        final State s = STATE.get();
        if (!s.active) return null;
        return new Vec3(s.chunkCamX, s.chunkCamY, s.chunkCamZ);
    }

    public static double getChunkCamXOr(final double fallback) {
        final State s = STATE.get();
        return s.active ? s.chunkCamX : fallback;
    }

    public static double getChunkCamYOr(final double fallback) {
        final State s = STATE.get();
        return s.active ? s.chunkCamY : fallback;
    }

    public static double getChunkCamZOr(final double fallback) {
        final State s = STATE.get();
        return s.active ? s.chunkCamZ : fallback;
    }

    private static double computeChunkCameraOrigin(final double value) {
        final int integral = (int) value;
        final float fullPrecision = (float) (value - (double) integral);
        final float modifier = Math.copySign(PRECISION_MODIFIER, fullPrecision);
        final float reduced = (fullPrecision + modifier) - modifier;
        return (double) integral + (double) reduced;
    }
}
