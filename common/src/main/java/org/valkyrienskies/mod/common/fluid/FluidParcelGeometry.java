package org.valkyrienskies.mod.common.fluid;

import java.util.ArrayList;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class FluidParcelGeometry {
    private static final List<List<Vector3d>> UNIT = unitMesh();

    private FluidParcelGeometry() { }

    public static List<List<Vector3d>> faces(final double volume, final Vector3dc velocity) {
        if (!(volume > 0) || !Double.isFinite(volume)) return List.of();
        final double speed = velocity.length();
        final double stretch = 1 + Math.min(0.75, speed / 40);
        final double radius = Math.cbrt(volume);
        final double width = radius / Math.sqrt(stretch);
        final var rotation = new Quaterniond();
        if (speed > 1e-8 && Double.isFinite(speed)) rotation.rotationTo(0, 1, 0,
            velocity.x() / speed, velocity.y() / speed, velocity.z() / speed);
        final var result = new ArrayList<List<Vector3d>>(UNIT.size());
        for (final var face : UNIT) {
            final var points = new ArrayList<Vector3d>(3);
            for (final var point : face) points.add(new Vector3d(point).mul(width, radius * stretch, width).rotate(rotation));
            result.add(points);
        }
        return result;
    }

    private static List<List<Vector3d>> unitMesh() {
        final var faces = new ArrayList<List<Vector3d>>();
        for (int latitude = 0; latitude < 4; latitude++) {
            for (int longitude = 0; longitude < 8; longitude++) {
                final var a = point(latitude, longitude);
                final var b = point(latitude + 1, longitude);
                final var c = point(latitude + 1, longitude + 1);
                final var d = point(latitude, longitude + 1);
                if (latitude != 3) faces.add(List.of(a, b, c));
                if (latitude != 0) faces.add(List.of(a, c, d));
            }
        }
        double volume = 0;
        for (final var face : faces) volume += face.get(0).dot(new Vector3d(face.get(1)).cross(face.get(2))) / 6;
        final double scale = 1 / Math.cbrt(Math.abs(volume));
        for (int i = 0; i < faces.size(); i++) {
            final var face = faces.get(i);
            final var scaled = face.stream().map(point -> new Vector3d(point).mul(scale)).toList();
            faces.set(i, volume < 0 ? List.of(scaled.get(2), scaled.get(1), scaled.get(0)) : scaled);
        }
        return List.copyOf(faces);
    }

    private static Vector3d point(final int latitude, final int longitude) {
        final double angle = Math.PI * latitude / 4;
        final double around = Math.PI * longitude / 4;
        return new Vector3d(Math.sin(angle) * Math.cos(around), Math.cos(angle), Math.sin(angle) * Math.sin(around));
    }

    public record Tile(List<Vector3d> points, Vector3d normal, int u, int v, double originU, double originV, double size) { }

    public static List<Tile> tiles(final List<Vector3d> face) {
        final var normal = new Vector3d(face.get(1)).sub(face.get(0)).cross(new Vector3d(face.get(2)).sub(face.get(0)));
        final double magnitude = Math.max(Math.abs(normal.x), Math.max(Math.abs(normal.y), Math.abs(normal.z)));
        if (!(magnitude > 0) || !Double.isFinite(magnitude)) return List.of();
        normal.div(magnitude).normalize();
        final int axis = normal.absolute(new Vector3d()).maxComponent();
        final int u = axis == 0 ? 2 : 0, v = axis == 1 ? 2 : 1;
        final double minU = face.stream().mapToDouble(p -> p.get(u)).min().orElseThrow();
        final double maxU = face.stream().mapToDouble(p -> p.get(u)).max().orElseThrow();
        final double minV = face.stream().mapToDouble(p -> p.get(v)).min().orElseThrow();
        final double maxV = face.stream().mapToDouble(p -> p.get(v)).max().orElseThrow();
        final double size = Math.max(1, Math.ceil(Math.max(maxU - minU, maxV - minV) / 8));
        final double startU = Math.floor(minU / size) * size, startV = Math.floor(minV / size) * size;
        final var result = new ArrayList<Tile>();
        for (int i = 0; i < 10; i++) {
            final double x = startU + i * size;
            if (x >= maxU) break;
            for (int j = 0; j < 10; j++) {
                final double y = startV + j * size;
                if (y >= maxV) break;
                var points = clip(face, u, x, false);
                points = clip(points, u, x + size, true);
                points = clip(points, v, y, false);
                points = clip(points, v, y + size, true);
                if (points.size() >= 3) result.add(new Tile(points, normal, u, v, x, y, size));
            }
        }
        return result;
    }

    private static List<Vector3d> clip(final List<Vector3d> points, final int axis, final double limit, final boolean below) {
        final var result = new ArrayList<Vector3d>();
        for (int i = 0; i < points.size(); i++) {
            final var a = points.get(i);
            final var b = points.get((i + 1) % points.size());
            final double da = (a.get(axis) - limit) * (below ? 1 : -1);
            final double db = (b.get(axis) - limit) * (below ? 1 : -1);
            if (da <= 0) result.add(a);
            if (da < 0 && db > 0 || da > 0 && db < 0) result.add(new Vector3d(a).lerp(b, da / (da - db)));
        }
        return result;
    }
}
