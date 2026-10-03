package org.valkyrienskies.mod.compat.sodium.light;

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.SectionPos;

/** A Y/X/Z lookup table whose coordinate spans contain only currently tracked sections. */
public final class LightLut {
    private final Int2ObjectAVLTreeMap<Int2ObjectAVLTreeMap<Int2IntAVLTreeMap>> sections =
        new Int2ObjectAVLTreeMap<>();

    public void add(final long sectionPos, final int index) {
        sections.computeIfAbsent(SectionPos.y(sectionPos), y -> new Int2ObjectAVLTreeMap<>())
            .computeIfAbsent(SectionPos.x(sectionPos), x -> new Int2IntAVLTreeMap())
            .put(SectionPos.z(sectionPos), index + 1);
    }

    public void remove(final long sectionPos) {
        final int y = SectionPos.y(sectionPos);
        final int x = SectionPos.x(sectionPos);
        final var xs = sections.get(y);
        if (xs == null) return;
        final var zs = xs.get(x);
        if (zs == null) return;
        zs.remove(SectionPos.z(sectionPos));
        if (zs.isEmpty()) xs.remove(x);
        if (xs.isEmpty()) sections.remove(y);
    }

    public void flattenInto(final IntArrayList out) {
        out.clear();
        if (sections.isEmpty()) {
            reserve(out, 0, -1);
            return;
        }
        final int yBase = sections.firstIntKey();
        final int yEntries = reserve(out, yBase, sections.lastIntKey());
        for (final var yEntry : sections.int2ObjectEntrySet()) {
            final var xs = yEntry.getValue();
            out.set(yEntries + yEntry.getIntKey() - yBase, out.size());
            final int xBase = xs.firstIntKey();
            final int xEntries = reserve(out, xBase, xs.lastIntKey());
            for (final var xEntry : xs.int2ObjectEntrySet()) {
                final var zs = xEntry.getValue();
                out.set(xEntries + xEntry.getIntKey() - xBase, out.size());
                final int zBase = zs.firstIntKey();
                final int zEntries = reserve(out, zBase, zs.lastIntKey());
                for (final var zEntry : zs.int2IntEntrySet()) {
                    out.set(zEntries + zEntry.getIntKey() - zBase, zEntry.getIntValue());
                }
            }
        }
    }

    private static int reserve(final IntArrayList out, final int first, final int last) {
        final int size = last - first + 1;
        out.add(first);
        out.add(size);
        final int entries = out.size();
        out.size(entries + size); // Zero-fill holes, including reused storage.
        return entries;
    }

    public int[] flatten() {
        final IntArrayList out = new IntArrayList();
        flattenInto(out);
        return out.toIntArray();
    }
}
