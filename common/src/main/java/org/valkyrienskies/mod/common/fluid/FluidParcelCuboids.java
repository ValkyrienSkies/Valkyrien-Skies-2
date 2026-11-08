package org.valkyrienskies.mod.common.fluid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class FluidParcelCuboids {
    public record Parcel(Vector3dc position, double volume, int material) { }
    public record Face(int axis, int sign, double plane, double minU, double maxU,
                       double minV, double maxV, int material) {
        public int u() { return (axis + 1) % 3; }
        public int v() { return (axis + 2) % 3; }
        public double coordinate(int component, double u, double v) {
            return component == axis ? plane : component == u() ? u : v;
        }
        public double distanceSquared(double x, double y, double z) {
            final double u = (minU + maxU) * 0.5, v = (minV + maxV) * 0.5;
            final double dx = coordinate(0, u, v) - x, dy = coordinate(1, u, v) - y;
            final double dz = coordinate(2, u, v) - z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public record Mesh(Vector3dc origin, List<Face> faces) { }

    private FluidParcelCuboids() { }

    private static final class Box {
        final double[] center = new double[3], lo = new double[3], hi = new double[3];
        final double volume;
        double half, density = 1;

        Box(Parcel parcel, Vector3dc origin) {
            volume = parcel.volume;
            half = Math.cbrt(volume) * 0.5;
            for (int a = 0; a < 3; a++) center[a] = parcel.position.get(a) - origin.get(a);
            update();
        }

        void update() {
            for (int a = 0; a < 3; a++) { lo[a] = center[a] - half; hi[a] = center[a] + half; }
        }

        boolean overlaps(Box other) {
            for (int a = 0; a < 3; a++) if (lo[a] > other.hi[a] || hi[a] < other.lo[a]) return false;
            return true;
        }

        boolean contains(Box other) {
            for (int a = 0; a < 3; a++) if (lo[a] > other.lo[a] || hi[a] < other.hi[a]) return false;
            return true;
        }
    }

    /** Keep clipping order stable while pruning along the longest axis, including vertical streams. */
    private static final class BoxIndex {
        private final List<Box> boxes;
        private final Integer[] order;
        private final double[] prefixMax;
        private final int axis;

        static BoxIndex forElongated(List<Box> boxes) {
            final double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            final double[] hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (final var box : boxes) for (int a = 0; a < 3; a++) {
                lo[a] = Math.min(lo[a], box.lo[a]);
                hi[a] = Math.max(hi[a], box.hi[a]);
            }
            int longest = 0;
            for (int a = 1; a < 3; a++)
                if (hi[a] - lo[a] > hi[longest] - lo[longest]) longest = a;
            // Keep the cheap original path for clouds and clusters with comparable extents.
            if (longest == 0 || hi[longest] - lo[longest] <= 2 * (hi[0] - lo[0])) return null;
            return new BoxIndex(boxes, longest);
        }

        private BoxIndex(List<Box> boxes, int axis) {
            this.boxes = boxes;
            this.axis = axis;
            order = new Integer[boxes.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            if (axis != 0) Arrays.sort(order, Comparator.comparingDouble((Integer i) -> boxes.get(i).lo[axis])
                .thenComparingInt(i -> i));
            prefixMax = new double[order.length];
            for (int i = 0; i < order.length; i++)
                prefixMax[i] = Math.max(i == 0 ? Double.NEGATIVE_INFINITY : prefixMax[i - 1], boxes.get(order[i]).hi[axis]);
        }

        int overlaps(Box box, int[] result) {
            int start = Arrays.binarySearch(prefixMax, box.lo[axis]);
            if (start < 0) start = -start - 1;
            else while (start > 0 && prefixMax[start - 1] >= box.lo[axis]) start--;
            int count = 0;
            for (int k = start; k < order.length; k++) {
                final int i = order[k];
                final var other = boxes.get(i);
                if (other.lo[axis] > box.hi[axis]) break;
                if (box.overlaps(other)) result[count++] = i;
            }
            // Preserve density addition, coincident-face ownership, clipping and face emission order.
            if (axis != 0) Arrays.sort(result, 0, count);
            return count;
        }
    }

    private static final class Rectangles {
        double[] current = new double[256], next = new double[256];
        int count;

        void reset(double u0, double u1, double v0, double v1) {
            count = 1;
            current[0] = u0; current[1] = u1; current[2] = v0; current[3] = v1;
        }

        void subtract(double u0, double u1, double v0, double v1) {
            int size = 0;
            if (next.length < count * 16) next = new double[count * 32];
            for (int i = 0; i < count; i++) {
                final int p = i * 4;
                final double a = current[p], b = current[p + 1], c = current[p + 2], d = current[p + 3];
                final double left = Math.max(a, u0), right = Math.min(b, u1);
                final double bottom = Math.max(c, v0), top = Math.min(d, v1);
                if (left >= right || bottom >= top) {
                    next[size++] = a; next[size++] = b; next[size++] = c; next[size++] = d;
                    continue;
                }
                if (a < left) { next[size++] = a; next[size++] = left; next[size++] = c; next[size++] = d; }
                if (right < b) { next[size++] = right; next[size++] = b; next[size++] = c; next[size++] = d; }
                if (c < bottom) { next[size++] = left; next[size++] = right; next[size++] = c; next[size++] = bottom; }
                if (top < d) { next[size++] = left; next[size++] = right; next[size++] = top; next[size++] = d; }
            }
            final var swap = current;
            current = next; next = swap; count = size / 4;
        }
    }

    public static Mesh build(List<Parcel> parcels) {
        if (parcels.isEmpty()) return new Mesh(new Vector3d(), new ArrayList<>());
        final var first = parcels.stream().filter(p -> p.volume > 0 && Double.isFinite(p.volume) && p.position.isFinite()).findFirst();
        if (first.isEmpty()) return new Mesh(new Vector3d(), new ArrayList<>());
        final var origin = new Vector3d(first.get().position);
        final var materials = new TreeMap<Integer, List<Box>>();
        for (final var parcel : parcels) {
            if (!(parcel.volume > 0) || !Double.isFinite(parcel.volume) || !parcel.position.isFinite()) continue;
            final var box = new Box(parcel, origin);
            if (box.lo[0] >= box.hi[0] || box.lo[1] >= box.hi[1] || box.lo[2] >= box.hi[2]) continue;
            materials.computeIfAbsent(parcel.material, key -> new ArrayList<>()).add(box);
        }
        final var faces = new ArrayList<Face>();
        final var scratch = new Rectangles();
        for (final var entry : materials.entrySet()) {
            final var boxes = entry.getValue();
            boxes.sort(Comparator.comparingDouble((Box b) -> b.lo[0])
                .thenComparingDouble(b -> b.lo[1]).thenComparingDouble(b -> b.lo[2])
                .thenComparingDouble(b -> b.volume));
            final var densityIndex = BoxIndex.forElongated(boxes);
            final int[] neighbors = densityIndex == null ? null : new int[boxes.size()];
            double target = 0;
            for (int i = 0; i < boxes.size(); i++) {
                final var a = boxes.get(i);
                target += a.volume;
                if (densityIndex == null) {
                    for (int j = i + 1; j < boxes.size(); j++) {
                        final var b = boxes.get(j);
                        if (b.lo[0] >= a.hi[0]) break;
                        addOverlapDensity(a, b);
                    }
                } else {
                    final int count = densityIndex.overlaps(a, neighbors);
                    for (int k = 0; k < count; k++) {
                        final int j = neighbors[k];
                        if (j <= i) continue;
                        final var b = boxes.get(j);
                        if (b.lo[0] >= a.hi[0]) break;
                        addOverlapDensity(a, b);
                    }
                }
            }
            for (final var box : boxes) { box.half *= Math.cbrt(box.density); box.update(); }
            boxes.sort(Comparator.comparingDouble((Box b) -> b.lo[0])
                .thenComparingDouble(b -> b.lo[1]).thenComparingDouble(b -> b.lo[2])
                .thenComparingDouble(b -> b.volume));
            final int firstFace = faces.size();
            final double volume = emit(boxes, scratch, faces, entry.getKey());
            if (!(volume > 0) || !Double.isFinite(volume) || !Double.isFinite(target)) {
                faces.subList(firstFace, faces.size()).clear();
                continue;
            }
            final double ratio = target / volume;
            final var center = new Vector3d();
            for (final var box : boxes)
                center.add(box.center[0] * (box.volume / target), box.center[1] * (box.volume / target), box.center[2] * (box.volume / target));
            final var variance = new Vector3d();
            double intrinsic = 0;
            for (final var box : boxes) {
                final double weight = box.volume / target;
                for (int a = 0; a < 3; a++) {
                    final double delta = box.center[a] - center.get(a);
                    variance.setComponent(a, variance.get(a) + delta * delta * weight);
                }
                intrinsic += weight * box.half * box.half / 3;
            }
            final var weights = new Vector3d(1 / (variance.x + intrinsic), 1 / (variance.y + intrinsic), 1 / (variance.z + intrinsic));
            weights.div(weights.x + weights.y + weights.z);
            final var scale = new Vector3d(Math.pow(ratio, weights.x), Math.pow(ratio, weights.y), Math.pow(ratio, weights.z));
            for (int i = firstFace; i < faces.size(); i++) {
                final var f = faces.get(i);
                final int a = f.axis, u = f.u(), v = f.v();
                faces.set(i, new Face(a, f.sign, (f.plane - center.get(a)) * scale.get(a) + center.get(a),
                    (f.minU - center.get(u)) * scale.get(u) + center.get(u),
                    (f.maxU - center.get(u)) * scale.get(u) + center.get(u),
                    (f.minV - center.get(v)) * scale.get(v) + center.get(v),
                    (f.maxV - center.get(v)) * scale.get(v) + center.get(v), entry.getKey()));
            }
        }
        return new Mesh(origin, faces);
    }

    private static void addOverlapDensity(Box a, Box b) {
        double overlap = 1;
        for (int axis = 0; axis < 3; axis++)
            overlap *= Math.max(0, Math.min(a.hi[axis], b.hi[axis]) - Math.max(a.lo[axis], b.lo[axis]));
        a.density += overlap / a.volume;
        b.density += overlap / b.volume;
    }

    private static double emit(List<Box> boxes, Rectangles scratch, List<Face> faces, int material) {
        double volume = 0;
        final double origin = boxes.get(0).center[0];
        final int[] neighbors = new int[boxes.size()];
        final var index = BoxIndex.forElongated(boxes);
        final double[] prefixMax = index == null ? new double[boxes.size()] : null;
        if (prefixMax != null) for (int i = 0; i < boxes.size(); i++)
            prefixMax[i] = Math.max(i == 0 ? Double.NEGATIVE_INFINITY : prefixMax[i - 1], boxes.get(i).hi[0]);
        bodies: for (int i = 0; i < boxes.size(); i++) {
            final var box = boxes.get(i);
            int count = 0;
            if (index == null) {
                int start = Arrays.binarySearch(prefixMax, box.lo[0]);
                if (start < 0) start = -start - 1;
                else while (start > 0 && prefixMax[start - 1] >= box.lo[0]) start--;
                for (int j = start; j < boxes.size(); j++) {
                    final var other = boxes.get(j);
                    if (other.lo[0] > box.hi[0]) break;
                    if (i == j || !box.overlaps(other)) continue;
                    if (other.contains(box) && (j < i || !box.contains(other))) continue bodies;
                    neighbors[count++] = j;
                }
            } else {
                final int candidates = index.overlaps(box, neighbors);
                for (int k = 0; k < candidates; k++) {
                    final int j = neighbors[k];
                    if (i == j) continue;
                    final var other = boxes.get(j);
                    if (other.contains(box) && (j < i || !box.contains(other))) continue bodies;
                    neighbors[count++] = j;
                }
            }
            for (int axis = 0; axis < 3; axis++) for (int sign = -1; sign <= 1; sign += 2) {
                final int u = (axis + 1) % 3, v = (axis + 2) % 3;
                final double plane = sign < 0 ? box.lo[axis] : box.hi[axis];
                scratch.reset(box.lo[u], box.hi[u], box.lo[v], box.hi[v]);
                for (int k = 0; k < count && scratch.count > 0; k++) {
                    final int j = neighbors[k];
                    final var other = boxes.get(j);
                    if (plane < other.lo[axis] || plane > other.hi[axis]) continue;
                    if (sign > 0 && plane == other.hi[axis] && i < j || sign < 0 && plane == other.lo[axis] && i < j) continue;
                    scratch.subtract(other.lo[u], other.hi[u], other.lo[v], other.hi[v]);
                }
                for (int k = 0; k < scratch.count; k++) {
                    final int p = k * 4;
                    final double u0 = scratch.current[p], u1 = scratch.current[p + 1];
                    final double v0 = scratch.current[p + 2], v1 = scratch.current[p + 3];
                    if (axis == 0) volume += sign * (plane - origin) * (u1 - u0) * (v1 - v0);
                    faces.add(new Face(axis, sign, plane, u0, u1, v0, v1, material));
                }
            }
        }
        return volume;
    }
}
