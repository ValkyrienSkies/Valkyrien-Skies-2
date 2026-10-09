package org.valkyrienskies.mod.common.render.light;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import org.joml.primitives.AABBi;

public final class ShipShadowBoxes {
    private ShipShadowBoxes() {
    }

    private record Span(int minA, int maxA, int minB, int maxB) { }

    /** Join section boxes that share a complete face. Keep holes open. */
    public static List<AABBi> joinSections(final List<AABBi> input, final int limit) {
        if (limit <= 0 || input.isEmpty()) return List.of();
        if (input.size() == 1) return List.of(new AABBi(input.get(0)));
        List<AABBi> boxes = new ArrayList<>(input);
        for (int pass = 0; pass < 2; pass++) for (int axis = 0; axis < 3; axis++) {
            if (boxes.size() <= 1) return boxes;
            final int along = axis;
            final Map<Span, List<AABBi>> groups = new LinkedHashMap<>();
            for (final AABBi box : boxes) {
                final Span span = switch (axis) {
                    case 0 -> new Span(box.minY, box.maxY, box.minZ, box.maxZ);
                    case 1 -> new Span(box.minX, box.maxX, box.minZ, box.maxZ);
                    default -> new Span(box.minX, box.maxX, box.minY, box.maxY);
                };
                groups.computeIfAbsent(span, key -> new ArrayList<>()).add(box);
            }
            boxes = new ArrayList<>();
            for (final List<AABBi> group : groups.values()) {
                group.sort(Comparator.comparingInt(box -> minimum(box, along)));
                AABBi merged = null;
                for (final AABBi next : group) {
                    if (merged != null && maximum(merged, axis) == minimum(next, axis)) {
                        if (axis == 0) merged.maxX = next.maxX;
                        else if (axis == 1) merged.maxY = next.maxY;
                        else merged.maxZ = next.maxZ;
                    } else {
                        if (merged != null) boxes.add(merged);
                        merged = new AABBi(next);
                    }
                }
                if (merged != null) boxes.add(merged);
            }
        }
        return boxes.size() <= limit ? boxes : new ArrayList<>(boxes.subList(0, limit));
    }

    private static int minimum(final AABBi box, final int axis) {
        return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
    }

    private static int maximum(final AABBi box, final int axis) {
        return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
    }

    /** Combine adjacent opaque cells. Keep empty cells outside all boxes. */
    public static List<AABBi> merge(final BitSet cells, final int width, final int height,
        final int depth, final int limit) {
        if (limit <= 0 || cells.isEmpty()) return List.of();
        final BitSet remaining = (BitSet) cells.clone();
        final List<AABBi> boxes = new ArrayList<>();
        for (int index = remaining.nextSetBit(0); index >= 0 && boxes.size() < limit;
            index = remaining.nextSetBit(0)) {
            final int x = index % width;
            final int z = index / width % depth;
            final int y = index / (width * depth);
            int endX = x + 1;
            while (endX < width && remaining.get(index + endX - x)) endX++;
            int endZ = z + 1;
            while (endZ < depth && rowPresent(remaining, x, endX, y, endZ, width, depth)) endZ++;
            int endY = y + 1;
            while (endY < height && layerPresent(remaining, x, endX, endY, z, endZ, width, depth)) endY++;
            for (int cy = y; cy < endY; cy++) for (int cz = z; cz < endZ; cz++) {
                final int row = (cy * depth + cz) * width;
                remaining.clear(row + x, row + endX);
            }
            boxes.add(new AABBi(x, y, z, endX, endY, endZ));
        }
        return boxes;
    }

    private static boolean rowPresent(final BitSet cells, final int x, final int endX,
        final int y, final int z, final int width, final int depth) {
        final int start = (y * depth + z) * width + x;
        return cells.nextClearBit(start) >= start + endX - x;
    }

    private static boolean layerPresent(final BitSet cells, final int x, final int endX,
        final int y, final int z, final int endZ, final int width, final int depth) {
        for (int cz = z; cz < endZ; cz++) {
            if (!rowPresent(cells, x, endX, y, cz, width, depth)) return false;
        }
        return true;
    }
}
