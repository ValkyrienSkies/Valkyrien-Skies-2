package org.valkyrienskies.mod.common.fluid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;

/** Convex clipping in entity-relative coordinates, avoiding subtraction at world/shipyard magnitudes. */
final class FluidVolumeGeometry {
    private FluidVolumeGeometry() {
    }

    record Contact(double top, double volume, Vec3 center) { }

    static final class Box {
        final AABB bounds;
        private Plane[] planes;

        Box(final AABB local, final Matrix4dc toWorld, final Matrix4dc toLocal, final Vec3 origin) {
            final Vector3d localOrigin = toLocal.transformPosition(origin.x, origin.y, origin.z, new Vector3d());
            final var relative = new org.joml.Matrix4d(toWorld).setTranslation(0, 0, 0);
            final AABBd transformed = new AABBd(local.minX - localOrigin.x, local.minY - localOrigin.y, local.minZ - localOrigin.z,
                local.maxX - localOrigin.x, local.maxY - localOrigin.y, local.maxZ - localOrigin.z).transform(relative);
            bounds = new AABB(transformed.minX, transformed.minY, transformed.minZ,
                transformed.maxX, transformed.maxY, transformed.maxZ);
            planes = new Plane[] {
                new Plane(-toLocal.m00(), -toLocal.m10(), -toLocal.m20(), localOrigin.x - local.minX),
                new Plane(toLocal.m00(), toLocal.m10(), toLocal.m20(), local.maxX - localOrigin.x),
                new Plane(-toLocal.m01(), -toLocal.m11(), -toLocal.m21(), localOrigin.y - local.minY),
                new Plane(toLocal.m01(), toLocal.m11(), toLocal.m21(), local.maxY - localOrigin.y),
                new Plane(-toLocal.m02(), -toLocal.m12(), -toLocal.m22(), localOrigin.z - local.minZ),
                new Plane(toLocal.m02(), toLocal.m12(), toLocal.m22(), local.maxZ - localOrigin.z)
            };
        }

        Box(final org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel voxel,
            final org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion surface,
            final Matrix4dc toWorld, final Matrix4dc toLocal, final Vec3 origin) {
            this(FluidVoxelGeometry.localBounds(surface.getBounds()).move(voxel.getPositionX(), voxel.getPositionY(),
                voxel.getPositionZ()), toWorld, toLocal, origin);
            final var localOrigin = toLocal.transformPosition(origin.x, origin.y, origin.z, new Vector3d())
                .sub(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ());
            final double nx = surface.getNormalX(), ny = surface.getNormalY(), nz = surface.getNormalZ();
            final double x = nx * toLocal.m00() + ny * toLocal.m01() + nz * toLocal.m02();
            final double y = nx * toLocal.m10() + ny * toLocal.m11() + nz * toLocal.m12();
            final double z = nx * toLocal.m20() + ny * toLocal.m21() + nz * toLocal.m22();
            final double shift = nx * localOrigin.x + ny * localOrigin.y + nz * localOrigin.z;
            planes = java.util.Arrays.copyOf(planes, 8);
            planes[6] = new Plane(x, y, z, surface.getUpperProjection() - shift);
            planes[7] = new Plane(-x, -y, -z, shift - surface.getLowerProjection());
        }

        double intersectionTop(final AABB query) {
            if (!bounds.intersects(query)) return Double.NaN;
            if (contains(query)) return query.maxY;
            Poly intersection = Poly.box(query);
            for (final Plane plane : planes) {
                intersection = intersection == null ? null : intersection.clip(plane);
            }
            return intersection == null ? Double.NaN : intersection.bounds.maxY;
        }

        Contact contact(final AABB query) {
            if (!bounds.intersects(query)) return null;
            if (contains(query)) return new Contact(query.maxY, query.getXsize() * query.getYsize() * query.getZsize(), query.getCenter());
            Poly intersection = Poly.box(query);
            for (final Plane plane : planes) {
                if (intersection == null) return null;
                intersection = intersection.clip(plane);
            }
            return intersection == null ? null : intersection.contact();
        }

        private boolean contains(final AABB query) {
            for (final Plane plane : planes) {
                final double x = plane.x >= 0 ? query.maxX : query.minX;
                final double y = plane.y >= 0 ? query.maxY : query.minY;
                final double z = plane.z >= 0 ? query.maxZ : query.minZ;
                if (plane.distance(x, y, z) > 0) return false;
            }
            return true;
        }

        private void subtract(final Poly poly, final List<Poly> outside) {
            if (!bounds.intersects(poly.bounds)) {
                outside.add(poly);
                return;
            }
            if (contains(poly.bounds)) return;
            Poly inside = poly;
            for (final Plane plane : planes) {
                final Poly part = inside.clip(plane.reversed());
                if (part != null) outside.add(part);
                inside = inside.clip(plane);
                if (inside == null) return;
            }
        }
    }

    /** Highest point of positive-volume liquid left outside the union of controlled ship domains. */
    static double uncoveredTop(final AABB liquid, final List<Box> domains) {
        List<Poly> pieces = null;
        for (final Box domain : domains) {
            if (!domain.bounds.intersects(liquid)) continue;
            if (domain.contains(liquid)) return Double.NaN;
            if (pieces == null) {
                pieces = new ArrayList<>();
                pieces.add(Poly.box(liquid));
            }
            final List<Poly> outside = new ArrayList<>();
            for (final Poly piece : pieces) domain.subtract(piece, outside);
            pieces = outside;
            if (pieces.isEmpty()) return Double.NaN;
        }
        if (pieces == null) return liquid.maxY;
        double top = Double.NEGATIVE_INFINITY;
        for (final Poly piece : pieces) top = Math.max(top, piece.bounds.maxY);
        return top;
    }

    private record Plane(double x, double y, double z, double offset) {
        double distance(final Vec3 point) {
            return distance(point.x, point.y, point.z);
        }

        double distance(final double px, final double py, final double pz) {
            final double dx = x * px, dy = y * py, dz = z * pz;
            final double distance = dx + dy + dz - offset;
            final double tolerance = Math.ulp(Math.abs(dx) + Math.abs(dy) + Math.abs(dz) + Math.abs(offset)) * 8;
            return Math.abs(distance) <= tolerance ? 0 : distance;
        }

        Plane reversed() {
            return new Plane(-x, -y, -z, -offset);
        }
    }

    private static final class Poly {
        private final List<List<Vec3>> faces;
        private final AABB bounds;

        private Poly(final List<List<Vec3>> faces) {
            this.faces = faces;
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for (final List<Vec3> face : faces) {
                for (final Vec3 p : face) {
                    minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
                    maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
                }
            }
            bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        static Poly box(final AABB b) {
            final Vec3 a = new Vec3(b.minX, b.minY, b.minZ), c = new Vec3(b.maxX, b.minY, b.minZ);
            final Vec3 d = new Vec3(b.maxX, b.maxY, b.minZ), e = new Vec3(b.minX, b.maxY, b.minZ);
            final Vec3 f = new Vec3(b.minX, b.minY, b.maxZ), g = new Vec3(b.maxX, b.minY, b.maxZ);
            final Vec3 h = new Vec3(b.maxX, b.maxY, b.maxZ), i = new Vec3(b.minX, b.maxY, b.maxZ);
            return new Poly(List.of(List.of(a, c, d, e), List.of(f, g, h, i),
                List.of(a, c, g, f), List.of(e, d, h, i), List.of(a, e, i, f), List.of(c, d, h, g)));
        }

        Poly clip(final Plane plane) {
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (final List<Vec3> face : faces) {
                for (final Vec3 p : face) {
                    final double distance = plane.distance(p);
                    min = Math.min(min, distance); max = Math.max(max, distance);
                }
            }
            if (max <= 0) return this;
            if (min >= 0) return null; // Touching a face/edge alone is not immersion.
            final List<List<Vec3>> clipped = new ArrayList<>();
            final List<Vec3> cap = new ArrayList<>();
            for (final List<Vec3> face : faces) {
                final List<Vec3> polygon = new ArrayList<>();
                Vec3 previous = face.get(face.size() - 1);
                double previousDistance = plane.distance(previous);
                for (final Vec3 current : face) {
                    final double distance = plane.distance(current);
                    if ((distance <= 0) != (previousDistance <= 0)) {
                        final double t = previousDistance / (previousDistance - distance);
                        final Vec3 crossing = previous.lerp(current, t);
                        polygon.add(crossing);
                        addUnique(cap, crossing);
                    }
                    if (distance <= 0) polygon.add(current);
                    previous = current;
                    previousDistance = distance;
                }
                if (polygon.size() >= 3) clipped.add(polygon);
            }
            if (cap.size() >= 3) {
                Vec3 sum = Vec3.ZERO;
                for (final Vec3 point : cap) sum = sum.add(point);
                final Vec3 center = sum.scale(1.0 / cap.size());
                final double length = Math.hypot(Math.hypot(plane.x, plane.y), plane.z);
                final Vec3 normal = new Vec3(plane.x / length, plane.y / length, plane.z / length);
                final Vec3 axis = Math.abs(normal.x) < 0.8 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                final Vec3 u = normal.cross(axis).normalize(), v = normal.cross(u);
                cap.sort(Comparator.comparingDouble(p -> Math.atan2(p.subtract(center).dot(v), p.subtract(center).dot(u))));
                clipped.add(cap);
            }
            if (clipped.size() < 4) return null;
            final Poly result = new Poly(clipped);
            return result.hasVolume() ? result : null;
        }

        private boolean hasVolume() {
            final double boxVolume = (bounds.maxX - bounds.minX) * (bounds.maxY - bounds.minY) * (bounds.maxZ - bounds.minZ);
            if (!(boxVolume > 0)) return false;
            final Vec3 origin = faces.get(0).get(0);
            double volume6 = 0;
            for (final List<Vec3> face : faces) {
                final Vec3 a = face.get(0).subtract(origin);
                for (int i = 1; i + 1 < face.size(); i++) {
                    volume6 += Math.abs(a.dot(face.get(i).subtract(origin).cross(face.get(i + 1).subtract(origin))));
                }
            }
            return volume6 > boxVolume * 1.0e-12;
        }

        private Contact contact() {
            final Vec3 origin = faces.get(0).get(0);
            double volume = 0;
            Vec3 moment = Vec3.ZERO;
            for (final List<Vec3> face : faces) {
                final Vec3 a = face.get(0).subtract(origin);
                for (int i = 1; i + 1 < face.size(); i++) {
                    final Vec3 b = face.get(i).subtract(origin), c = face.get(i + 1).subtract(origin);
                    final double part = Math.abs(a.dot(b.cross(c))) / 6;
                    volume += part;
                    moment = moment.add(a.add(b).add(c).scale(part / 4));
                }
            }
            return volume > 0 ? new Contact(bounds.maxY, volume, origin.add(moment.scale(1 / volume))) : null;
        }

        private static void addUnique(final List<Vec3> points, final Vec3 added) {
            for (final Vec3 p : points) {
                final double magnitude = Math.max(Math.max(Math.abs(p.x), Math.abs(p.y)), Math.abs(p.z));
                final double tolerance = Math.ulp(magnitude) * 16;
                if (Math.abs(p.x - added.x) <= tolerance && Math.abs(p.y - added.y) <= tolerance
                    && Math.abs(p.z - added.z) <= tolerance) return;
            }
            points.add(added);
        }
    }
}
