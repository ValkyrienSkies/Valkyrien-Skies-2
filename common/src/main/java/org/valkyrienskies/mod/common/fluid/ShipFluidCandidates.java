package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidGameplaySnapshot;
import org.valkyrienskies.core.internal.world.VsiShipWorld;

final class ShipFluidCandidates {
    private static final Map<VsiShipWorld, Index> EXTRA_DOMAINS = new WeakHashMap<>();
    private record Bounds(long sequence, @Nullable AABBd local) { }
    private static final class Index {
        final Long2ObjectOpenHashMap<Bounds> bounds = new Long2ObjectOpenHashMap<>();
        long[] extras;
    }

    private ShipFluidCandidates() { }

    static synchronized void invalidate(final VsiShipWorld world) {
        final Index index = EXTRA_DOMAINS.get(world);
        if (index != null) index.extras = null;
    }

    static synchronized void clear(final VsiShipWorld world) {
        EXTRA_DOMAINS.remove(world);
    }

    private static synchronized @Nullable AABBdc domainBounds(final VsiShipWorld world, final long id,
        final @Nullable VsiFluidGameplaySnapshot pair) {
        final Index index = EXTRA_DOMAINS.computeIfAbsent(world, ignored -> new Index());
        if (pair == null || !pair.getTopology().getEnabled() || !pair.getTopology().getValid()) {
            index.bounds.remove(id);
            return null;
        }
        final var topology = pair.getTopology();
        Bounds bounds = index.bounds.get(id);
        if (bounds == null || bounds.sequence() != topology.getStreamSequence()) {
            final AABBd local = new AABBd();
            boolean present = false;
            for (final var voxel : topology.getVoxels()) {
                if (!voxel.getSealedInterior() && !voxel.getOpenContainer()) continue;
                local.union(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ());
                local.union(voxel.getPositionX() + 1.0, voxel.getPositionY() + 1.0, voxel.getPositionZ() + 1.0);
                present = true;
            }
            bounds = new Bounds(topology.getStreamSequence(), present ? local : null);
            index.bounds.put(id, bounds);
        }
        return bounds.local();
    }

    static boolean containsRenderedDomain(final VsiShipWorld world, final ClientShip ship,
        final Vec3 position, final @Nullable VsiFluidGameplaySnapshot pair) {
        final AABBdc bounds = domainBounds(world, ship.getId(), pair);
        if (bounds == null) return false;
        final Vector3d local = ship.getRenderTransform().getWorldToShip().transformPosition(
            position.x, position.y, position.z, new Vector3d());
        return bounds.containsPoint(local);
    }

    private static synchronized Index extraDomains(final VsiShipWorld world) {
        final Index index = EXTRA_DOMAINS.computeIfAbsent(world, ignored -> new Index());
        if (index.extras != null) return index;
        final LongArrayList ids = new LongArrayList();
        final LongOpenHashSet loaded = new LongOpenHashSet();
        for (final Ship ship : world.getLoadedShips()) {
            loaded.add(ship.getId());
            final var pair = ShipFluidInteraction.snapshotFor(world, ship.getId());
            final AABBdc local = domainBounds(world, ship.getId(), pair);
            if (local == null) continue;
            final AABBd fluid = new AABBd(local).transform(ship.getShipToWorld());
            final AABBdc hull = ship.getWorldAABB();
            if (fluid.minX < hull.minX() || fluid.minY < hull.minY() || fluid.minZ < hull.minZ()
                || fluid.maxX > hull.maxX() || fluid.maxY > hull.maxY() || fluid.maxZ > hull.maxZ()) ids.add(ship.getId());
        }
        index.bounds.keySet().removeIf(id -> !loaded.contains(id));
        index.extras = ids.toLongArray();
        return index;
    }

    static synchronized List<Ship> includeDomains(final VsiShipWorld world, final String dimension,
        final Iterable<? extends Ship> ordinary, final @Nullable AABBdc query) {
        final List<Ship> result = new ArrayList<>();
        final LongOpenHashSet included = new LongOpenHashSet();
        final AABBd queryBounds = query == null ? null : new AABBd(query);
        for (final Ship ship : ordinary) if (included.add(ship.getId())) result.add(ship);
        final Index index = extraDomains(world);
        for (final long id : index.extras) {
            if (included.contains(id)) continue;
            final Ship ship = world.getAllShips().getById(id);
            if (ship == null || !dimension.equals(ship.getChunkClaimDimension())) continue;
            final Bounds bounds = index.bounds.get(id);
            if (bounds == null || bounds.local() == null) continue;
            if (queryBounds == null || new AABBd(bounds.local()).transform(ship.getShipToWorld()).intersectsAABB(queryBounds)) {
                included.add(id);
                result.add(ship);
            }
        }
        return result;
    }

    static List<Ship> intersecting(final VsiShipWorld world, final String dimension, final AABBdc query) {
        return includeDomains(world, dimension, world.getAllShips().getIntersecting(query, dimension), query);
    }
}
