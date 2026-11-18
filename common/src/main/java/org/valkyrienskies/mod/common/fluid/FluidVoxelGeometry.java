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

    static org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion withoutCurrent(
        final org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion region) {
        return region.getCurrent() == null ? region : new org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion(
            region.getBounds(), region.getNormalX(), region.getNormalY(), region.getNormalZ(),
            region.getLowerProjection(), region.getUpperProjection(), region.getConstituents());
    }

    static java.util.List<org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion> withoutCurrents(
        final java.util.List<org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion> regions) {
        for (final var region : regions) if (region.getCurrent() != null)
            return regions.stream().map(FluidVoxelGeometry::withoutCurrent).toList();
        return regions;
    }

    static boolean sameSurfaceGeometry(final java.util.List<org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion> a,
        final java.util.List<org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion> b) {
        if (a == b) return true;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); ++i) {
            final var x = a.get(i); final var y = b.get(i);
            if (x.getBounds() != y.getBounds() || x.getNormalX() != y.getNormalX() || x.getNormalY() != y.getNormalY()
                || x.getNormalZ() != y.getNormalZ() || x.getLowerProjection() != y.getLowerProjection()
                || x.getUpperProjection() != y.getUpperProjection() || !x.getConstituents().equals(y.getConstituents())) return false;
        }
        return true;
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
        return fluidAt(voxel, x, y, z, false);
    }

    public static int fluidAt(final VsiFluidFloodedVoxel voxel, final double x, final double y, final double z,
        final boolean clientSide) {
        if (!voxel.getSurfaceRegions().isEmpty()) {
            for (final var region : voxel.getSurfaceRegions()) {
                final double projection = region.getNormalX() * x + region.getNormalY() * y + region.getNormalZ() * z;
                if (contains(region.getBounds(), x, y, z) && projection >= region.getLowerProjection()
                    && projection <= region.getUpperProjection()) return dominantFluid(region, clientSide);
            }
            return 0;
        }
        if (voxel.getRegions().isEmpty()) {
            return FloodedFluidClientCache.isLocallySubmerged(voxel.getFillAmount(), y) ? voxel.getFluidId() : 0;
        }
        for (final var region : voxel.getRegions()) {
            if (y < region.getHeight() && contains(region.getBounds(), x, y, z)) return region.getFluidId();
        }
        return 0;
    }

    static int dominantFluid(final org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion region,
        final boolean clientSide) {
        final var parts = region.getConstituents();
        if (parts.size() == 1) return parts.get(0).getFluidId();
        final var materials = new java.util.HashMap<Object, MaterialAmount>();
        for (final var part : parts) {
            final var key = material(part.getFluidId(), clientSide);
            final var total = materials.computeIfAbsent(key, ignored -> new MaterialAmount(part.getFluidId()));
            total.amount += part.getAmount();
            total.id = Math.min(total.id, part.getFluidId());
        }
        MaterialAmount best = null;
        for (final var total : materials.values()) {
            if (best == null || total.amount > best.amount || total.amount == best.amount && total.id < best.id) best = total;
        }
        return java.util.Objects.requireNonNull(best).id;
    }

    static boolean sameMaterial(final int first, final int second, final boolean clientSide) {
        return first == second || material(first, clientSide).equals(material(second, clientSide));
    }

    private static Object material(final int id, final boolean clientSide) {
        final var fluid = org.valkyrienskies.mod.common.config.MassDatapackResolver.INSTANCE.getFlowingFluid(id, clientSide);
        return fluid == null ? Integer.valueOf(id) : fluid.getSource();
    }

    private static final class MaterialAmount {
        private int id;
        private double amount;

        private MaterialAmount(final int id) { this.id = id; }
    }

    static AABB localBounds(final int bounds) {
        return new AABB(minimum(bounds, 0), minimum(bounds, 1), minimum(bounds, 2),
            maximum(bounds, 0), maximum(bounds, 1), maximum(bounds, 2));
    }

    static java.util.List<java.util.List<org.joml.Vector3d>> faces(
        final org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion region) {
        if (region.getUpperProjection() - region.getLowerProjection() <= 1e-12) return java.util.List.of();
        return FluidSurfaceGeometry.clip(localBounds(region.getBounds()),
            new FluidSurfaceGeometry.Plane(region.getNormalX(), region.getNormalY(), region.getNormalZ(), region.getUpperProjection()),
            new FluidSurfaceGeometry.Plane(region.getNormalX(), region.getNormalY(), region.getNormalZ(), region.getLowerProjection()));
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
