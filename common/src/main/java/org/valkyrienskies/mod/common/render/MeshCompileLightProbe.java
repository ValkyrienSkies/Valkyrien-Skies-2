package org.valkyrienskies.mod.common.render;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * Test-only probe that captures per-face light reads during
 * {@code LevelRenderer.getLightColor(...)} calls. Used by the vsgametest
 * ship-lighting tests to verify compile-time light values against the
 * engine's live state — catching bugs where a freshly-compiled mesh
 * bakes stale light even though the engine is correct.
 *
 * <p>Off in production: the mixin guard checks {@link #isArmed()}, a
 * near-zero AtomicReference null read when unarmed.
 *
 * <p>The armed state is GLOBAL (not ThreadLocal) on purpose: section
 * compile runs on the render thread for the sync path
 * ({@code SectionRenderDispatcher#rebuildSectionSync}) AND on worker
 * threads for async recompiles via {@code compileSections}. Drift tests
 * that want to observe the actual scheduled compile cycle (e.g., the
 * first sync compile on a newly-visible ship section, then a subsequent
 * async recompile triggered by a setDirty) need captures from both
 * thread types. A ThreadLocal probe would only see one or the other.
 *
 * <p>Tradeoff: armed across tests stays armed across worker pool idle
 * periods, so stray compiles from other parts of the game during the
 * armed window can also capture. Scope filtering by section
 * bounding box keeps that noise out of the assertion space.
 */
public final class MeshCompileLightProbe {

    public record Capture(long tickTime, BlockPos pos, int packedLight) {}

    /** A single raw read from {@code LightEngine.getLightValue(BlockPos)}. */
    public record LayerRead(long tickTime, BlockPos pos, int layer /* 0=sky, 1=block */, int value) {}

    private static final AtomicReference<ArmedScope> ARMED = new AtomicReference<>();

    private static final class ArmedScope {
        final int minX, minY, minZ, maxX, maxY, maxZ;
        final List<Capture> captures;
        final List<LayerRead> layerReads;

        ArmedScope(SectionPos sectionPos) {
            this.minX = sectionPos.minBlockX() - 1;
            this.minY = sectionPos.minBlockY() - 1;
            this.minZ = sectionPos.minBlockZ() - 1;
            this.maxX = sectionPos.maxBlockX() + 1;
            this.maxY = sectionPos.maxBlockY() + 1;
            this.maxZ = sectionPos.maxBlockZ() + 1;
            this.captures = java.util.Collections.synchronizedList(new ArrayList<>());
            this.layerReads = java.util.Collections.synchronizedList(new ArrayList<>());
        }

        boolean contains(BlockPos p) {
            final int x = p.getX();
            final int y = p.getY();
            final int z = p.getZ();
            return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
        }
    }

    private MeshCompileLightProbe() {}

    public static void arm(SectionPos sectionPos) {
        ARMED.set(new ArmedScope(sectionPos));
    }

    public static List<Capture> disarmAndTake() {
        final ArmedScope s = ARMED.getAndSet(null);
        if (s == null) return List.of();
        synchronized (s.captures) {
            return new ArrayList<>(s.captures);
        }
    }

    /**
     * Disarms and returns BOTH capture channels:
     * {@code getLightColor()} packed reads (mesh face path) and
     * {@code LightEngine.getLightValue()} raw layer reads (AO-corner path).
     */
    public static ArmedSnapshot disarmAndTakeAll() {
        final ArmedScope s = ARMED.getAndSet(null);
        if (s == null) return new ArmedSnapshot(List.of(), List.of());
        final List<Capture> caps;
        final List<LayerRead> reads;
        synchronized (s.captures) { caps = new ArrayList<>(s.captures); }
        synchronized (s.layerReads) { reads = new ArrayList<>(s.layerReads); }
        return new ArmedSnapshot(caps, reads);
    }

    public record ArmedSnapshot(List<Capture> captures, List<LayerRead> layerReads) {}

    public static boolean isArmed() {
        return ARMED.get() != null;
    }

    /** Called from the mixin. Filters by section bounds for cheap discrimination. */
    public static void capture(final BlockPos pos, final int packedLight) {
        final ArmedScope s = ARMED.get();
        if (s == null) return;
        if (!s.contains(pos)) return;
        s.captures.add(new Capture(System.nanoTime(), pos.immutable(), packedLight));
    }

    /**
     * Called from the {@code LightEngine.getLightValue} mixin. Catches the
     * AO-corner sampling path that {@link #capture} misses — smooth lighting
     * reads {@code BlockAndTintGetter.getBrightness(layer, cornerPos)} for
     * each vertex corner directly, bypassing {@code LevelRenderer.getLightColor}.
     *
     * @param layer 0 for sky, 1 for block
     */
    public static void captureLayerRead(final BlockPos pos, final int layer, final int value) {
        final ArmedScope s = ARMED.get();
        if (s == null) return;
        if (!s.contains(pos)) return;
        s.layerReads.add(new LayerRead(System.nanoTime(), pos.immutable(), layer, value));
    }
}
