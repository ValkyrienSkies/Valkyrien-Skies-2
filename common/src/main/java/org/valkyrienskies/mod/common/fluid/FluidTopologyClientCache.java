package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologySnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;

/**
 * Render-thread spatial index over synchronized topology/occlusion snapshots.
 */
public final class FluidTopologyClientCache {
    private static final Long2ObjectOpenHashMap<CachedSnapshot> SNAPSHOTS =
        new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet LOADED_SHIP_IDS = new LongOpenHashSet();

    private FluidTopologyClientCache() {
    }

    public static @Nullable CachedSnapshot get(
        final VsiClientShipWorld shipWorld,
        final long bodyId
    ) {
        final VsiFluidTopologySnapshot snapshot =
            shipWorld.getFluidTopologySnapshot(bodyId);
        if (snapshot == null) {
            SNAPSHOTS.remove(bodyId);
            return null;
        }

        CachedSnapshot cached = SNAPSHOTS.get(bodyId);
        if (cached == null || cached.streamSequence != snapshot.getStreamSequence()) {
            cached = new CachedSnapshot(snapshot);
            SNAPSHOTS.put(bodyId, cached);
        }
        return cached;
    }

    public static void prune(final VsiClientShipWorld shipWorld) {
        LOADED_SHIP_IDS.clear();
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            LOADED_SHIP_IDS.add(ship.getId());
        }
        SNAPSHOTS.keySet().removeIf(bodyId -> !LOADED_SHIP_IDS.contains(bodyId));
    }

    public static final class CachedSnapshot {
        private final long streamSequence;
        private final VsiFluidTopologySnapshot snapshot;
        private final Long2ObjectOpenHashMap<VsiFluidTopologyVoxel> voxelsByPosition;

        private CachedSnapshot(final VsiFluidTopologySnapshot snapshot) {
            this.streamSequence = snapshot.getStreamSequence();
            this.snapshot = snapshot;
            this.voxelsByPosition =
                new Long2ObjectOpenHashMap<>(snapshot.getVoxels().size());
            if (snapshot.getEnabled() && snapshot.getValid()) {
                for (final VsiFluidTopologyVoxel voxel : snapshot.getVoxels()) {
                    voxelsByPosition.put(
                        FloodedFluidClientCache.pack(
                            voxel.getPositionX(),
                            voxel.getPositionY(),
                            voxel.getPositionZ()
                        ),
                        voxel
                    );
                }
            }
        }

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

        public boolean isDryDomainCell(
            final int x,
            final int y,
            final int z,
            final @Nullable FloodedFluidClientCache.CachedSnapshot flooding
        ) {
            if (!voxelsByPosition.containsKey(
                FloodedFluidClientCache.pack(x, y, z)
            )) {
                return false;
            }
            return flooding == null || !flooding.contains(x, y, z);
        }
    }
}
