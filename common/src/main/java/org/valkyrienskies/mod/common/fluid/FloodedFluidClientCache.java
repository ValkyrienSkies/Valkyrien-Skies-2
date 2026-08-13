package org.valkyrienskies.mod.common.fluid;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodingSnapshot;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;

/**
 * Render-thread index over the latest synchronized flooding snapshots.
 */
public final class FloodedFluidClientCache {
    private static final Long2ObjectOpenHashMap<CachedSnapshot> SNAPSHOTS = new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet LOADED_SHIP_IDS = new LongOpenHashSet();

    private FloodedFluidClientCache() {
    }

    public static @Nullable CachedSnapshot get(
        final VsiClientShipWorld shipWorld,
        final long bodyId
    ) {
        final VsiFluidFloodingSnapshot snapshot = shipWorld.getFluidFloodingSnapshot(bodyId);
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
            if (voxel == null || !isLocallySubmerged(voxel.getFillAmount(), local.y - voxelY)) {
                continue;
            }

            final FlowingFluid fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(voxel.getFluidId());
            if (fluid != null) {
                return new FloodedFluidSample(ship.getId(), voxel, fluid);
            }
        }
        return null;
    }

    public static void prune(final VsiClientShipWorld shipWorld) {
        LOADED_SHIP_IDS.clear();
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            LOADED_SHIP_IDS.add(ship.getId());
        }
        SNAPSHOTS.keySet().removeIf(bodyId -> !LOADED_SHIP_IDS.contains(bodyId));
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

        private CachedSnapshot(final VsiFluidFloodingSnapshot snapshot) {
            this.streamSequence = snapshot.getStreamSequence();
            this.snapshot = snapshot;
            this.voxelsByPosition = new Long2ObjectOpenHashMap<>(snapshot.getVoxels().size());
            for (final VsiFluidFloodedVoxel voxel : snapshot.getVoxels()) {
                if (voxel.getFillAmount() > 0) {
                    voxelsByPosition.put(
                        pack(voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()),
                        voxel
                    );
                }
            }
        }

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
