package org.valkyrienskies.mod.common.fluid;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class FluidParcelFaceSorter {
    private static final Comparator<Entry> ORDER = (a, b) -> Double.compare(b.distance, a.distance);
    private Entry[] entries = new Entry[0];

    private static final class Entry {
        FluidParcelCuboids.Face face;
        double distance;
    }

    void clear() { entries = new Entry[0]; }

    void sort(List<FluidParcelCuboids.Face> faces, double x, double y, double z) {
        final int count = faces.size();
        if (count < 2) return;
        if (entries.length < count) {
            final int previous = entries.length;
            entries = Arrays.copyOf(entries, Math.max(count, previous * 2));
            for (int i = previous; i < entries.length; i++) entries[i] = new Entry();
        }
        for (int i = 0; i < count; i++) {
            final var face = faces.get(i);
            entries[i].face = face;
            entries[i].distance = face.distanceSquared(x, y, z);
        }
        Arrays.sort(entries, 0, count, ORDER);
        for (int i = 0; i < count; i++) {
            faces.set(i, entries[i].face);
            entries[i].face = null;
        }
    }
}
