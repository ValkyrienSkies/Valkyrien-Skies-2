package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologySnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidGameplaySnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;

/**
 * Render-thread spatial index over synchronized topology/occlusion snapshots.
 */
public final class FluidTopologyClientCache {
    private static final Long2ObjectOpenHashMap<CachedSnapshot> SNAPSHOTS =
        new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet LOADED_SHIP_IDS = new LongOpenHashSet();
    private static WeakReference<VsiClientShipWorld> lastWorld = new WeakReference<>(null);

    private FluidTopologyClientCache() {
    }

    public static @Nullable CachedSnapshot get(
        final VsiClientShipWorld shipWorld,
        final long bodyId
    ) {
        return get(shipWorld, bodyId, shipWorld.getFluidGameplaySnapshot(bodyId));
    }

    static @Nullable CachedSnapshot get(
        final VsiClientShipWorld shipWorld, final long bodyId,
        final @Nullable VsiFluidGameplaySnapshot pair
    ) {
        useWorld(shipWorld);
        final VsiFluidTopologySnapshot snapshot =
            pair == null ? null : pair.getTopology();
        if (snapshot == null) {
            SNAPSHOTS.remove(bodyId);
            return null;
        }

        CachedSnapshot cached = SNAPSHOTS.get(bodyId);
        if (cached == null || cached.streamSequence != snapshot.getStreamSequence()) {
            cached = new CachedSnapshot(snapshot, cached);
            SNAPSHOTS.put(bodyId, cached);
        }
        return cached;
    }

    public static void prune(final VsiClientShipWorld shipWorld) {
        useWorld(shipWorld);
        LOADED_SHIP_IDS.clear();
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            LOADED_SHIP_IDS.add(ship.getId());
        }
        SNAPSHOTS.keySet().removeIf(bodyId -> !LOADED_SHIP_IDS.contains(bodyId));
    }

    public static void clear() {
        SNAPSHOTS.clear();
        LOADED_SHIP_IDS.clear();
        lastWorld.clear();
    }

    private static void useWorld(final VsiClientShipWorld shipWorld) {
        if (lastWorld.get() != shipWorld) {
            clear();
            lastWorld = new WeakReference<>(shipWorld);
        }
    }

    public static final class CachedSnapshot {
        private final long streamSequence;
        private final VsiFluidTopologySnapshot snapshot;
        private final Long2ObjectOpenHashMap<VsiFluidTopologyVoxel> voxelsByPosition;
        private final Object occlusionIdentity;

        CachedSnapshot(final VsiFluidTopologySnapshot snapshot) {
            this(snapshot, null);
        }

        private CachedSnapshot(final VsiFluidTopologySnapshot snapshot, final @Nullable CachedSnapshot previous) {
            this.streamSequence = snapshot.getStreamSequence();
            this.snapshot = snapshot;
            final boolean active = snapshot.getEnabled() && snapshot.getValid();
            if (previous != null && active == (previous.snapshot.getEnabled() && previous.snapshot.getValid()) &&
                (!active || snapshot.getVoxels().equals(previous.snapshot.getVoxels()))) {
                voxelsByPosition = previous.voxelsByPosition;
                occlusionIdentity = previous.occlusionIdentity;
                return;
            }
            final Long2ObjectOpenHashMap<VsiFluidTopologyVoxel> cells =
                new Long2ObjectOpenHashMap<>(active ? snapshot.getVoxels().size() : 0);
            boolean sameDomain = previous != null;
            boolean sameValues = sameDomain;
            if (active) {
                for (final VsiFluidTopologyVoxel voxel : snapshot.getVoxels()) {
                    final long key = FloodedFluidClientCache.pack(
                        voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ());
                    if (sameDomain) {
                        final VsiFluidTopologyVoxel old = previous.voxelsByPosition.get(key);
                        sameDomain = old != null && old.getRegions().equals(voxel.getRegions());
                        sameValues &= voxel.equals(old);
                    }
                    cells.put(key, voxel);
                }
            }
            sameDomain &= previous != null && cells.size() == previous.voxelsByPosition.size();
            voxelsByPosition = sameDomain && sameValues ? previous.voxelsByPosition : cells;
            // Flags and publication metadata do not change the controlled domain.
            occlusionIdentity = sameDomain ? previous.occlusionIdentity : new Object();
        }

        Object occlusionIdentity() { return occlusionIdentity; }

        public VsiFluidTopologySnapshot getSnapshot() {
            return snapshot;
        }

        public @Nullable VsiFluidTopologyVoxel get(
            final int x,
            final int y,
            final int z
        ) {
            return voxelsByPosition.get(FloodedFluidClientCache.pack(x, y, z));
        }

        /** Fill below the remaining dry space; -1 means no controlled dry space. */
        public int getDryCellFillAmount(
            final int x,
            final int y,
            final int z,
            final @Nullable FloodedFluidClientCache.CachedSnapshot flooding
        ) {
            final var domain = voxelsByPosition.get(FloodedFluidClientCache.pack(x, y, z));
            if (domain == null || !domain.getRegions().isEmpty()) {
                return -1;
            }
            final var voxel = flooding == null ? null : flooding.get(x, y, z);
            final int fill = voxel == null ? 0 : voxel.getFillAmount();
            return fill >= 255 ? -1 : Math.max(0, fill);
        }
    }
}
