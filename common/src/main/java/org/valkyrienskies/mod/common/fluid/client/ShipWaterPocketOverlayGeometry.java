package org.valkyrienskies.mod.common.fluid.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.BitSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Per-ship boundary geometry shared by {@link ShipWaterPocketLiquidOverlay} and
 * {@link ShipPocketWorldWaterOccluder}.
 *
 * <p>Both consumers need to know exactly which cells sit on the boundary between the
 * water-reachable "open" volume and either solid geometry or the sealed interior air pocket.
 * That boundary is computed once per ship per geometry revision here, so both renderers walk the
 * identical face set instead of each re-deriving (or approximating) it independently, and the
 * one block-state scan per ship is shared instead of duplicated.</p>
 */
public final class ShipWaterPocketOverlayGeometry {

    private ShipWaterPocketOverlayGeometry() {}

    public static final class Cache {
        final long shipId;
        long geometryRevision = Long.MIN_VALUE;
        int minX;
        int minY;
        int minZ;
        int sizeX;
        int sizeY;
        int sizeZ;
        @Nullable BitSet overlaySolids;
        @Nullable BitSet fullCellOverlaySolids;
        @Nullable BitSet overlayBoundary;

        private Cache(final long shipId) {
            this.shipId = shipId;
        }
    }

    private static final Long2ObjectOpenHashMap<Cache> CACHE = new Long2ObjectOpenHashMap<>();

    public static void clear() {
        CACHE.clear();
    }

    /** Returns the up-to-date boundary cache for this ship, rebuilding it only if the snapshot's geometry changed. */
    public static Cache get(final ClientLevel level, final long shipId,
        final ShipFluidRenderSnapshot snapshot) {
        Cache cache = CACHE.get(shipId);
        if (cache == null) {
            cache = new Cache(shipId);
            CACHE.put(shipId, cache);
        }
        ensure(level, cache, snapshot);
        return cache;
    }

    private static void ensure(final ClientLevel level, final Cache cache,
        final ShipFluidRenderSnapshot snapshot) {
        final int minX = snapshot.getMinX();
        final int minY = snapshot.getMinY();
        final int minZ = snapshot.getMinZ();
        final int sizeX = snapshot.getSizeX();
        final int sizeY = snapshot.getSizeY();
        final int sizeZ = snapshot.getSizeZ();
        final int volume = sizeX * sizeY * sizeZ;

        final boolean boundsChanged =
            cache.minX != minX || cache.minY != minY || cache.minZ != minZ ||
                cache.sizeX != sizeX || cache.sizeY != sizeY || cache.sizeZ != sizeZ;

        if (!boundsChanged && cache.geometryRevision == snapshot.getGeometryRevision() && cache.overlaySolids != null) {
            return;
        }

        cache.geometryRevision = snapshot.getGeometryRevision();
        cache.minX = minX;
        cache.minY = minY;
        cache.minZ = minZ;
        cache.sizeX = sizeX;
        cache.sizeY = sizeY;
        cache.sizeZ = sizeZ;

        final BitSet overlaySolids = new BitSet(volume);
        final BitSet fullCellOverlaySolids = new BitSet(volume);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int idx = 0;
        for (int lz = 0; lz < sizeZ; lz++) {
            for (int ly = 0; ly < sizeY; ly++) {
                for (int lx = 0; lx < sizeX; lx++) {
                    pos.set(minX + lx, minY + ly, minZ + lz);
                    final BlockState state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) {
                        idx++;
                        continue;
                    }

                    if (isOverlaySolidCandidate(level, pos, state)) {
                        overlaySolids.set(idx);
                        if (ShipWaterPocketLiquidOverlay.shouldUseFullCellSolidOverlay(state)) {
                            fullCellOverlaySolids.set(idx);
                        }
                    }

                    idx++;
                }
            }
        }

        cache.overlaySolids = overlaySolids;
        cache.fullCellOverlaySolids = fullCellOverlaySolids;
        cache.overlayBoundary = ShipWaterPocketLiquidOverlay.buildOverlayBoundaryMask(
            snapshot.getOpen(), snapshot.getInterior(), overlaySolids, fullCellOverlaySolids, sizeX, sizeY, sizeZ);
    }

    private static boolean isOverlaySolidCandidate(final ClientLevel level, final BlockPos pos, final BlockState state) {
        return ShipWaterPocketLiquidOverlay.isOverlaySolidCandidate(
            ShipWaterPocketLiquidOverlay.isOverlaySolidRenderType(ItemBlockRenderTypes.getChunkRenderType(state)),
            state.isSolidRender(level, pos),
            state.propagatesSkylightDown(level, pos),
            state.getLightBlock(level, pos)
        );
    }
}
