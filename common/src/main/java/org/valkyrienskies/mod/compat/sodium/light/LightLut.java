package org.valkyrienskies.mod.compat.sodium.light;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.core.SectionPos;

/**
 * 3-level (Y -> X -> Z) coordinate-span lookup table for mapping section
 * coordinates to indices into a flat sections buffer.
 *
 * <p>Mirrors the layout used by Flywheel's {@code light_lut.glsl}, so the
 * shader-side traversal of the LUT is identical: each level stores a base
 * coordinate, a span size, then one entry per coordinate in the span. Entries
 * pointing to nothing are stored as 0; otherwise they hold the (1-based) index
 * of the next layer / section.
 */
public final class LightLut {
    private final Layer<Layer<IntLayer>> indices = new Layer<>();

    public void add(long sectionPos, int index) {
        int x = SectionPos.x(sectionPos);
        int y = SectionPos.y(sectionPos);
        int z = SectionPos.z(sectionPos);

        Layer<IntLayer> layerX = indices.computeIfAbsent(y, Layer::new);
        IntLayer layerZ = layerX.computeIfAbsent(x, IntLayer::new);
        layerZ.set(z, index + 1);
    }

    public void remove(long sectionPos) {
        int x = SectionPos.x(sectionPos);
        int y = SectionPos.y(sectionPos);
        int z = SectionPos.z(sectionPos);

        Layer<IntLayer> layerX = indices.get(y);
        if (layerX == null) return;
        IntLayer layerZ = layerX.get(x);
        if (layerZ == null) return;
        layerZ.clear(z);
    }

    /** Flatten the LUT into the caller-provided list, reusing its backing storage. */
    public void flattenInto(IntArrayList out) {
        out.clear();
        indices.fillLut(out, (xLayer, lut2) ->
                xLayer.fillLut(lut2, IntLayer::fillLut));
    }

    public int[] flatten() {
        IntArrayList out = new IntArrayList();
        flattenInto(out);
        return out.toIntArray();
    }

    @FunctionalInterface
    private interface BiConsumer<T> {
        void accept(T t, IntArrayList lut);
    }

    private interface Supplier<T> {
        T get();
    }

    private static final class Layer<T> {
        private boolean hasBase = false;
        private int base = 0;
        private Object[] next = new Object[0];

        @SuppressWarnings("unchecked")
        T get(int i) {
            if (!hasBase) return null;
            int off = i - base;
            if (off < 0 || off >= next.length) return null;
            return (T) next[off];
        }

        @SuppressWarnings("unchecked")
        T computeIfAbsent(int i, Supplier<T> s) {
            if (!hasBase) {
                base = i;
                hasBase = true;
            }
            if (i < base) rebase(i);
            int off = i - base;
            if (off >= next.length) resize(off + 1);
            Object v = next[off];
            if (v == null) {
                v = s.get();
                next[off] = v;
            }
            return (T) v;
        }

        @SuppressWarnings("unchecked")
        void fillLut(IntArrayList lut, BiConsumer<T> inner) {
            int n = next.length;
            int innerIdxBase = lut.size() + 2;
            lut.add(base);
            lut.add(n);
            // Bulk-extend with zero placeholders; cheaper than n add(0) calls.
            lut.size(innerIdxBase + n);

            for (int i = 0; i < n; i++) {
                T v = (T) next[i];
                if (v == null) continue;
                lut.set(innerIdxBase + i, lut.size());
                inner.accept(v, lut);
            }
        }

        private void resize(int len) {
            Object[] n = new Object[len];
            System.arraycopy(next, 0, n, 0, next.length);
            next = n;
        }

        private void rebase(int newBase) {
            int growth = base - newBase;
            Object[] n = new Object[next.length + growth];
            System.arraycopy(next, 0, n, growth, next.length);
            next = n;
            base = newBase;
        }
    }

    private static final class IntLayer {
        private boolean hasBase = false;
        private int base = 0;
        private int[] indices = new int[0];

        void set(int i, int v) {
            if (!hasBase) {
                base = i;
                hasBase = true;
            }
            if (i < base) rebase(i);
            int off = i - base;
            if (off >= indices.length) resize(off + 1);
            indices[off] = v;
        }

        void clear(int i) {
            if (!hasBase) return;
            int off = i - base;
            if (off < 0 || off >= indices.length) return;
            indices[off] = 0;
        }

        void fillLut(IntArrayList lut) {
            lut.add(base);
            lut.add(indices.length);
            lut.addElements(lut.size(), indices, 0, indices.length);
        }

        private void resize(int len) {
            int[] n = new int[len];
            System.arraycopy(indices, 0, n, 0, indices.length);
            indices = n;
        }

        private void rebase(int newBase) {
            int growth = base - newBase;
            int[] n = new int[indices.length + growth];
            System.arraycopy(indices, 0, n, growth, indices.length);
            indices = n;
            base = newBase;
        }
    }
}
