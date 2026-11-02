package org.valkyrienskies.mod.common.fluid;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4dc;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;

/** Conservative whole-volume coverage, with bounded work and reusable render-pass scratch. */
final class WorldFluidCoverage {
    static final int MAX_CELLS = 64;
    private static final int[][] NEIGHBORS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0},
        {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private final ClientLevel level;
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
    private final AABBd bounds = new AABBd();
    private final HeightSampler sampler = this::heightAt;
    private @Nullable FlowingFluid retainedFluid;

    WorldFluidCoverage(final ClientLevel level) {
        this.level = level;
    }

    boolean covers(final Matrix4dc shipToWorld, final VsiFluidFloodedVoxel voxel,
        final @Nullable FlowingFluid fluid) {
        if (fluid == null || voxel.getFillAmount() <= 0) return false;
        retainedFluid = fluid;
        transformedBounds(shipToWorld, voxel, bounds);
        return coversBounds(bounds, sampler);
    }

    private double heightAt(final int x, final int y, final int z) {
        double minimumHeight = rawHeightAt(x, y, z);
        if (minimumHeight <= 0.0 || minimumHeight >= 1.0) return minimumHeight;
        // Vanilla averages horizontal neighbors at surface corners. Their minimum is a
        // conservative lower bound on every corner, including a sloping edge next to air.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                minimumHeight = Math.min(minimumHeight, rawHeightAt(x + dx, y, z + dz));
                if (minimumHeight <= 0.0) return minimumHeight;
            }
        }
        return minimumHeight;
    }

    private double rawHeightAt(final int x, final int y, final int z) {
        position.set(x, y, z);
        if (!level.hasChunkAt(position)) return -1.0;
        final FluidState state = level.getFluidState(position);
        if (state.isEmpty() || !retainedFluid.isSame(state.getType())) return -1.0;
        return state.getHeight(level, position);
    }

    /** Ambient world blocks cannot hide a surface bordering protected compartment air. */
    static boolean hasProtectedBoundary(final VsiFluidFloodedVoxel voxel,
        final @Nullable FluidTopologyClientCache.CachedSnapshot topology,
        final FloodedFluidClientCache.CachedSnapshot flooding) {
        if (voxel.getFillAmount() < 255 || topology == null) return true;
        final var domain = topology.getSnapshot();
        final var water = flooding.getSnapshot();
        if (!domain.getEnabled() || !domain.getValid() || domain.getSourceRevision() != water.getSourceRevision()
            || domain.getTotalDomainVoxelCount() > domain.getVoxels().size()
            || water.getTotalFloodedVoxelCount() > water.getVoxels().size()) return true;
        for (final int[] offset : NEIGHBORS) {
            final int x = voxel.getPositionX() + offset[0];
            final int y = voxel.getPositionY() + offset[1];
            final int z = voxel.getPositionZ() + offset[2];
            if (topology.getDryCellFillAmount(x, y, z, flooding) >= 0) return true;
            final var neighbor = flooding.get(x, y, z);
            if (neighbor != null && neighbor.getFluidId() != voxel.getFluidId()) return true;
        }
        return false;
    }

    static void transformedBounds(final Matrix4dc transform, final VsiFluidFloodedVoxel voxel,
        final AABBd result) {
        // Enclose the entire cell volume, rather than just the inset rendered surface.
        result.minX = voxel.getPositionX();
        result.minY = voxel.getPositionY();
        result.minZ = voxel.getPositionZ();
        result.maxX = result.minX + 1.0;
        result.maxY = result.minY + Math.min(255, voxel.getFillAmount()) / 255.0;
        result.maxZ = result.minZ + 1.0;
        result.transform(transform, result);
    }

    @FunctionalInterface
    interface HeightSampler {
        /** Local fluid height, or a negative value for missing/incompatible/unloaded fluid. */
        double height(int x, int y, int z);
    }

    static boolean coversBounds(final AABBd bounds, final HeightSampler sampler) {
        if (!(bounds.minX < bounds.maxX && bounds.minY < bounds.maxY && bounds.minZ < bounds.maxZ)
            || bounds.minX <= Integer.MIN_VALUE || bounds.minY <= Integer.MIN_VALUE
            || bounds.minZ <= Integer.MIN_VALUE || bounds.maxX >= Integer.MAX_VALUE
            || bounds.maxY >= Integer.MAX_VALUE || bounds.maxZ >= Integer.MAX_VALUE) return false;
        final int minX = (int) Math.floor(bounds.minX);
        final int minY = (int) Math.floor(bounds.minY);
        final int minZ = (int) Math.floor(bounds.minZ);
        final int maxX = (int) Math.floor(Math.nextDown(bounds.maxX));
        final int maxY = (int) Math.floor(Math.nextDown(bounds.maxY));
        final int maxZ = (int) Math.floor(Math.nextDown(bounds.maxZ));
        final long sx = (long) maxX - minX + 1;
        final long sy = (long) maxY - minY + 1;
        final long sz = (long) maxZ - minZ + 1;
        if (sx > MAX_CELLS || sy > MAX_CELLS / sx || sz > MAX_CELLS / (sx * sy)) return false;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    final double height = sampler.height(x, y, z);
                    if (!(height > 0.0 && y + Math.min(1.0, height) >= Math.min(bounds.maxY, y + 1.0))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
