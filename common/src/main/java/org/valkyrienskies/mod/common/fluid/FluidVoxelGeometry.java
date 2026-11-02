package org.valkyrienskies.mod.common.fluid;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel;

public final class FluidVoxelGeometry {
    private FluidVoxelGeometry() { }

    public static double minimum(final long bounds, final int axis) {
        return ((bounds >>> (axis * 5)) & 31) / 16.0;
    }

    public static double maximum(final long bounds, final int axis) {
        return ((bounds >>> (15 + axis * 5)) & 31) / 16.0;
    }

    private static boolean contains(final long bounds, final double x, final double y, final double z) {
        return x >= minimum(bounds, 0) && x < maximum(bounds, 0)
            && y >= minimum(bounds, 1) && y < maximum(bounds, 1)
            && z >= minimum(bounds, 2) && z < maximum(bounds, 2);
    }

    public static boolean contains(final VsiFluidTopologyVoxel voxel, final double x, final double y, final double z) {
        if (voxel.getRegions().isEmpty()) return true;
        for (final long region : voxel.getRegions()) if (contains(region, x, y, z)) return true;
        return false;
    }

    public static int fluidAt(final VsiFluidFloodedVoxel voxel, final double x, final double y, final double z) {
        if (voxel.getRegions().isEmpty()) {
            return FloodedFluidClientCache.isLocallySubmerged(voxel.getFillAmount(), y) ? voxel.getFluidId() : 0;
        }
        for (final var region : voxel.getRegions()) {
            if (y < region.getHeight() && contains(region.getBounds(), x, y, z)) return region.getFluidId();
        }
        return 0;
    }

    public static void forEachDomain(final VsiFluidTopologyVoxel voxel, final Consumer<AABB> visit) {
        final int x = voxel.getPositionX(), y = voxel.getPositionY(), z = voxel.getPositionZ();
        if (voxel.getRegions().isEmpty()) visit.accept(new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0));
        else for (final long region : voxel.getRegions()) visit.accept(new AABB(
            x + minimum(region, 0), y + minimum(region, 1), z + minimum(region, 2),
            x + maximum(region, 0), y + maximum(region, 1), z + maximum(region, 2)));
    }

    public static void forEachFlooded(final VsiFluidFloodedVoxel voxel, final BiConsumer<Integer, AABB> visit) {
        final int x = voxel.getPositionX(), y = voxel.getPositionY(), z = voxel.getPositionZ();
        if (voxel.getRegions().isEmpty()) {
            if (voxel.getFillAmount() > 0) visit.accept(voxel.getFluidId(), new AABB(x, y, z,
                x + 1.0, y + Math.min(255, voxel.getFillAmount()) / 255.0, z + 1.0));
        } else for (final var region : voxel.getRegions()) {
            final double bottom = minimum(region.getBounds(), 1), top = Math.min(maximum(region.getBounds(), 1), region.getHeight());
            if (top <= bottom) continue;
            visit.accept(region.getFluidId(), new AABB(
                x + minimum(region.getBounds(), 0), y + bottom, z + minimum(region.getBounds(), 2),
                x + maximum(region.getBounds(), 0), y + top, z + maximum(region.getBounds(), 2)));
        }
    }
}
