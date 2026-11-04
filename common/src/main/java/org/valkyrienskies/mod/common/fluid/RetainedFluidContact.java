package org.valkyrienskies.mod.common.fluid;

import java.util.function.IntPredicate;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion;

final class RetainedFluidContact {
    static final double DESTRUCTION_VOLUME = 1.0 / 8.0;
    private static final AABB CELL = new AABB(0, 0, 0, 1, 1, 1);

    private RetainedFluidContact() { }

    static double volume(final VsiFluidFloodedVoxel voxel, final IntPredicate material) {
        if (!voxel.getSurfaceRegions().isEmpty()) {
            double amount = 0;
            for (final var surface : voxel.getSurfaceRegions())
                for (final var part : surface.getConstituents())
                    if (material.test(part.getFluidId())) amount += part.getAmount();
            return amount / 255.0;
        }
        if (voxel.getRegions().isEmpty())
            return material.test(voxel.getFluidId()) ? Math.max(0, Math.min(255, voxel.getFillAmount())) / 255.0 : 0;
        double volume = 0;
        for (final var region : voxel.getRegions()) {
            if (!material.test(region.getFluidId())) continue;
            final AABB bounds = FluidVoxelGeometry.localBounds(region.getBounds());
            final double height = Math.max(0, Math.min(bounds.maxY, region.getHeight()) - bounds.minY);
            volume += bounds.getXsize() * bounds.getZsize() * height;
        }
        return volume;
    }

    static boolean touches(final VsiFluidFloodedVoxel voxel, final AABB shape, final IntPredicate material) {
        if (!voxel.getSurfaceRegions().isEmpty()) {
            for (final var surface : voxel.getSurfaceRegions()) {
                if (surface.getConstituents().stream().noneMatch(part -> material.test(part.getFluidId()))) continue;
                if (touches(surface, shape)) return true;
            }
            return false;
        }
        if (voxel.getRegions().isEmpty()) {
            if (!material.test(voxel.getFluidId()) || voxel.getFillAmount() <= 0) return false;
            return boxesTouch(new AABB(0, 0, 0, 1, Math.min(255, voxel.getFillAmount()) / 255.0, 1), shape);
        }
        for (final var region : voxel.getRegions()) {
            if (!material.test(region.getFluidId())) continue;
            final AABB bounds = FluidVoxelGeometry.localBounds(region.getBounds());
            final double top = Math.min(bounds.maxY, region.getHeight());
            if (top > bounds.minY && boxesTouch(new AABB(bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, top, bounds.maxZ), shape)) return true;
        }
        return false;
    }

    static boolean canDestroy(final VsiFluidFloodedVoxel voxel, final Iterable<AABB> shapes,
        final IntPredicate water) {
        if (volume(voxel, water) < DESTRUCTION_VOLUME) return false;
        boolean any = false;
        for (final AABB shape : shapes) {
            any = true;
            if (touches(voxel, shape, water)) return true;
        }
        return !any && touches(voxel, CELL, water);
    }

    static boolean materialsTouch(final VsiFluidFloodedVoxel first, final IntPredicate firstMaterial,
        final VsiFluidFloodedVoxel second, final IntPredicate secondMaterial) {
        final int x = second.getPositionX() - first.getPositionX();
        final int y = second.getPositionY() - first.getPositionY();
        final int z = second.getPositionZ() - first.getPositionZ();
        if (first.getSurfaceRegions().isEmpty() || second.getSurfaceRegions().isEmpty()) {
            if (first == second) return false;
            final AABB face = CELL.intersect(CELL.move(x, y, z));
            return touches(first, face, firstMaterial) && touches(second, face.move(-x, -y, -z), secondMaterial);
        }
        for (final var a : first.getSurfaceRegions()) {
            if (a.getConstituents().stream().noneMatch(part -> firstMaterial.test(part.getFluidId()))) continue;
            final AABB boundsA = FluidVoxelGeometry.localBounds(a.getBounds());
            for (final var b : second.getSurfaceRegions()) {
                if (b.getConstituents().stream().noneMatch(part -> secondMaterial.test(part.getFluidId()))) continue;
                final AABB boundsB = FluidVoxelGeometry.localBounds(b.getBounds()).move(x, y, z);
                if (!boxesTouch(boundsA, boundsB)) continue;
                final AABB overlap = boundsA.intersect(boundsB);
                final double nx = a.getNormalX(), ny = a.getNormalY(), nz = a.getNormalZ();
                if (Math.abs(nx - b.getNormalX()) + Math.abs(ny - b.getNormalY()) +
                    Math.abs(nz - b.getNormalZ()) > 1e-8) continue;
                final double shift = nx * x + ny * y + nz * z;
                final double low = Math.max(a.getLowerProjection(), b.getLowerProjection() + shift);
                final double high = Math.min(a.getUpperProjection(), b.getUpperProjection() + shift);
                final double min = nx * (nx >= 0 ? overlap.minX : overlap.maxX) +
                    ny * (ny >= 0 ? overlap.minY : overlap.maxY) + nz * (nz >= 0 ? overlap.minZ : overlap.maxZ);
                final double max = nx * (nx >= 0 ? overlap.maxX : overlap.minX) +
                    ny * (ny >= 0 ? overlap.maxY : overlap.minY) + nz * (nz >= 0 ? overlap.maxZ : overlap.minZ);
                if (low <= high + 1e-8 && min <= high + 1e-8 && max >= low - 1e-8) return true;
            }
        }
        return false;
    }

    private static boolean boxesTouch(final AABB first, final AABB second) {
        final double x = Math.min(first.maxX, second.maxX) - Math.max(first.minX, second.minX);
        final double y = Math.min(first.maxY, second.maxY) - Math.max(first.minY, second.minY);
        final double z = Math.min(first.maxZ, second.maxZ) - Math.max(first.minZ, second.minZ);
        return x >= 0 && y >= 0 && z >= 0 && (x > 0 ? 1 : 0) + (y > 0 ? 1 : 0) + (z > 0 ? 1 : 0) >= 2;
    }

    private static boolean touches(final VsiFluidSurfaceRegion surface, final AABB shape) {
        final AABB region = FluidVoxelGeometry.localBounds(surface.getBounds());
        if (!boxesTouch(region, shape) || surface.getUpperProjection() <= surface.getLowerProjection()) return false;
        final double[] low = {Math.max(region.minX, shape.minX), Math.max(region.minY, shape.minY),
            Math.max(region.minZ, shape.minZ)};
        final double[] high = {Math.min(region.maxX, shape.maxX), Math.min(region.maxY, shape.maxY),
            Math.min(region.maxZ, shape.maxZ)};
        final double[] normal = {surface.getNormalX(), surface.getNormalY(), surface.getNormalZ()};
        double lower = surface.getLowerProjection(), upper = surface.getUpperProjection();
        for (int axis = 0; axis < 3; ++axis) if (low[axis] == high[axis]) {
            lower -= normal[axis] * low[axis];
            upper -= normal[axis] * low[axis];
            normal[axis] = 0;
            low[axis] = 0;
            high[axis] = 1;
        }
        final double length = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        if (length == 0) return lower <= 0 && upper >= 0;
        final AABB intersection = new AABB(low[0], low[1], low[2], high[0], high[1], high[2]);
        final FluidSurfaceGeometry.Plane top = new FluidSurfaceGeometry.Plane(normal[0] / length,
            normal[1] / length, normal[2] / length, upper / length);
        final FluidSurfaceGeometry.Plane bottom = new FluidSurfaceGeometry.Plane(normal[0] / length,
            normal[1] / length, normal[2] / length, lower / length);
        return FluidSurfaceGeometry.volumeBelow(intersection, top) > FluidSurfaceGeometry.volumeBelow(intersection, bottom);
    }
}
