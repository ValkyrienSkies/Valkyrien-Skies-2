package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.ref.WeakReference;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodingSnapshot;
import org.valkyrienskies.core.internal.physics.VsiFluidGameplaySnapshot;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;

/**
 * Render-thread index over the latest synchronized flooding snapshots.
 */
public final class FloodedFluidClientCache {
    private static final Long2ObjectOpenHashMap<CachedSnapshot> SNAPSHOTS = new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet LOADED_SHIP_IDS = new LongOpenHashSet();
    private static WeakReference<VsiClientShipWorld> lastWorld = new WeakReference<>(null);

    private FloodedFluidClientCache() {
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
        final VsiFluidFloodingSnapshot snapshot = pair == null ? null : pair.getFlooding();
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

    public static @Nullable FloodedFluidSample findAtWorldPosition(
        final ClientLevel level,
        final Vec3 worldPosition
    ) {
        final VsiClientShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        final Vector3d local = new Vector3d();
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            if (!ship.getRenderAABB().containsPoint(worldPosition.x, worldPosition.y, worldPosition.z)) {
                continue;
            }

            ship.getRenderTransform().getWorldToShip().transformPosition(
                worldPosition.x, worldPosition.y, worldPosition.z, local
            );
            final int voxelX = floorToInt(local.x);
            final int voxelY = floorToInt(local.y);
            final int voxelZ = floorToInt(local.z);
            final CachedSnapshot cached = get(shipWorld, ship.getId());
            if (cached == null) continue;

            final VsiFluidFloodedVoxel voxel = cached.voxelsByPosition.get(pack(voxelX, voxelY, voxelZ));
            final int fluidId = voxel == null ? 0 : FluidVoxelGeometry.fluidAt(voxel, local.x - voxelX, local.y - voxelY, local.z - voxelZ);
            if (fluidId == 0) {
                continue;
            }

            final FlowingFluid fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(fluidId, true);
            if (fluid != null) {
                return new FloodedFluidSample(ship.getId(), voxel, fluid);
            }
        }
        return null;
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

    static boolean isLocallySubmerged(final int fillAmount, final double localHeight) {
        if (fillAmount <= 0) return false;
        if (fillAmount >= 255) return true;
        return localHeight <= fillAmount / 255.0;
    }

    static long pack(final int x, final int y, final int z) {
        return ((long) x & 0x3FFFFFFL) << 38 |
            ((long) z & 0x3FFFFFFL) << 12 |
            ((long) y & 0xFFFL);
    }

    private static int floorToInt(final double value) {
        final int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    public static final class CachedSnapshot {
        private final long streamSequence;
        private final VsiFluidFloodingSnapshot snapshot;
        private final Long2ObjectOpenHashMap<VsiFluidFloodedVoxel> voxelsByPosition;
        private final Object occlusionIdentity;

        CachedSnapshot(final VsiFluidFloodingSnapshot snapshot) {
            this(snapshot, null);
        }

        private CachedSnapshot(final VsiFluidFloodingSnapshot snapshot, final @Nullable CachedSnapshot previous) {
            this.streamSequence = snapshot.getStreamSequence();
            this.snapshot = snapshot;
            if (previous != null && snapshot.getVoxels().equals(previous.snapshot.getVoxels())) {
                voxelsByPosition = previous.voxelsByPosition;
                occlusionIdentity = previous.occlusionIdentity;
                return;
            }
            final Long2ObjectOpenHashMap<VsiFluidFloodedVoxel> cells =
                new Long2ObjectOpenHashMap<>(snapshot.getVoxels().size());
            boolean sameFill = previous != null;
            boolean sameValues = sameFill;
            for (final VsiFluidFloodedVoxel voxel : snapshot.getVoxels()) {
                if (voxel.getFillAmount() > 0) {
                    final long key = pack(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ());
                    if (sameFill) {
                        final VsiFluidFloodedVoxel old = previous.voxelsByPosition.get(key);
                        sameFill = old != null && Math.min(255, old.getFillAmount()) == Math.min(255, voxel.getFillAmount())
                            && old.getRegions().equals(voxel.getRegions());
                        sameValues &= voxel.equals(old);
                    }
                    cells.put(key, voxel);
                }
            }
            sameFill &= previous != null && cells.size() == previous.voxelsByPosition.size();
            voxelsByPosition = sameFill && sameValues ? previous.voxelsByPosition : cells;
            // Pressure and fluid identity remain visible to queries without rebuilding dry space.
            occlusionIdentity = sameFill ? previous.occlusionIdentity : new Object();
        }

        Object occlusionIdentity() { return occlusionIdentity; }

        public VsiFluidFloodingSnapshot getSnapshot() {
            return snapshot;
        }

        public boolean contains(final int x, final int y, final int z) {
            return voxelsByPosition.containsKey(pack(x, y, z));
        }

        public @Nullable VsiFluidFloodedVoxel get(final int x, final int y, final int z) {
            return voxelsByPosition.get(pack(x, y, z));
        }
    }

    public record FloodedFluidSample(
        long bodyId,
        VsiFluidFloodedVoxel voxel,
        FlowingFluid fluid
    ) {
    }
}
