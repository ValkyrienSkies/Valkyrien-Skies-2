package org.valkyrienskies.mod.common.fluid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;

final class FluidSteepSurface {
    private static final int FULL_BOUNDS = (16 << 15) | (16 << 20) | (16 << 25);
    private static final int[][] SIDES = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4},
        {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
    private static final Vector3d[] POINTS = new Vector3d[9];
    private static final AABB CUBE = new AABB(0, 0, 0, 1, 1, 1);
    static {
        for (int i = 0; i < 8; i++) POINTS[i] = new Vector3d(i & 1, (i >> 1) & 1, (i >> 2) & 1);
        POINTS[8] = new Vector3d(0.5);
    }

    private FluidSteepSurface() { }

    static Map<Long, List<FluidSurfaceReconstruction.Face>> build(final Collection<VsiFluidFloodedVoxel> voxels,
        final IntFunction<Object> material) {
        final var cells = new HashMap<Long, Cell>();
        for (final var voxel : voxels) {
            final var cell = create(voxel, material);
            if (cell != null) cells.put(cell.key(), cell);
        }
        final var ordered = new ArrayList<>(cells.values());
        ordered.sort(Comparator.comparingLong(Cell::key));
        for (final var cell : ordered) for (int axis = 0; axis < 3; axis++) {
            final int side = axis * 2 + 1;
            final var other = cells.get(cell.neighbor(axis));
            if (!cell.compatible(other) || !cell.wet(side) || !other.wet(side - 1)) continue;
            cell.neighbors[side] = other;
            other.neighbors[side - 1] = cell;
            for (final int i : SIDES[side]) {
                final int j = i ^ (1 << axis);
                if (!cell.full && !other.full) cell.corners[i].join(other.corners[j]);
            }
        }
        final var visited = new HashSet<Long>();
        final var result = new HashMap<Long, List<FluidSurfaceReconstruction.Face>>();
        for (final var start : ordered) {
            if (!visited.add(start.key())) continue;
            final var component = new ArrayList<Cell>();
            final var queue = new ArrayDeque<Cell>();
            queue.add(start);
            boolean steep = false, changed = false;
            while (!queue.isEmpty()) {
                final var cell = queue.removeFirst();
                component.add(cell);
                steep |= cell.steep;
                if (!cell.full) for (int i = 0; i < 8; i++) {
                    cell.values[i] = cell.corners[i].value();
                    changed |= Math.abs(cell.values[i] - cell.original[i]) > 1e-10;
                }
                for (final var other : cell.neighbors) if (other != null && visited.add(other.key())) queue.add(other);
            }
            if (!steep || !changed) continue;
            final var normals = new HashMap<Point, Vector3d>();
            for (final var cell : component) cell.reconstruct(normals);
            for (final var normal : normals.values()) if (normal.lengthSquared() > 1e-24) normal.normalize();
            for (final var cell : component) result.put(cell.key(), List.copyOf(cell.faces));
        }
        return result;
    }

    static boolean clipped(final VsiFluidFloodedVoxel voxel) {
        if (voxel.getSurfaceRegions().size() != 1) return false;
        final var r = voxel.getSurfaceRegions().get(0);
        if (r.getBounds() != FULL_BOUNDS) return false;
        final double[] n = {r.getNormalX(), r.getNormalY(), r.getNormalZ()};
        final double min = Math.min(0, n[0]) + Math.min(0, n[1]) + Math.min(0, n[2]);
        final double max = Math.max(0, n[0]) + Math.max(0, n[1]) + Math.max(0, n[2]);
        if (r.getLowerProjection() > min + 1e-9 || r.getUpperProjection() <= min || r.getUpperProjection() >= max) return false;
        int axis = 0;
        for (int i = 1; i < 3; i++) if (Math.abs(n[i]) > Math.abs(n[axis])) axis = i;
        final int u = (axis + 1) % 3, v = (axis + 2) % 3;
        for (int i = 0; i < 4; i++) {
            final double h = (r.getUpperProjection() - n[u] * (i & 1) - n[v] * (i >> 1)) / n[axis];
            if (h < 1e-6 || h > 1 - 1e-6) return true;
        }
        return false;
    }

    private static Cell create(final VsiFluidFloodedVoxel voxel, final IntFunction<Object> material) {
        if (voxel.getSurfaceRegions().size() != 1) return null;
        final var region = voxel.getSurfaceRegions().get(0);
        if (region.getBounds() != FULL_BOUNDS) return null;
        final var n = new Vector3d(region.getNormalX(), region.getNormalY(), region.getNormalZ());
        if (region.getLowerProjection() > Math.min(0, n.x) + Math.min(0, n.y) + Math.min(0, n.z) + 1e-9) return null;
        final double target = FluidSurfaceGeometry.volumeBelow(CUBE,
            new FluidSurfaceGeometry.Plane(n.x, n.y, n.z, region.getUpperProjection()));
        final var identity = material.apply(FluidVoxelGeometry.dominantFluid(region, true));
        if (target <= 0 || identity == null) return null;
        return new Cell(voxel, n, region.getUpperProjection(), target, identity);
    }

    private static final class Sample {
        private Sample parent = this;
        private double sum;
        private int count = 1;
        private Sample(final double value) { sum = value; }
        private Sample root() {
            if (parent != this) parent = parent.root();
            return parent;
        }
        private void join(final Sample other) {
            final var a = root(); final var b = other.root();
            if (a == b) return;
            b.parent = a; a.sum += b.sum; a.count += b.count;
        }
        private double value() {
            final var r = root();
            final double value = r.sum / r.count;
            return Math.abs(value) < 1e-14 ? -1e-14 : value;
        }
    }

    private record Point(long x, long y, long z) { }

    private static final class Cell {
        private final int x, y, z;
        private final Vector3d normal;
        private final double target;
        private final Object material;
        private final boolean full, steep;
        private final double[] original = new double[8], values = new double[9];
        private final Sample[] corners = new Sample[8];
        private final Cell[] neighbors = new Cell[6];
        private final List<FluidSurfaceReconstruction.Face> faces = new ArrayList<>();

        private Cell(final VsiFluidFloodedVoxel voxel, final Vector3d normal, final double offset,
            final double target, final Object material) {
            x = voxel.getPositionX(); y = voxel.getPositionY(); z = voxel.getPositionZ();
            this.normal = normal; this.target = target; this.material = material;
            full = target == 1;
            int axis = 0;
            for (int i = 1; i < 3; i++) if (Math.abs(normal.get(i)) > Math.abs(normal.get(axis))) axis = i;
            boolean clipped = false;
            for (int i = 0; i < 8; i++) {
                original[i] = normal.dot(POINTS[i]) - offset;
                corners[i] = new Sample(original[i]);
                values[i] = full ? -1 : original[i];
                if ((i & (1 << axis)) == 0) {
                    final double h = -original[i] / normal.get(axis);
                    clipped |= h < 1e-6 || h > 1 - 1e-6;
                }
            }
            steep = !full && clipped;
        }

        private long key() { return FloodedFluidClientCache.pack(x, y, z); }
        private long neighbor(final int axis) {
            return FloodedFluidClientCache.pack(x + (axis == 0 ? 1 : 0), y + (axis == 1 ? 1 : 0), z + (axis == 2 ? 1 : 0));
        }
        private boolean compatible(final Cell other) {
            return other != null && material.equals(other.material) && normal.dot(other.normal) > 1 - 1e-8;
        }
        private boolean wet(final int side) {
            if (full) return true;
            for (final int i : SIDES[side]) if (original[i] < -1e-12) return true;
            return false;
        }
        private double volume(final double center) {
            double sum = 0;
            for (final var side : SIDES) for (int i = 1; i < 3; i++) {
                sum += fraction(center, values[side[0]], values[side[i]], values[side[i + 1]]) / 12;
            }
            return sum;
        }
        private void reconstruct(final Map<Point, Vector3d> normals) {
            if (!full) {
                double scale = 0;
                for (int i = 0; i < 8; i++) scale = Math.max(scale, Math.abs(values[i]));
                double low = -scale, high = scale;
                for (int i = 0; i < 80 && volume(low) < target; i++) low *= 2;
                for (int i = 0; i < 80 && volume(high) > target; i++) high *= 2;
                final double tolerance = Math.min(1e-13, Math.min(target, 1 - target) * 1e-8);
                for (int i = 0; i < 64; i++) {
                    final double middle = (low + high) * 0.5;
                    final double filled = volume(middle);
                    if (Math.abs(filled - target) <= tolerance) { low = middle; high = middle; break; }
                    if (filled > target) low = middle; else high = middle;
                }
                values[8] = (low + high) * 0.5;
            } else values[8] = -1;
            for (int s = 0; s < 6; s++) {
                final var side = SIDES[s];
                final boolean top = normal.get(s / 2) * (s % 2 == 0 ? -1 : 1) > 0.99;
                if (full && (neighbors[s] == null || neighbors[s].full)) {
                    faces.add(new FluidSurfaceReconstruction.Face(List.of(new Vector3d(POINTS[side[0]]),
                        new Vector3d(POINTS[side[1]]), new Vector3d(POINTS[side[2]]), new Vector3d(POINTS[side[3]])),
                        List.of(), top, neighbors[s] != null));
                    continue;
                }
                double[] boundaryValues = values;
                if (full && neighbors[s] != null && !neighbors[s].full) {
                    boundaryValues = new double[8];
                    for (final int i : side) boundaryValues[i] = neighbors[s].values[i ^ (1 << (s / 2))];
                }
                for (int i = 1; i < 3; i++) {
                    final int a = side[0], b = side[i], c = side[i + 1];
                    final var boundary = boundary(a, b, c, boundaryValues, true);
                    if (boundary.size() >= 3) faces.add(new FluidSurfaceReconstruction.Face(boundary, List.of(), top, neighbors[s] != null));
                    if (boundaryValues != values) addSurface(boundary(a, b, c, boundaryValues, false), top, normals);
                    if (!full) surface(new int[]{8, a, b, c}, normals);
                }
            }
        }
        private Vector3d intersection(final int a, final int b) {
            return intersection(a, b, values);
        }
        private Vector3d intersection(final int a, final int b, final double[] samples) {
            if (a > b) return intersection(b, a, samples);
            return new Vector3d(POINTS[a]).lerp(POINTS[b], samples[a] / (samples[a] - samples[b]));
        }
        private List<Vector3d> boundary(final int a, final int b, final int c, final double[] samples, final boolean wet) {
            final var result = new ArrayList<Vector3d>(4);
            final int[] triangle = {a, b, c};
            for (int i = 0; i < 3; i++) {
                final int first = triangle[i], next = triangle[(i + 1) % 3];
                if (wet ? samples[first] <= 0 : samples[first] >= 0) result.add(new Vector3d(POINTS[first]));
                if (samples[first] < 0 && samples[next] > 0 || samples[first] > 0 && samples[next] < 0) result.add(intersection(first, next, samples));
            }
            return List.copyOf(result);
        }
        private void surface(final int[] tetra, final Map<Point, Vector3d> normals) {
            final var wet = new ArrayList<Integer>(4);
            final var dry = new ArrayList<Integer>(4);
            for (final int i : tetra) (values[i] <= 0 ? wet : dry).add(i);
            if (wet.isEmpty() || dry.isEmpty()) return;
            final var points = new ArrayList<Vector3d>(4);
            if (wet.size() == 2) {
                points.add(intersection(wet.get(0), dry.get(0)));
                points.add(intersection(wet.get(0), dry.get(1)));
                points.add(intersection(wet.get(1), dry.get(1)));
                points.add(intersection(wet.get(1), dry.get(0)));
            } else {
                final var single = wet.size() == 1 ? wet : dry;
                final var multiple = wet.size() == 1 ? dry : wet;
                for (final int i : multiple) points.add(intersection(single.get(0), i));
            }
            for (int i = points.size() - 1; i >= 0; i--) {
                if (points.get(i).equals(points.get((i + 1) % points.size()))) points.remove(i);
            }
            if (points.size() < 3) return;
            final var direction = new Vector3d(POINTS[dry.get(0)]).sub(POINTS[wet.get(0)]);
            final var n = areaNormal(points);
            if (n.dot(direction) < 0) java.util.Collections.reverse(points);
            addSurface(points, true, normals);
        }
        private Vector3d areaNormal(final List<Vector3d> points) {
            final var n = new Vector3d();
            for (int i = 1; i + 1 < points.size(); i++) n.add(new Vector3d(points.get(i)).sub(points.get(0))
                .cross(new Vector3d(points.get(i + 1)).sub(points.get(0))));
            return n;
        }
        private void addSurface(final List<Vector3d> points, final boolean top, final Map<Point, Vector3d> normals) {
            final var n = areaNormal(points);
            if (n.lengthSquared() < 1e-24) return;
            final var vertexNormals = new ArrayList<Vector3d>(points.size());
            for (final var p : points) {
                final var key = new Point(x * 1_000_000_000L + Math.round(p.x * 1e9),
                    y * 1_000_000_000L + Math.round(p.y * 1e9), z * 1_000_000_000L + Math.round(p.z * 1e9));
                final var shared = normals.computeIfAbsent(key, ignored -> new Vector3d());
                shared.add(n);
                vertexNormals.add(shared);
            }
            faces.add(new FluidSurfaceReconstruction.Face(List.copyOf(points), List.copyOf(vertexNormals), top, false));
        }
    }

    private static double fraction(double a, double b, double c, double d) {
        double swap;
        if (a > b) { swap = a; a = b; b = swap; }
        if (c > d) { swap = c; c = d; d = swap; }
        if (a > c) { swap = a; a = c; c = swap; }
        if (b > d) { swap = b; b = d; d = swap; }
        if (b > c) { swap = b; b = c; c = swap; }
        if (a >= 0) return 0;
        if (d <= 0) return 1;
        if (b >= 0) return a / (a - b) * a / (a - c) * a / (a - d);
        if (c <= 0) return 1 - d / (d - a) * d / (d - b) * d / (d - c);
        final double ac = a / (a - c), ad = a / (a - d), bc = b / (b - c), bd = b / (b - d);
        return ac * ad + ac * bd * (1 - ad) + bc * bd * (1 - ac);
    }
}
