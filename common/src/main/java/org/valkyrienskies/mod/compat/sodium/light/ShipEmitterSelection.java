package org.valkyrienskies.mod.compat.sodium.light;

import org.joml.primitives.AABBdc;

/** Select only emitters whose light radius intersects the receiver, preferring stronger contributions. */
public final class ShipEmitterSelection {
    public static final int MAX_SELECTED = 128;

    private ShipEmitterSelection() {
    }

    public static int select(final float[] emitters, final int count, final AABBdc bounds,
        final int[] selected, final double[] scores) {
        int size = 0;
        if (selected.length == 0) return size;
        int weakestSlot = 0;
        for (int i = 0; i < count; i++) {
            final int p = i * 4;
            final double dx = Math.max(Math.max(bounds.minX() - emitters[p], 0), emitters[p] - bounds.maxX());
            final double dy = Math.max(Math.max(bounds.minY() - emitters[p + 1], 0), emitters[p + 1] - bounds.maxY());
            final double dz = Math.max(Math.max(bounds.minZ() - emitters[p + 2], 0), emitters[p + 2] - bounds.maxZ());
            final double radius = emitters[p + 3];
            final double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= radius * radius) continue;
            final double score = radius - Math.sqrt(distanceSquared);
            int slot = size;
            if (size == selected.length) {
                slot = weakestSlot;
                if (score <= scores[slot]) continue;
                selected[slot] = i;
                scores[slot] = score;
                for (int j = 0; j < size; j++) {
                    if (scores[j] < scores[weakestSlot]) weakestSlot = j;
                }
            } else {
                size++;
                selected[slot] = i;
                scores[slot] = score;
                if (score < scores[weakestSlot]) weakestSlot = slot;
            }
        }
        return size;
    }
}
