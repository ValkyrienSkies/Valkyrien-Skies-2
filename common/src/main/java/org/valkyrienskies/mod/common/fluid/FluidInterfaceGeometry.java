package org.valkyrienskies.mod.common.fluid;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;

final class FluidInterfaceGeometry {
    private FluidInterfaceGeometry() { }

    record Face(List<Vector3d> points, boolean interfaceFace) { }

    static List<Face> partition(final RetainedFluidRenderer.Cell cell, final List<Vector3d> face,
        final Vector3d normal, final boolean includeSelf) {
        List<Face> result = List.of(new Face(face, false));
        final var candidates = new ArrayList<VsiFluidFloodedVoxel>(cell.neighbors());
        if (includeSelf) candidates.add(cell.voxel());
        final int material = FluidVoxelGeometry.dominantFluid(cell.surface(), true);
        for (final var other : candidates) {
            if (other == null) continue;
            final var offset = new Vector3d(other.getPositionX() - cell.voxel().getPositionX(),
                other.getPositionY() - cell.voxel().getPositionY(), other.getPositionZ() - cell.voxel().getPositionZ());
            for (final var region : other.getSurfaceRegions()) {
                final boolean sameMaterial = FluidVoxelGeometry.sameMaterial(material, FluidVoxelGeometry.dominantFluid(region, true), true);
                if (sameMaterial && other == cell.voxel() && region.equals(cell.surface())) continue;
                for (final var boundary : FluidVoxelGeometry.faces(region)) {
                    final var direction = new Vector3d(boundary.get(1)).sub(boundary.get(0))
                        .cross(new Vector3d(boundary.get(2)).sub(boundary.get(0)));
                    if (direction.lengthSquared() < 1e-24 || direction.normalize().dot(normal) > -1.0 + 1e-10) continue;
                    final var translated = boundary.stream().map(p -> new Vector3d(p).add(offset)).toList();
                    if (translated.stream().anyMatch(p -> Math.abs(new Vector3d(p).sub(face.get(0)).dot(normal)) > 1e-9)) continue;
                    final List<Face> next = new ArrayList<>();
                    for (final var piece : result) {
                        if (piece.interfaceFace()) next.add(piece);
                        else partition(piece.points(), translated, direction, next, !sameMaterial);
                    }
                    result = next;
                }
            }
        }
        return result;
    }

    private static void partition(final List<Vector3d> face, final List<Vector3d> boundary,
        final Vector3d normal, final List<Face> result, final boolean retainInterface) {
        List<Vector3d> inside = face;
        final List<Face> outside = new ArrayList<>();
        for (int i = 0; i < boundary.size(); i++) {
            final var a = boundary.get(i);
            final var edgeNormal = new Vector3d(boundary.get((i + 1) % boundary.size())).sub(a).cross(normal);
            if (edgeNormal.lengthSquared() < 1e-24) continue;
            edgeNormal.normalize();
            final var plane = new FluidSurfaceGeometry.Plane(edgeNormal.x, edgeNormal.y, edgeNormal.z, edgeNormal.dot(a));
            final var cut = clip(inside, plane, false);
            if (hasArea(cut)) outside.add(new Face(cut, false));
            inside = clip(inside, plane, true);
            if (!hasArea(inside)) {
                result.add(new Face(face, false));
                return;
            }
        }
        result.addAll(outside);
        if (retainInterface) result.add(new Face(inside, true));
    }

    private static List<Vector3d> clip(final List<Vector3d> polygon, final FluidSurfaceGeometry.Plane plane,
        final boolean inside) {
        final List<Vector3d> result = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            final var a = polygon.get(i);
            final var b = polygon.get((i + 1) % polygon.size());
            final double da = plane.distance(a), db = plane.distance(b);
            if (inside ? da <= 0 : da >= 0) result.add(a);
            if (da < 0 && db > 0 || da > 0 && db < 0) result.add(new Vector3d(a).lerp(b, da / (da - db)));
        }
        return result;
    }

    private static boolean hasArea(final List<Vector3d> polygon) {
        for (int i = 1; i + 1 < polygon.size(); i++) {
            if (new Vector3d(polygon.get(i)).sub(polygon.get(0))
                .cross(new Vector3d(polygon.get(i + 1)).sub(polygon.get(0))).lengthSquared() > 1e-24) return true;
        }
        return false;
    }
}
