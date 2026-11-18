package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
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
import org.joml.Matrix4dc;
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
        for (final Ship ship : ShipFluidCandidates.intersecting(shipWorld, VSGameUtilsKt.getDimensionId(level), queryBounds)) {
            final CachedBody body = bodyFor(shipWorld, ship.getId());
            if (body == null) continue;

            ship.getWorldToShip().transformPosition(
                worldPosition.x, worldPosition.y, worldPosition.z, local
            );
            final LocalPointSample localSample = body.sampleLocal(local.x, local.y, local.z, shipWorld instanceof VsiClientShipWorld);
            if (!localSample.insideDomain()) {
                if (!body.topologyByPosition.isEmpty() && insideHull(level, body, local)) {
                    drySample = new PointSample(true, null);
                }
                continue;
            }

            final FlowingFluid fluid = localSample.fluidId() == null
                ? null
                : MassDatapackResolver.INSTANCE.getFlowingFluid(localSample.fluidId(), shipWorld instanceof VsiClientShipWorld);
            if (fluid != null) {
                return new PointSample(true, fluid);
            }
            drySample = new PointSample(true, null);
        }
        return drySample;
    }

    private static boolean insideHull(final Level level, final CachedBody body, final Vector3d point) {
        final AABB query = new AABB(point.x - 1.0e-6, point.y - 1.0e-6, point.z - 1.0e-6,
            point.x + 1.0e-6, point.y + 1.0e-6, point.z + 1.0e-6);
        if (!boundedHullQuery(query)) return false;
        for (final var shape : level.getBlockCollisions(null, query)) {
            for (final AABB box : shape.toAabbs()) {
                if (box.contains(point.x, point.y, point.z)) return true;
            }
        }
        final var voxel = body.topologyByPosition.get(FloodedFluidClientCache.pack(
            floorToInt(point.x), floorToInt(point.y), floorToInt(point.z)));
        if (voxel != null) {
            for (final AABB box : nonCollidingBoundaryMasks(level, voxel)) {
                if (box.contains(point.x, point.y, point.z)) return true;
            }
        }
        return false;
    }

    /**
     * Topology excludes outline geometry even when a block (such as a torch) has no collision.
     * That excluded solid space must not be mistaken for exterior world fluid. Only supplement
     * partial, published domain cells; an exposed torch must not create its own dry domain.
     */
    private static List<AABB> nonCollidingBoundaryMasks(final Level level, final VsiFluidTopologyVoxel voxel) {
        if (voxel.getRegions().isEmpty()) return List.of();
        final BlockPos pos = new BlockPos(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ());
        final var state = level.getBlockState(pos);
        final List<AABB> boxes;
        try {
            if (!state.getCollisionShape(level, pos).isEmpty()) return List.of();
            boxes = state.getShape(level, pos).toAabbs();
        } catch (final RuntimeException exception) {
            // Registration also tolerates shape-query failures. Do not turn a fluid query into a crash.
            return List.of();
        }
        final List<AABB> masks = new ArrayList<>();
        for (final AABB box : boxes) {
            // Match the outward 1/16-block rounding used by FluidBoundaryRegistration.
            // Invalid/out-of-cell outlines cannot safely describe this published partial cell.
            if (!(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0
                && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1)) return List.of();
            if (box.maxX <= box.minX || box.maxY <= box.minY || box.maxZ <= box.minZ) continue;
            masks.add(new AABB(Math.floor(box.minX * 16) / 16 + pos.getX(),
                Math.floor(box.minY * 16) / 16 + pos.getY(), Math.floor(box.minZ * 16) / 16 + pos.getZ(),
                Math.ceil(box.maxX * 16) / 16 + pos.getX(), Math.ceil(box.maxY * 16) / 16 + pos.getY(),
                Math.ceil(box.maxZ * 16) / 16 + pos.getZ()));
        }
        return masks;
    }

    private static boolean boundedHullQuery(final AABB bounds) {
        final double cells = (Math.floor(bounds.maxX + 1.0e-7) - Math.floor(bounds.minX - 1.0e-7) + 3)
            * (Math.floor(bounds.maxY + 1.0e-7) - Math.floor(bounds.minY - 1.0e-7) + 3)
            * (Math.floor(bounds.maxZ + 1.0e-7) - Math.floor(bounds.minZ - 1.0e-7) + 3);
        return cells > 0 && cells <= 4096 && bounds.minX > Integer.MIN_VALUE + 2.0
            && bounds.minY > Integer.MIN_VALUE + 2.0 && bounds.minZ > Integer.MIN_VALUE + 2.0
            && bounds.maxX < Integer.MAX_VALUE - 2.0 && bounds.maxY < Integer.MAX_VALUE - 2.0
            && bounds.maxZ < Integer.MAX_VALUE - 2.0;
    }

    /** Prepare one immutable query for the platform's original fluid loop. */
    public static VolumeQuery prepareVolume(final Level level, final AABB worldBounds) {
        final VsiShipWorld world = shipWorld(level);
        if (world == null) return new VolumeQuery(worldBounds);
        return prepareVolume(level, world, ShipFluidCandidates.intersecting(world, VSGameUtilsKt.getDimensionId(level), new AABBd(
            worldBounds.minX, worldBounds.minY, worldBounds.minZ,
            worldBounds.maxX, worldBounds.maxY, worldBounds.maxZ
        )), worldBounds);
    }

    static VolumeQuery prepareVolume(final VsiShipWorld world, final Iterable<? extends Ship> ships,
        final AABB worldBounds) {
        return prepareVolume(null, world, ships, worldBounds);
    }

    static VolumeQuery prepareVolume(final @Nullable Level level, final VsiShipWorld world,
        final Iterable<? extends Ship> ships, final AABB worldBounds) {
        final VolumeQuery result = new VolumeQuery(level, worldBounds);
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
            if (level != null && !body.topologyByPosition.isEmpty()) {
                result.hulls.add(new Hull(localQuery, toWorld, toLocal, body));
            }
            // Use local hash probes for small queries, sparse content for huge inverse-transformed volumes.
            body.forEachDomain(localQuery, domain -> {
                final FluidVolumeGeometry.Box box = new FluidVolumeGeometry.Box(domain, toWorld, toLocal, result.origin);
                if (box.bounds.intersects(result.relativeBounds)) result.domains.add(box);
            });
            body.forEachFlooded(localQuery, voxel -> {
                if (!voxel.getSurfaceRegions().isEmpty()) {
                    for (final var surface : voxel.getSurfaceRegions()) {
                        final FlowingFluid fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(
                            FluidVoxelGeometry.dominantFluid(surface, world instanceof VsiClientShipWorld), world instanceof VsiClientShipWorld);
                        if (fluid == null) continue;
                        final var current = surface.getCurrent();
                        if (current == null && result.retained.getDouble(fluid) >= result.relativeBounds.maxY) continue;
                        final var box = new FluidVolumeGeometry.Box(voxel, surface, toWorld, toLocal, result.origin);
                        final var contact = current == null ? null : box.contact(result.relativeBounds);
                        final double top = current == null ? box.intersectionTop(result.relativeBounds) :
                            contact == null ? Double.NaN : contact.top();
                        if (Double.isFinite(top) && top > 0) result.retained.put(fluid, Math.max(result.retained.getDouble(fluid), top));
                        if (contact != null) {
                            final Vec3 center = contact.center().add(result.origin);
                            final Vector3d offset = toLocal.transformPosition(center.x, center.y, center.z, new Vector3d())
                                .sub(voxel.getPositionX() + .5, voxel.getPositionY() + .5, voxel.getPositionZ() + .5);
                            final Vector3d velocity = new Vector3d(current.getAngularVelocityX(), current.getAngularVelocityY(),
                                current.getAngularVelocityZ()).cross(offset).add(current.getVelocityX(), current.getVelocityY(), current.getVelocityZ());
                            toWorld.transformDirection(velocity);
                            if (velocity.isFinite()) result.currents.computeIfAbsent(fluid, ignored -> new CurrentContact())
                                .add(contact.volume(), new Vec3(velocity.x, velocity.y, velocity.z));
                        }
                    }
                    return;
                }
                FluidVoxelGeometry.forEachFlooded(voxel, (fluidId, local) -> {
                final FlowingFluid fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(fluidId, world instanceof VsiClientShipWorld);
                if (fluid == null || result.retained.getDouble(fluid) >= result.relativeBounds.maxY) return;
                final double top = new FluidVolumeGeometry.Box(local, toWorld, toLocal, result.origin)
                    .intersectionTop(result.relativeBounds);
                if (Double.isFinite(top) && top > 0) {
                    result.retained.put(fluid, Math.max(result.retained.getDouble(fluid), top));
                }
                });
            });
        }
        return result;
    }

    private record Hull(AABB bounds, Matrix4dc toWorld, Matrix4dc toLocal, CachedBody body) { }

    private static final class CurrentContact {
        double volume;
        Vec3 moment = Vec3.ZERO;
        void add(final double amount, final Vec3 velocity) {
            volume += amount;
            moment = moment.add(velocity.scale(amount));
        }
    }

    public static final class VolumeQuery {
        private final @Nullable Level level;
        private final Vec3 origin;
        private final AABB relativeBounds;
        private final boolean valid;
        private final List<FluidVolumeGeometry.Box> domains = new ArrayList<>();
        private final List<Hull> hulls = new ArrayList<>();
        private boolean hullsRead;
        private final Long2ObjectOpenHashMap<CachedBody> bodies = new Long2ObjectOpenHashMap<>();
        private final Object2DoubleOpenHashMap<FlowingFluid> retained =
            new Object2DoubleOpenHashMap<>();
        private final Map<FlowingFluid, CurrentContact> currents = new java.util.HashMap<>();

        private VolumeQuery(final AABB bounds) {
            this(null, bounds);
        }

        private VolumeQuery(final @Nullable Level level, final AABB bounds) {
            this.level = level;
            origin = new Vec3(bounds.minX, bounds.minY, bounds.minZ);
            relativeBounds = bounds.move(-origin.x, -origin.y, -origin.z);
            valid = Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ)
                && Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ)
                && relativeBounds.maxX > 0 && relativeBounds.maxY > 0 && relativeBounds.maxZ > 0;
        }

        /** NaN means this world-fluid cell has no positive-volume contact outside controlled ship space. */
        public float worldHeight(final BlockPos block, final float height) {
            if (!valid || !hasDomains()) return height;
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
            double top = intersects ? FluidVolumeGeometry.uncoveredTop(liquid, domains) : maxY;
            if (Double.isFinite(top) && !hullsRead && !hulls.isEmpty()) {
                readHullMasks();
                top = FluidVolumeGeometry.uncoveredTop(liquid, domains);
            }
            // Side-only clipping leaves the original world surface/depth intact, including full submersion.
            if (Double.isFinite(top) && top >= Math.nextDown(maxY)) return height;
            return Double.isFinite(top) ? (float) (top + origin.y - block.getY()) : Float.NaN;
        }

        public boolean hasDomains() {
            return !domains.isEmpty() || !hulls.isEmpty();
        }

        private void readHullMasks() {
            hullsRead = true;
            for (final Hull hull : hulls) {
                final AABB local = hull.bounds;
                // Partial topology is sparse and independently bounded, even on very small ships.
                hull.body.forEachPartialTopologyVoxel(local, voxel -> {
                    for (final AABB mask : nonCollidingBoundaryMasks(level, voxel)) {
                        final var box = new FluidVolumeGeometry.Box(mask, hull.toWorld, hull.toLocal, origin);
                        if (box.bounds.intersects(relativeBounds)) domains.add(box);
                    }
                });
                if (!boundedHullQuery(local)) continue;
                for (final var shape : level.getBlockCollisions(null, local)) {
                    shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                        final var box = new FluidVolumeGeometry.Box(new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                            hull.toWorld, hull.toLocal, origin);
                        if (box.bounds.intersects(relativeBounds)) domains.add(box);
                    });
                }
            }
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

        public Vec3 currentMovement(final Vec3 before, final Vec3 after, final Predicate<FluidState> accepts,
            final ToDoubleFunction<FluidState> motionScale) {
            if (!valid || currents.isEmpty()) return after;
            final double entityVolume = relativeBounds.getXsize() * relativeBounds.getYsize() * relativeBounds.getZsize();
            double volume = 0;
            Vec3 push = Vec3.ZERO;
            for (final var entry : currents.entrySet()) {
                final FluidState fluid = entry.getKey().defaultFluidState();
                if (!accepts.test(fluid)) continue;
                final CurrentContact contact = entry.getValue();
                volume += contact.volume;
                push = push.add(RetainedFluidCurrent.push(before, contact.moment.scale(1 / contact.volume),
                    contact.volume / entityVolume, motionScale.applyAsDouble(fluid)));
            }
            if (volume > entityVolume) push = push.scale(entityVolume / volume);
            return before.add(after.subtract(before).scale(1 - Math.min(1, volume / entityVolume))).add(push);
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
        ShipFluidCandidates.invalidate(shipWorld);
        final Long2ObjectOpenHashMap<CachedBody> worldCache = CACHES.get(shipWorld);
        if (worldCache == null) return;
        worldCache.keySet().removeIf(bodyId -> shipWorld.getAllShips().getById(bodyId) == null);
        if (worldCache.isEmpty()) CACHES.remove(shipWorld);
    }

    public static synchronized void clear(final VsiShipWorld shipWorld) {
        ShipFluidCandidates.clear(shipWorld);
        CACHES.remove(shipWorld);
    }

    static @Nullable VsiFluidGameplaySnapshot snapshotFor(final VsiShipWorld shipWorld, final long bodyId) {
        if (shipWorld instanceof final VsiClientShipWorld clientWorld) {
            return clientWorld.getFluidGameplaySnapshot(bodyId);
        } else if (shipWorld instanceof final VsiServerShipWorld serverWorld) {
            return serverWorld.getFluidGameplaySnapshot(bodyId);
        }
        return null;
    }

    static synchronized @Nullable CachedBody bodyFor(final VsiShipWorld shipWorld, final long bodyId) {
        final VsiFluidGameplaySnapshot pair = snapshotFor(shipWorld, bodyId);
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
        private final List<VsiFluidTopologyVoxel> partialTopologyVoxels;
        private final Long2ObjectOpenHashMap<VsiFluidFloodedVoxel> floodingByPosition;
        private final List<AABB> domainBoxes;
        final @Nullable AABBd domainBounds;

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
                partialTopologyVoxels = previous.partialTopologyVoxels;
                domainBoxes = previous.domainBoxes;
                domainBounds = previous.domainBounds;
            } else {
                topologyByPosition = new Long2ObjectOpenHashMap<>(topology.getVoxels().size());
                for (final VsiFluidTopologyVoxel voxel : topology.getVoxels()) {
                    topologyByPosition.put(FloodedFluidClientCache.pack(
                        voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()), voxel);
                }
                partialTopologyVoxels = topologyByPosition.values().stream()
                    .filter(voxel -> !voxel.getRegions().isEmpty()).toList();
                domainBoxes = coalesceDomains(topologyByPosition.values());
                final AABBd bounds = new AABBd();
                for (final AABB box : domainBoxes) {
                    bounds.union(box.minX, box.minY, box.minZ);
                    bounds.union(box.maxX, box.maxY, box.maxZ);
                }
                domainBounds = domainBoxes.isEmpty() ? null : bounds;
            }
            if (flooding != null) {
                for (final VsiFluidFloodedVoxel voxel : flooding.getVoxels()) {
                    if (voxel.getFillAmount() <= 0 && voxel.getSurfaceRegions().isEmpty()) continue;
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
                FluidVoxelGeometry.forEachDomain(voxel, boxes::add);
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

        private void forEachPartialTopologyVoxel(final AABB bounds, final Consumer<VsiFluidTopologyVoxel> visitor) {
            if (visitIndexedCells(bounds, partialTopologyVoxels.size(), (x, y, z) -> {
                final var voxel = topologyByPosition.get(FloodedFluidClientCache.pack(x, y, z));
                if (voxel != null && !voxel.getRegions().isEmpty()) visitor.accept(voxel);
            })) return;
            for (final var voxel : partialTopologyVoxels) {
                if (voxel.getPositionX() < bounds.maxX && voxel.getPositionX() + 1.0 > bounds.minX
                    && voxel.getPositionY() < bounds.maxY && voxel.getPositionY() + 1.0 > bounds.minY
                    && voxel.getPositionZ() < bounds.maxZ && voxel.getPositionZ() + 1.0 > bounds.minZ) {
                    visitor.accept(voxel);
                }
            }
        }

        private void forEachDomain(final AABB bounds, final Consumer<AABB> visitor) {
            if (visitIndexedCells(bounds, domainBoxes.size(), (x, y, z) -> {
                final var voxel = topologyByPosition.get(FloodedFluidClientCache.pack(x, y, z));
                if (voxel != null) FluidVoxelGeometry.forEachDomain(voxel, visitor);
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
            return sampleLocal(x, y, z, false);
        }

        LocalPointSample sampleLocal(final double x, final double y, final double z, final boolean clientSide) {
            final int voxelX = floorToInt(x);
            final int voxelY = floorToInt(y);
            final int voxelZ = floorToInt(z);
            final long packed = FloodedFluidClientCache.pack(voxelX, voxelY, voxelZ);
            final var domain = topologyByPosition.get(packed);
            if (domain == null || !FluidVoxelGeometry.contains(domain, x - voxelX, y - voxelY, z - voxelZ)) {
                return new LocalPointSample(false, null);
            }

            final VsiFluidFloodedVoxel flooded = floodingByPosition.get(packed);
            final int fluidId = flooded == null ? 0 : FluidVoxelGeometry.fluidAt(flooded, x - voxelX, y - voxelY, z - voxelZ, clientSide);
            if (fluidId != 0) return new LocalPointSample(true, fluidId);
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
