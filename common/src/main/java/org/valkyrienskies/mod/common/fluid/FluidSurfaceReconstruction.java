package org.valkyrienskies.mod.common.fluid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.joml.Vector3d;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;

final class FluidSurfaceReconstruction {
    private static final int FULL_BOUNDS = (16 << 15) | (16 << 20) | (16 << 25);
    private static final int[] U = {0, 1, 1, 0}, V = {0, 0, 1, 1};
    private static final double MARGIN = 1e-6;

    record Face(List<Vector3d> points, List<Vector3d> normals, boolean top, boolean internal) { }

    private FluidSurfaceReconstruction() { }

    static Map<Long, List<Face>> build(final Collection<VsiFluidFloodedVoxel> voxels,
        final IntFunction<Object> material) {
        final var cells = new HashMap<Long, Cell>();
        boolean steep = false;
        for (final var voxel : voxels) {
            final var cell = create(voxel, material);
            if (cell != null) cells.put(cell.key(), cell);
            else if (!steep) steep = FluidSteepSurface.clipped(voxel);
        }
        final var result = steep ? FluidSteepSurface.build(voxels, material) : new HashMap<Long, List<Face>>();
        cells.keySet().removeAll(result.keySet());
        for (final var cell : cells.values()) {
            final var alongU = cells.get(cell.neighbor(cell.u));
            final var alongV = cells.get(cell.neighbor(cell.v));
            if (cell.compatible(alongU)) join(cell, 1, 2, 1, alongU, 0, 3, 3);
            if (cell.compatible(alongV)) join(cell, 3, 2, 2, alongV, 0, 1, 0);
        }
        boolean changed = false;
        for (final var cell : cells.values()) if (cell.joined != 0) {
            for (int i = 0; i < 4; i++) changed |= Math.abs(cell.corners[i].height() - cell.original[i]) > 1e-7;
        }
        if (!changed) return result;
        for (final var cell : cells.values()) {
            if (cell.joined == 0) continue;
            if (!cell.reconstruct()) return result;
            cell.accumulateNormals();
        }
        final var normalized = new IdentityHashMap<Vector3d, Boolean>();
        for (final var cell : cells.values()) {
            if (cell.joined == 0) continue;
            for (final var normal : cell.normals) {
                if (normal != null && normalized.put(normal, true) == null) normal.normalize();
            }
        }
        for (final var cell : cells.values()) if (cell.joined != 0) result.put(cell.key(), cell.faces());
        return result;
    }

    private static Cell create(final VsiFluidFloodedVoxel voxel, final IntFunction<Object> material) {
        if (voxel.getSurfaceRegions().size() != 1) return null;
        final var region = voxel.getSurfaceRegions().get(0);
        if (region.getBounds() != FULL_BOUNDS) return null;
        final double[] n = {region.getNormalX(), region.getNormalY(), region.getNormalZ()};
        int axis = 0;
        for (int i = 1; i < 3; i++) if (Math.abs(n[i]) > Math.abs(n[axis])) axis = i;
        final double minimum = Math.min(0, n[0]) + Math.min(0, n[1]) + Math.min(0, n[2]);
        if (region.getLowerProjection() > minimum + 1e-9) return null;
        final double maximum = Math.max(0, n[0]) + Math.max(0, n[1]) + Math.max(0, n[2]);
        if (region.getUpperProjection() >= maximum - 1e-9) return null;
        final var identity = material.apply(FluidVoxelGeometry.dominantFluid(region, true));
        if (identity == null) return null;
        final var cell = new Cell(voxel, n, axis, identity);
        for (int i = 0; i < 4; i++) {
            double h = (region.getUpperProjection() - n[cell.u] * U[i] - n[cell.v] * V[i]) / n[axis];
            if (cell.sign < 0) h = 1 - h;
            if (h < MARGIN || h > 1 - MARGIN) return null;
            cell.original[i] = h;
            cell.corners[i] = new Corner(h);
        }
        return cell;
    }

    private static void join(final Cell a, final int a0, final int a1, final int edgeA,
        final Cell b, final int b0, final int b1, final int edgeB) {
        a.corners[a0].join(b.corners[b0]);
        a.corners[a1].join(b.corners[b1]);
        a.joined |= 1 << edgeA;
        b.joined |= 1 << edgeB;
    }

    private static final class Corner {
        private Corner parent = this;
        private double sum;
        private int count = 1;
        private final Vector3d normal = new Vector3d();

        private Corner(final double height) { sum = height; }
        private Corner root() {
            if (parent != this) parent = parent.root();
            return parent;
        }
        private void join(final Corner other) {
            final var a = root();
            final var b = other.root();
            if (a == b) return;
            b.parent = a; a.sum += b.sum; a.count += b.count;
        }
        private double height() { final var root = root(); return root.sum / root.count; }
    }

    private static final class Cell {
        private final int[] position;
        private final double[] normal;
        private final int axis, u, v, sign;
        private final Object material;
        private final double[] original = new double[4];
        private final Corner[] corners = new Corner[4];
        private final Vector3d[] points = new Vector3d[9], normals = new Vector3d[9];
        private final List<int[]> triangles = new ArrayList<>(12);
        private int joined;

        private Cell(final VsiFluidFloodedVoxel voxel, final double[] normal, final int axis, final Object material) {
            position = new int[]{voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()};
            this.normal = normal; this.axis = axis; this.material = material;
            u = (axis + 1) % 3; v = (axis + 2) % 3; sign = normal[axis] > 0 ? 1 : -1;
            for (int i = 0; i < 4; i++) {
                final int j = (i + 1) % 4;
                triangles.add(new int[]{i, j, 4 + j});
                triangles.add(new int[]{i, 4 + j, 4 + i});
                triangles.add(new int[]{8, 4 + i, 4 + j});
            }
            if (sign < 0) for (final var triangle : triangles) {
                final int swap = triangle[1]; triangle[1] = triangle[2]; triangle[2] = swap;
            }
        }

        private long key() { return FloodedFluidClientCache.pack(position[0], position[1], position[2]); }
        private long neighbor(final int axis) {
            return FloodedFluidClientCache.pack(position[0] + (axis == 0 ? 1 : 0),
                position[1] + (axis == 1 ? 1 : 0), position[2] + (axis == 2 ? 1 : 0));
        }
        private boolean compatible(final Cell other) {
            return other != null && axis == other.axis && sign == other.sign && material.equals(other.material)
                && normal[0] * other.normal[0] + normal[1] * other.normal[1] + normal[2] * other.normal[2] > 1 - 1e-8;
        }
        private Vector3d point(final double x, final double y, final double height) {
            final var result = new Vector3d();
            result.setComponent(u, x); result.setComponent(v, y);
            result.setComponent(axis, sign > 0 ? height : 1 - height);
            return result;
        }
        private double height(final Vector3d point) { return sign > 0 ? point.get(axis) : 1 - point.get(axis); }
        private double originalHeight(final double x, final double y) {
            return original[0] + x * (original[1] - original[0]) + y * (original[3] - original[0]);
        }

        private boolean reconstruct() {
            for (int i = 0; i < 4; i++) {
                points[i] = point(U[i], V[i], corners[i].height());
                normals[i] = corners[i].root().normal;
            }
            final double target = (original[0] + original[1] + original[2] + original[3]) * 0.25;
            final double boundary = (height(points[0]) + height(points[1]) + height(points[2]) + height(points[3])) * 0.25;
            final double center = 3 * target - 2 * boundary;
            if (center >= 0 && center <= 1) {
                points[8] = point(0.5, 0.5, center);
                normals[8] = new Vector3d();
                triangles.clear();
                for (int i = 0; i < 4; i++) triangles.add(sign > 0
                    ? new int[]{8, i, (i + 1) % 4} : new int[]{8, (i + 1) % 4, i});
                return true;
            }
            double inset = 0.25;
            for (int attempt = 0; attempt < 24; attempt++, inset *= 0.5) {
                for (int i = 0; i < 4; i++) {
                    final double x = inset + U[i] * (1 - 2 * inset), y = inset + V[i] * (1 - 2 * inset);
                    points[4 + i] = point(x, y, originalHeight(x, y));
                }
                points[8] = point(0.5, 0.5, originalHeight(0.5, 0.5));
                double volume = 0, response = 0;
                for (final var triangle : triangles) {
                    final var a = points[triangle[0]]; final var b = points[triangle[1]]; final var c = points[triangle[2]];
                    final double area = Math.abs((b.get(u) - a.get(u)) * (c.get(v) - a.get(v))
                        - (b.get(v) - a.get(v)) * (c.get(u) - a.get(u))) * 0.5;
                    volume += area * (height(a) + height(b) + height(c)) / 3;
                    for (final int index : triangle) if (index >= 4) response += area / 3;
                }
                final double shift = (target - volume) / response;
                boolean fits = true;
                for (int i = 4; i < 9; i++) fits &= height(points[i]) + shift >= 0 && height(points[i]) + shift <= 1;
                if (!fits) continue;
                for (int i = 4; i < 9; i++) {
                    points[i].setComponent(axis, points[i].get(axis) + sign * shift);
                    normals[i] = new Vector3d();
                }
                return true;
            }
            return false;
        }

        private void accumulateNormals() {
            for (final var triangle : triangles) {
                final var n = new Vector3d(points[triangle[1]]).sub(points[triangle[0]])
                    .cross(new Vector3d(points[triangle[2]]).sub(points[triangle[0]]));
                for (final int index : triangle) normals[index].add(n);
            }
        }

        private List<Face> faces() {
            final var faces = new ArrayList<Face>();
            for (final var t : triangles) faces.add(new Face(List.of(points[t[0]], points[t[1]], points[t[2]]),
                List.of(normals[t[0]], normals[t[1]], normals[t[2]]), true, false));
            final var bottom = new ArrayList<Vector3d>();
            for (int i = 0; i < 4; i++) {
                final int j = (i + 1) % 4;
                bottom.add(point(U[i], V[i], 0));
                final var side = new ArrayList<>(List.of(point(U[i], V[i], 0), point(U[j], V[j], 0), points[j], points[i]));
                if (sign < 0) java.util.Collections.reverse(side);
                faces.add(new Face(List.copyOf(side), List.of(), false, (joined & (1 << i)) != 0));
            }
            if (sign > 0) java.util.Collections.reverse(bottom);
            faces.add(new Face(List.copyOf(bottom), List.of(), false, false));
            return List.copyOf(faces);
        }
    }
}
