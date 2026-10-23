package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodingSnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidGameplaySnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologySnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.core.internal.world.VsiShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;

/**
 * Shared client/server view of the fluid occupying a ship topology domain.
 *
 * <p>Flood snapshots do not currently export a velocity field. Entity interaction therefore
 * supplies immersion and vanilla fluid behavior without adding an artificial current.</p>
 */
public final class ShipFluidInteraction {
    private static final PointSample OUTSIDE = new PointSample(false, null);
    private static final Map<VsiShipWorld, Long2ObjectOpenHashMap<CachedBody>> CACHES =
        new WeakHashMap<>();

    private ShipFluidInteraction() {
    }

    public static PointSample samplePoint(final Level level, final Vec3 worldPosition) {
        final VsiShipWorld shipWorld = shipWorld(level);
        if (shipWorld == null) return OUTSIDE;

        final AABBd queryBounds = new AABBd(
            worldPosition.x - 0.001,
            worldPosition.y - 0.001,
            worldPosition.z - 0.001,
            worldPosition.x + 0.001,
            worldPosition.y + 0.001,
            worldPosition.z + 0.001
        );
        final Vector3d local = new Vector3d();
        PointSample drySample = OUTSIDE;
        for (final Ship ship : shipWorld.getAllShips().getIntersecting(
            queryBounds, VSGameUtilsKt.getDimensionId(level)
        )) {
            final CachedBody body = bodyFor(shipWorld, ship.getId());
            if (body == null) continue;

            ship.getWorldToShip().transformPosition(
                worldPosition.x, worldPosition.y, worldPosition.z, local
            );
            final LocalPointSample localSample = body.sampleLocal(local.x, local.y, local.z);
            if (!localSample.insideDomain()) continue;

            final FlowingFluid fluid = localSample.fluidId() == null
                ? null
                : MassDatapackResolver.INSTANCE.getFlowingFluid(localSample.fluidId());
            if (fluid != null) {
                return new PointSample(true, fluid);
            }
            drySample = new PointSample(true, null);
        }
        return drySample;
    }

    /** Prepare one immutable query for the platform's original fluid loop. */
    public static VolumeQuery prepareVolume(final Level level, final AABB worldBounds) {
        final VsiShipWorld world = shipWorld(level);
        if (world == null) return new VolumeQuery(worldBounds);
        return prepareVolume(world, world.getAllShips().getIntersecting(new AABBd(
            worldBounds.minX, worldBounds.minY, worldBounds.minZ,
            worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ
        ), VSGameUtilsKt.getDimensionId(level)), worldBounds);
    }

    static VolumeQuery prepareVolume(final VsiShipWorld world, final Iterable<? extends Ship> ships,
        final AABB worldBounds) {
        final VolumeQuery result = new VolumeQuery(worldBounds);
        if (!result.valid) return result;
        for (final Ship ship : ships) {
            final CachedBody body = bodyFor(world, ship.getId());
            if (body == null) continue;
            result.bodies.put(ship.getId(), body);
            final var toWorld = ship.getShipToWorld();
            final var toLocal = ship.getWorldToShip();
            final AABBd transformed = new AABBd(worldBounds.minX, worldBounds.minY, worldBounds.minZ,
                worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ).transform(toLocal);
            final AABB localQuery = new AABB(transformed.minX, transformed.minY, transformed.minZ,
                transformed.maxX, transformed.maxY, transformed.maxZ);
            // Use local hash probes for small queries, sparse content for huge inverse-transformed volumes.
            body.forEachDomain(localQuery, domain -> {
                final FluidVolumeGeometry.Box box = new FluidVolumeGeometry.Box(domain, toWorld, toLocal, result.origin);
                if (box.bounds.intersects(result.relativeBounds)) result.domains.add(box);
            });
            body.forEachFlooded(localQuery, voxel -> {
                final FlowingFluid fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(voxel.getFluidId());
                if (fluid == null || result.retained.getDouble(fluid) >= result.relativeBounds.maxY) return;
                final AABB local = new AABB(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ(),
                    voxel.getPositionX() + 1.0, voxel.getPositionY() + Math.min(255, voxel.getFillAmount()) / 255.0,
                    voxel.getPositionZ() + 1.0);
                final double top = new FluidVolumeGeometry.Box(local, toWorld, toLocal, result.origin)
                    .intersectionTop(result.relativeBounds);
                if (Double.isFinite(top) && top > 0) {
                    result.retained.put(fluid, Math.max(result.retained.getDouble(fluid), top));
                }
            });
        }
        return result;
    }

    public static final class VolumeQuery {
        private final Vec3 origin;
        private final AABB relativeBounds;
        private final boolean valid;
        private final List<FluidVolumeGeometry.Box> domains = new ArrayList<>();
        private final Long2ObjectOpenHashMap<CachedBody> bodies = new Long2ObjectOpenHashMap<>();
        private final Object2DoubleOpenHashMap<FlowingFluid> retained =
            new Object2DoubleOpenHashMap<>();

        private VolumeQuery(final AABB bounds) {
            origin = new Vec3(bounds.minX, bounds.minY, bounds.minZ);
            relativeBounds = bounds.move(-origin.x, -origin.y, -origin.z);
            valid = Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ)
                && relativeBounds.maxX > 0 && relativeBounds.maxY > 0 && relativeBounds.maxZ > 0;
        }

        /** NaN means this world-fluid cell has no positive-volume contact outside controlled ship space. */
        public float worldHeight(final BlockPos block, final float height) {
            if (domains.isEmpty() || !valid) return height;
            final double minX = Math.max(0, block.getX() - origin.x);
            final double minY = Math.max(0, block.getY() - origin.y);
            final double minZ = Math.max(0, block.getZ() - origin.z);
            final double maxX = Math.min(relativeBounds.maxX, block.getX() + 1.0 - origin.x);
            final double maxY = Math.min(relativeBounds.maxY, block.getY() + (double) height - origin.y);
            final double maxZ = Math.min(relativeBounds.maxZ, block.getZ() + 1.0 - origin.z);
            if (minX >= maxX || minY >= maxY || minZ >= maxZ) return Float.NaN;
            final AABB liquid = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            boolean intersects = false;
            for (final FluidVolumeGeometry.Box domain : domains) {
                if (domain.bounds.intersects(liquid)) { intersects = true; break; }
            }
            if (!intersects) return height;
            final double top = FluidVolumeGeometry.uncoveredTop(liquid, domains);
            // Side-only clipping leaves the original world surface/depth intact, including full submersion.
            if (Double.isFinite(top) && top >= Math.nextDown(maxY)) return height;
            return Double.isFinite(top) ? (float) (top + origin.y - block.getY()) : Float.NaN;
        }

        public boolean hasDomains() {
            return !domains.isEmpty();
        }

        /** Retained domains own their local cell; vanilla block fluid must not be reintroduced there. */
        public boolean controlsCell(final long shipId, final BlockPos local) {
            final CachedBody body = bodies.get(shipId);
            return body != null && body.topologyByPosition.containsKey(FloodedFluidClientCache.pack(
                local.getX(), local.getY(), local.getZ()));
        }

        public double retainedDepth(final TagKey<Fluid> tag) {
            double depth = 0;
            for (final var entry : retained.object2DoubleEntrySet()) {
                if (entry.getKey().defaultFluidState().is(tag)) depth = Math.max(depth, entry.getDoubleValue());
            }
            return depth;
        }

        public void forEachRetained(final BiConsumer<FluidState, Double> consumer) {
            for (final var entry : retained.object2DoubleEntrySet()) {
                consumer.accept(entry.getKey().defaultFluidState(), entry.getDoubleValue());
            }
        }
    }


    private static @Nullable VsiShipWorld shipWorld(final Level level) {
        final Object shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        return shipWorld instanceof VsiShipWorld ? (VsiShipWorld) shipWorld : null;
    }

    /** Run once after ship-world updates, including ticks with no fluid queries. */
    public static synchronized void prune(final VsiShipWorld shipWorld) {
        final Long2ObjectOpenHashMap<CachedBody> worldCache = CACHES.get(shipWorld);
        if (worldCache == null) return;
        worldCache.keySet().removeIf(bodyId -> shipWorld.getAllShips().getById(bodyId) == null);
        if (worldCache.isEmpty()) CACHES.remove(shipWorld);
    }

    public static synchronized void clear(final VsiShipWorld shipWorld) {
        CACHES.remove(shipWorld);
    }

    static synchronized @Nullable CachedBody bodyFor(
        final VsiShipWorld shipWorld,
        final long bodyId
    ) {
        final VsiFluidGameplaySnapshot pair;
        if (shipWorld instanceof final VsiClientShipWorld clientWorld) {
            pair = clientWorld.getFluidGameplaySnapshot(bodyId);
        } else if (shipWorld instanceof final VsiServerShipWorld serverWorld) {
            pair = serverWorld.getFluidGameplaySnapshot(bodyId);
        } else {
            return null;
        }

        final Long2ObjectOpenHashMap<CachedBody> worldCache =
            CACHES.computeIfAbsent(shipWorld, ignored -> new Long2ObjectOpenHashMap<>());
        final VsiFluidTopologySnapshot topology = pair == null ? null : pair.getTopology();
        final VsiFluidFloodingSnapshot flooding = pair == null ? null : pair.getFlooding();
        if (topology == null || !topology.getEnabled() || !topology.getValid()) {
            worldCache.remove(bodyId);
            return null;
        }

        final long floodingSequence =
            flooding == null ? Long.MIN_VALUE : flooding.getStreamSequence();
        final CachedBody cached = worldCache.get(bodyId);
        if (cached != null
            && cached.topologySequence == topology.getStreamSequence()
            && cached.floodingSequence == floodingSequence) {
            return cached;
        }

        final CachedBody rebuilt = new CachedBody(topology, flooding, cached);
        worldCache.put(bodyId, rebuilt);
        return rebuilt;
    }

    static int floorToInt(final double value) {
        final int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    public record PointSample(boolean insideDomain, @Nullable FlowingFluid fluid) {
        public boolean flooded() {
            return fluid != null;
        }
    }

    record LocalPointSample(boolean insideDomain, @Nullable Integer fluidId) {
    }

    static final class CachedBody {
        private final long topologySequence;
        private final long floodingSequence;
        private final Long2ObjectOpenHashMap<VsiFluidTopologyVoxel> topologyByPosition;
        private final Long2ObjectOpenHashMap<VsiFluidFloodedVoxel> floodingByPosition;
        private final List<AABB> domainBoxes;

        CachedBody(
            final VsiFluidTopologySnapshot topology,
            final @Nullable VsiFluidFloodingSnapshot flooding
        ) {
            this(topology, flooding, null);
        }

        private CachedBody(final VsiFluidTopologySnapshot topology,
            final @Nullable VsiFluidFloodingSnapshot flooding, final @Nullable CachedBody previous) {
            topologySequence = topology.getStreamSequence();
            floodingSequence =
                flooding == null ? Long.MIN_VALUE : flooding.getStreamSequence();
            floodingByPosition = new Long2ObjectOpenHashMap<>(
                flooding == null ? 0 : flooding.getVoxels().size()
            );

            if (previous != null && previous.topologySequence == topologySequence) {
                topologyByPosition = previous.topologyByPosition;
                domainBoxes = previous.domainBoxes;
            } else {
                topologyByPosition = new Long2ObjectOpenHashMap<>(topology.getVoxels().size());
                for (final VsiFluidTopologyVoxel voxel : topology.getVoxels()) {
                    topologyByPosition.put(FloodedFluidClientCache.pack(
                        voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()), voxel);
                }
                domainBoxes = coalesceDomains(topologyByPosition.values());
            }
            if (flooding != null) {
                for (final VsiFluidFloodedVoxel voxel : flooding.getVoxels()) {
                    if (voxel.getFillAmount() <= 0) continue;
                    floodingByPosition.put(
                        FloodedFluidClientCache.pack(
                            voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()
                        ),
                        voxel
                    );
                }
            }
        }

        /** Exact rectangular runs, cached with the index, reduce clipping across adjacent domain cells. */
        private static List<AABB> coalesceDomains(final Collection<VsiFluidTopologyVoxel> voxels) {
            List<AABB> boxes = new ArrayList<>(voxels.size());
            for (final VsiFluidTopologyVoxel voxel : voxels) {
                boxes.add(new AABB(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ(),
                    voxel.getPositionX() + 1.0, voxel.getPositionY() + 1.0, voxel.getPositionZ() + 1.0));
            }
            for (int axis = 0; axis < 3; axis++) {
                final int mergeAxis = axis;
                boxes.sort((a, b) -> {
                    for (int dim = 0; dim < 3; dim++) {
                        if (dim == mergeAxis) continue;
                        int order = Double.compare(min(a, dim), min(b, dim));
                        if (order != 0) return order;
                        order = Double.compare(max(a, dim), max(b, dim));
                        if (order != 0) return order;
                    }
                    return Double.compare(min(a, mergeAxis), min(b, mergeAxis));
                });
                final List<AABB> merged = new ArrayList<>();
                AABB run = null;
                for (final AABB next : boxes) {
                    boolean adjacent = run != null && max(run, axis) == min(next, axis);
                    for (int dim = 0; adjacent && dim < 3; dim++) {
                        if (dim != axis) adjacent = min(run, dim) == min(next, dim) && max(run, dim) == max(next, dim);
                    }
                    if (adjacent) run = run.minmax(next);
                    else {
                        if (run != null) merged.add(run);
                        run = next;
                    }
                }
                if (run != null) merged.add(run);
                boxes = merged;
            }
            return boxes;
        }

        private static double min(final AABB box, final int axis) {
            return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ;
        }

        private static double max(final AABB box, final int axis) {
            return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
        }

        void forEachFlooded(final AABB bounds, final Consumer<VsiFluidFloodedVoxel> visitor) {
            if (visitIndexedCells(bounds, floodingByPosition.size(), (x, y, z) -> {
                final VsiFluidFloodedVoxel voxel = getFloodedVoxel(x, y, z);
                if (voxel != null) visitor.accept(voxel);
            })) return;
            for (final VsiFluidFloodedVoxel voxel : floodingByPosition.values()) {
                if (voxel.getPositionX() < bounds.maxX && voxel.getPositionX() + 1.0 > bounds.minX
                    && voxel.getPositionY() < bounds.maxY && voxel.getPositionY() + 1.0 > bounds.minY
                    && voxel.getPositionZ() < bounds.maxZ && voxel.getPositionZ() + 1.0 > bounds.minZ) {
                    visitor.accept(voxel);
                }
            }
        }

        private void forEachDomain(final AABB bounds, final Consumer<AABB> visitor) {
            if (visitIndexedCells(bounds, domainBoxes.size(), (x, y, z) -> {
                if (topologyByPosition.containsKey(FloodedFluidClientCache.pack(x, y, z))) {
                    visitor.accept(new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0));
                }
            })) return;
            for (final AABB box : domainBoxes) if (box.intersects(bounds)) visitor.accept(box);
        }

        private interface CellVisitor {
            void accept(int x, int y, int z);
        }

        /** Never visit more empty grid cells than the sparse alternative, or overflow an integer loop. */
        private static boolean visitIndexedCells(final AABB bounds, final int budget, final CellVisitor visitor) {
            final double minX = Math.floor(bounds.minX), minY = Math.floor(bounds.minY), minZ = Math.floor(bounds.minZ);
            final double maxX = Math.floor(Math.nextDown(bounds.maxX)), maxY = Math.floor(Math.nextDown(bounds.maxY)),
                maxZ = Math.floor(Math.nextDown(bounds.maxZ));
            final double count = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (!(count >= 0 && count <= budget) || minX < Integer.MIN_VALUE || minY < Integer.MIN_VALUE
                || minZ < Integer.MIN_VALUE || maxX > Integer.MAX_VALUE || maxY > Integer.MAX_VALUE
                || maxZ > Integer.MAX_VALUE) return false;
            for (long x = (long) minX; x <= (long) maxX; x++) {
                for (long y = (long) minY; y <= (long) maxY; y++) {
                    for (long z = (long) minZ; z <= (long) maxZ; z++) visitor.accept((int) x, (int) y, (int) z);
                }
            }
            return true;
        }

        LocalPointSample sampleLocal(final double x, final double y, final double z) {
            final int voxelX = floorToInt(x);
            final int voxelY = floorToInt(y);
            final int voxelZ = floorToInt(z);
            final long packed = FloodedFluidClientCache.pack(voxelX, voxelY, voxelZ);
            if (!topologyByPosition.containsKey(packed)) {
                return new LocalPointSample(false, null);
            }

            final VsiFluidFloodedVoxel flooded = floodingByPosition.get(packed);
            if (flooded != null && FloodedFluidClientCache.isLocallySubmerged(
                flooded.getFillAmount(), y - voxelY
            )) {
                return new LocalPointSample(true, flooded.getFluidId());
            }
            return new LocalPointSample(true, null);
        }

        @Nullable VsiFluidFloodedVoxel getFloodedVoxel(
            final int x,
            final int y,
            final int z
        ) {
            return floodingByPosition.get(FloodedFluidClientCache.pack(x, y, z));
        }
    }
}
