package org.valkyrienskies.mod.common.fluid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

final class FluidSurfaceGeometry {
    private FluidSurfaceGeometry() { }

    record Plane(double x, double y, double z, double offset) {
        Plane {
            if (!Double.isFinite(x + y + z + offset) || Math.abs(x * x + y * y + z * z - 1.0) > 1e-6) {
                throw new IllegalArgumentException("Fluid surface requires a finite unit normal");
            }
        }
        double distance(final Vector3d point) { return x * point.x + y * point.y + z * point.z - offset; }
        Plane inverted() { return new Plane(-x, -y, -z, -offset); }
    }

    static double volumeBelow(final AABB box, final Plane plane) {
        final double[] widths = {Math.abs(plane.x) * box.getXsize(), Math.abs(plane.y) * box.getYsize(),
            Math.abs(plane.z) * box.getZsize()};
        final double minimum = plane.x * (plane.x >= 0 ? box.minX : box.maxX)
            + plane.y * (plane.y >= 0 ? box.minY : box.maxY) + plane.z * (plane.z >= 0 ? box.minZ : box.maxZ);
        double totalWidth = 0.0, product = 1.0;
        int dimensions = 0;
        for (final double width : widths) if (width > 1e-14) {
            widths[dimensions++] = width; totalWidth += width; product *= width;
        }
        double limit = plane.offset - minimum;
        final double volume = box.getXsize() * box.getYsize() * box.getZsize();
        if (limit <= 0.0) return 0.0;
        if (limit >= totalWidth) return volume;
        final boolean complement = limit > totalWidth * 0.5;
        double smallest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < dimensions; i++) smallest = Math.min(smallest, widths[i]);
        if (dimensions > 1 && smallest < totalWidth * 1e-5) {
            double clippedVolume = 0.0;
            final var origin = new Vector3d(box.minX, box.minY, box.minZ);
            for (final var face : clip(box, plane, null)) {
                final var a = new Vector3d(face.get(0)).sub(origin);
                for (int i = 1; i + 1 < face.size(); i++) {
                    final var b = new Vector3d(face.get(i)).sub(origin);
                    final var c = new Vector3d(face.get(i + 1)).sub(origin);
                    clippedVolume += a.dot(b.cross(c)) / 6.0;
                }
            }
            return Math.max(0.0, Math.min(volume, clippedVolume));
        }
        if (complement) limit = totalWidth - limit;
        double sum = 0.0;
        for (int subset = 0; subset < (1 << dimensions); subset++) {
            double excess = limit;
            for (int axis = 0; axis < dimensions; axis++) if ((subset & (1 << axis)) != 0) excess -= widths[axis];
            if (excess > 0.0) sum += (Integer.bitCount(subset) % 2 == 0 ? 1.0 : -1.0) * Math.pow(excess, dimensions);
        }
        final int factorial = dimensions == 3 ? 6 : dimensions == 2 ? 2 : 1;
        double fraction = Math.max(0.0, Math.min(1.0, sum / (factorial * product)));
        if (complement) fraction = 1.0 - fraction;
        return volume * fraction;
    }

    static Plane atVolume(final List<AABB> boxes, final double nx, final double ny, final double nz, final double volume) {
        final double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(length > 0.0) || !Double.isFinite(length) || boxes.isEmpty()) throw new IllegalArgumentException("Invalid fluid domain");
        final double x = nx / length, y = ny / length, z = nz / length;
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY, capacity = 0.0;
        for (final var box : boxes) {
            low = Math.min(low, x * (x >= 0 ? box.minX : box.maxX) + y * (y >= 0 ? box.minY : box.maxY)
                + z * (z >= 0 ? box.minZ : box.maxZ));
            high = Math.max(high, x * (x >= 0 ? box.maxX : box.minX) + y * (y >= 0 ? box.maxY : box.minY)
                + z * (z >= 0 ? box.maxZ : box.minZ));
            capacity += box.getXsize() * box.getYsize() * box.getZsize();
        }
        if (!Double.isFinite(volume) || volume < 0.0 || volume > capacity + 1e-10 * Math.max(1.0, capacity)) {
            throw new IllegalArgumentException("Fluid volume exceeds its domain");
        }
        if (volume == 0.0) return new Plane(x, y, z, low);
        if (volume >= capacity) return new Plane(x, y, z, high);
        for (int iteration = 0; iteration < 64; iteration++) {
            final double middle = low + (high - low) * 0.5;
            final var plane = new Plane(x, y, z, middle);
            double filled = 0.0;
            for (final var box : boxes) filled += volumeBelow(box, plane);
            if (filled < volume) low = middle; else high = middle;
        }
        return new Plane(x, y, z, low + (high - low) * 0.5);
    }

    static List<List<Vector3d>> clip(final AABB box, final Plane top, final Plane bottom) {
        final Vector3d[] v = new Vector3d[8];
        for (int i = 0; i < 8; i++) v[i] = new Vector3d((i & 1) == 0 ? box.minX : box.maxX,
            (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
        final int[][] faces = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
        List<List<Vector3d>> polygons = new ArrayList<>();
        for (final var face : faces) polygons.add(List.of(v[face[0]], v[face[1]], v[face[2]], v[face[3]]));
        if (top != null) polygons = clip(polygons, top);
        if (bottom != null) polygons = clip(polygons, bottom.inverted());
        return polygons;
    }

    private static List<List<Vector3d>> clip(final List<List<Vector3d>> faces, final Plane plane) {
        final List<List<Vector3d>> result = new ArrayList<>();
        final List<Vector3d> cap = new ArrayList<>();
        boolean outside = false, inside = false;
        for (final var face : faces) {
            final List<Vector3d> clipped = new ArrayList<>();
            for (int i = 0; i < face.size(); i++) {
                final var a = face.get(i);
                final var b = face.get((i + 1) % face.size());
                final double da = plane.distance(a), db = plane.distance(b);
                outside |= da > 0.0; inside |= da < 0.0;
                if (da <= 0.0) clipped.add(a);
                if ((da < 0.0 && db > 0.0) || (da > 0.0 && db < 0.0)) {
                    final var intersection = new Vector3d(a).lerp(b, da / (da - db));
                    clipped.add(intersection);
                    if (cap.stream().noneMatch(point -> point.distanceSquared(intersection) < 1e-24)) cap.add(intersection);
                } else if (da == 0.0 && cap.stream().noneMatch(point -> point.distanceSquared(a) < 1e-24)) cap.add(a);
            }
            if (clipped.size() >= 3) result.add(clipped);
        }
        if (outside && inside && cap.size() >= 3) {
            final Vector3d center = new Vector3d();
            for (final var point : cap) center.add(point);
            center.div(cap.size());
            final var normal = new Vector3d(plane.x, plane.y, plane.z);
            final var u = normal.cross(Math.abs(plane.x) < 0.8 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0), new Vector3d()).normalize();
            final var w = normal.cross(u, new Vector3d());
            cap.sort(Comparator.comparingDouble(point -> {
                final var delta = new Vector3d(point).sub(center);
                return Math.atan2(delta.dot(w), delta.dot(u));
            }));
            result.add(cap);
        }
        return result;
    }
}
