package org.valkyrienskies.mod.common.fluid.client;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.util.FluidStateManager;

/**
 * Resolves where the world's fluid surface actually sits, in world space, around a ship.
 *
 * <p>Ship fluid rendering has to agree with what vanilla will really rasterize at the waterline, so
 * this reimplements vanilla's per-corner height derivation ({@code LiquidBlockRenderer}'s averaging)
 * rather than approximating a fluid block with one uniform height. A single resolved Y is wrong as
 * soon as the surface slopes: it let world water draw in front of ship geometry in some spots and
 * behind it in others, depending on local shoreline and flow topology.</p>
 *
 * <p>The per-block corner heights are position-independent within their block, so they are cached
 * and reused for any fractional query landing in the same block. Cache entries carry the revision of
 * the chunk they came from; {@link #invalidateExteriorFluidChunk} bumps that revision when the
 * client learns a chunk changed, which retires the affected entries lazily on next lookup.</p>
 */
public final class ShipExteriorFluidSampler {

    private ShipExteriorFluidSampler() {
    }

    private static final int MAX_FLUID_SURFACE_CACHE = 8192;
    private static final int MAX_FLUID_SURFACE_POINT_CACHE = 16384;

    public static final double SURFACE_EPS = 1.0E-5;

    private static final double[][] FLUID_SAMPLE_OFFSETS = {
        {0.0, 0.0, 0.0},
        {0.0, -0.35, 0.0},
        {0.35, 0.0, 0.0},
        {-0.35, 0.0, 0.0},
        {0.0, 0.0, 0.35},
        {0.0, 0.0, -0.35},
        {0.35, -0.35, 0.0},
        {-0.35, -0.35, 0.0},
        {0.0, -0.35, 0.35},
        {0.0, -0.35, -0.35},
        {0.35, 0.0, 0.35},
        {0.35, 0.0, -0.35},
        {-0.35, 0.0, 0.35},
        {-0.35, 0.0, -0.35},
    };

    public static final class FluidSurfaceSample {
        public final Fluid fluid;
        public final FluidState fluidState;
        public final BlockPos pos;
        public final double surfaceY;

        private FluidSurfaceSample(final Fluid fluid, final FluidState fluidState, final BlockPos pos, final double surfaceY) {
            this.fluid = fluid;
            this.fluidState = fluidState;
            this.pos = pos;
            this.surfaceY = surfaceY;
        }
    }

    /**
     * The vanilla-equivalent per-corner surface heights of the fluid block at {@code pos} —
     * position-independent within that block, so it's safe to cache and reuse for any fractional
     * (x, z) query landing in the same block, unlike a single resolved Y. See {@link
     * #computeVanillaColumnHeights} for how NW/NE/SW/SE are derived (a faithful port of vanilla's
     * {@code LiquidBlockRenderer.calculateAverageHeight}), and {@link #surfaceYAt} for how a query
     * point's fractional position within the block turns these into an actual world Y — matching
     * what vanilla will actually rasterize instead of approximating it with a single block-uniform
     * height (a real geometric mismatch that let world water render in front of our occluder in
     * some spots and behind it in others, depending on local shoreline/flow topology).
     */
    private static final class ColumnHeights {
        final Fluid fluid;
        final FluidState fluidState;
        final BlockPos pos;
        final float nw;
        final float ne;
        final float sw;
        final float se;

        private ColumnHeights(final Fluid fluid, final FluidState fluidState, final BlockPos pos,
            final float nw, final float ne, final float sw, final float se) {
            this.fluid = fluid;
            this.fluidState = fluidState;
            this.pos = pos;
            this.nw = nw;
            this.ne = ne;
            this.sw = sw;
            this.se = se;
        }

        /** Bilinear blend of the four corner heights at the given world X/Z, matching vanilla's quad. */
        private double surfaceYAt(final double worldX, final double worldZ) {
            final double fx = Mth.clamp(worldX - pos.getX(), 0.0, 1.0);
            final double fz = Mth.clamp(worldZ - pos.getZ(), 0.0, 1.0);
            final double h = nw * (1.0 - fx) * (1.0 - fz) + ne * fx * (1.0 - fz)
                + sw * (1.0 - fx) * fz + se * fx * fz;
            return pos.getY() + h;
        }
    }

    private static final class ChunkTrackedFluidSurface {
        private final long chunkRevision;
        private final ColumnHeights columnHeights;

        private ChunkTrackedFluidSurface(final long chunkRevision, final ColumnHeights columnHeights) {
            this.chunkRevision = chunkRevision;
            this.columnHeights = columnHeights;
        }
    }

    private static final ColumnHeights COLUMN_HEIGHTS_MISS = new ColumnHeights(
        Fluids.EMPTY,
        Fluids.EMPTY.defaultFluidState(),
        BlockPos.ZERO,
        Float.NaN, Float.NaN, Float.NaN, Float.NaN
    );
    private static ClientLevel lastSurfaceCacheLevel = null;
    private static final Long2LongOpenHashMap EXTERIOR_FLUID_CHUNK_REVISIONS = new Long2LongOpenHashMap();
    private static final Long2ObjectOpenHashMap<ChunkTrackedFluidSurface> FLUID_SURFACE_CACHE = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<ChunkTrackedFluidSurface> FLUID_SURFACE_POINT_CACHE =
        new Long2ObjectOpenHashMap<>();

    public static void invalidateExteriorFluidChunk(final @Nullable ClientLevel level, final int chunkX,
        final int chunkZ) {
        if (level == null) return;
        if (lastSurfaceCacheLevel != null && lastSurfaceCacheLevel != level) {
            clear();
        }
        lastSurfaceCacheLevel = level;
        invalidateExteriorFluidChunk(chunkX, chunkZ);
    }

    public static void invalidateExteriorFluidChunkForTests(final int chunkX, final int chunkZ) {
        invalidateExteriorFluidChunk(chunkX, chunkZ);
    }

    public static long getExteriorFluidChunkRevisionForTests(final int chunkX, final int chunkZ) {
        return currentExteriorFluidChunkRevision(exteriorFluidChunkKeyFromChunk(chunkX, chunkZ));
    }

    public static void clear() {
        EXTERIOR_FLUID_CHUNK_REVISIONS.clear();
        FLUID_SURFACE_CACHE.clear();
        FLUID_SURFACE_POINT_CACHE.clear();
    }

    public static Fluid canonicalSource(final Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    public static @Nullable FluidSurfaceSample findExteriorFluidSurface(final ClientLevel level,
        final BlockPos.MutableBlockPos fluidPos, final BlockPos.MutableBlockPos scanPos,
        final FluidStateManager.QueryCache fluidQueryCache, final double worldX, final double worldY, final double worldZ) {
        FluidSurfaceSample best = null;
        for (final double[] offset : FLUID_SAMPLE_OFFSETS) {
            final FluidSurfaceSample candidate = findExteriorFluidSurfaceAtSamplePoint(
                level,
                fluidPos,
                scanPos,
                fluidQueryCache,
                worldX + offset[0],
                worldY + offset[1],
                worldZ + offset[2]
            );
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.surfaceY > best.surfaceY + SURFACE_EPS) {
                best = candidate;
            }
        }
        return best;
    }

    private static @Nullable FluidSurfaceSample findExteriorFluidSurfaceAtSamplePoint(
        final ClientLevel level,
        final BlockPos.MutableBlockPos fluidPos,
        final BlockPos.MutableBlockPos scanPos,
        final FluidStateManager.QueryCache fluidQueryCache,
        final double worldX,
        final double worldY,
        final double worldZ
    ) {
        final int blockX = Mth.floor(worldX);
        final int blockY = Mth.floor(worldY);
        final int blockZ = Mth.floor(worldZ);
        final long chunkRevision = currentExteriorFluidChunkRevision(exteriorFluidChunkKey(blockX, blockZ));
        final long pointKey = BlockPos.asLong(blockX, blockY, blockZ);
        final ChunkTrackedFluidSurface pointCached = FLUID_SURFACE_POINT_CACHE.get(pointKey);
        if (pointCached != null) {
            if (pointCached.chunkRevision == chunkRevision) {
                return toSample(pointCached.columnHeights, worldX, worldZ);
            }
            FLUID_SURFACE_POINT_CACHE.remove(pointKey);
        }

        fluidPos.set(blockX, blockY, blockZ);
        FluidState sampleState = getRawExteriorFluidState(level, fluidPos, fluidQueryCache);
        if (sampleState == null) {
            fluidPos.move(0, -1, 0);
            sampleState = getRawExteriorFluidState(level, fluidPos, fluidQueryCache);
            if (sampleState == null) {
                fluidPos.move(0, 2, 0);
                sampleState = getRawExteriorFluidState(level, fluidPos, fluidQueryCache);
                if (sampleState == null) {
                    cacheFluidSurfacePoint(pointKey, chunkRevision, COLUMN_HEIGHTS_MISS);
                    return null;
                }
            }
        }

        final long key = BlockPos.asLong(fluidPos.getX(), fluidPos.getY(), fluidPos.getZ());
        final ChunkTrackedFluidSurface cached = FLUID_SURFACE_CACHE.get(key);
        if (cached != null) {
            if (cached.chunkRevision == chunkRevision) {
                cacheFluidSurfacePoint(pointKey, chunkRevision, cached.columnHeights);
                return toSample(cached.columnHeights, worldX, worldZ);
            }
            FLUID_SURFACE_CACHE.remove(key);
        }

        final Fluid canonicalFluid = canonicalSource(sampleState.getType());
        final int topY = scanRawExteriorFluidColumnTopY(level, fluidPos, canonicalFluid, fluidQueryCache, scanPos);
        scanPos.set(fluidPos.getX(), topY, fluidPos.getZ());

        final FluidState topFluid = getRawExteriorFluidState(level, scanPos, fluidQueryCache);
        if (topFluid == null) {
            cacheFluidSurfacePoint(pointKey, chunkRevision, COLUMN_HEIGHTS_MISS);
            return null;
        }

        final ColumnHeights columnHeights = computeVanillaColumnHeights(level, fluidQueryCache, scanPos.immutable(),
            canonicalFluid, topFluid);
        if (FLUID_SURFACE_CACHE.size() >= MAX_FLUID_SURFACE_CACHE) {
            FLUID_SURFACE_CACHE.clear();
        }
        FLUID_SURFACE_CACHE.put(key, new ChunkTrackedFluidSurface(chunkRevision, columnHeights));
        cacheFluidSurfacePoint(pointKey, chunkRevision, columnHeights);
        return toSample(columnHeights, worldX, worldZ);
    }

    private static @Nullable FluidSurfaceSample toSample(final ColumnHeights columnHeights, final double worldX, final double worldZ) {
        if (columnHeights == COLUMN_HEIGHTS_MISS) return null;
        return new FluidSurfaceSample(columnHeights.fluid, columnHeights.fluidState, columnHeights.pos,
            columnHeights.surfaceYAt(worldX, worldZ));
    }

    private static void cacheFluidSurfacePoint(final long pointKey, final long chunkRevision, final ColumnHeights columnHeights) {
        if (FLUID_SURFACE_POINT_CACHE.size() >= MAX_FLUID_SURFACE_POINT_CACHE) {
            FLUID_SURFACE_POINT_CACHE.clear();
        }
        FLUID_SURFACE_POINT_CACHE.put(pointKey, new ChunkTrackedFluidSurface(chunkRevision, columnHeights));
    }

    /**
     * Faithful port of vanilla's {@code LiquidBlockRenderer.calculateAverageHeight} /
     * {@code addWeightedHeight} / {@code getHeight}: the four corners of a fluid block's top face
     * are each a weighted average of the block's own height, its two axis-adjacent neighbors, and
     * (if either axis neighbor has any fluid) the diagonal neighbor — snapping to a flat 1.0 if the
     * block itself sits under another fluid block of the same type (an interior/non-surface block),
     * or if either axis neighbor or the diagonal neighbor is itself a full column. This must match
     * vanilla exactly, not approximate it: any deviation is a real geometric gap between our
     * occluder/overlay geometry and the actual rendered water mesh, not a precision/bias issue, and
     * such a gap can go either direction depending on local shoreline/flow topology.
     */
    private static ColumnHeights computeVanillaColumnHeights(final ClientLevel level,
        final FluidStateManager.QueryCache fluidQueryCache, final BlockPos pos, final Fluid canonicalFluid,
        final FluidState topFluidState) {
        final float own = vanillaFluidHeight(level, fluidQueryCache, pos, canonicalFluid);
        if (own >= 1.0f) {
            return new ColumnHeights(canonicalFluid, topFluidState, pos, 1.0f, 1.0f, 1.0f, 1.0f);
        }

        final float north = vanillaFluidHeight(level, fluidQueryCache, pos.north(), canonicalFluid);
        final float south = vanillaFluidHeight(level, fluidQueryCache, pos.south(), canonicalFluid);
        final float east = vanillaFluidHeight(level, fluidQueryCache, pos.east(), canonicalFluid);
        final float west = vanillaFluidHeight(level, fluidQueryCache, pos.west(), canonicalFluid);

        final float nw = vanillaCornerHeight(level, fluidQueryCache, canonicalFluid, own, north, west, pos.north().west());
        final float ne = vanillaCornerHeight(level, fluidQueryCache, canonicalFluid, own, north, east, pos.north().east());
        final float sw = vanillaCornerHeight(level, fluidQueryCache, canonicalFluid, own, south, west, pos.south().west());
        final float se = vanillaCornerHeight(level, fluidQueryCache, canonicalFluid, own, south, east, pos.south().east());
        return new ColumnHeights(canonicalFluid, topFluidState, pos, nw, ne, sw, se);
    }

    private static float vanillaCornerHeight(final ClientLevel level,
        final FluidStateManager.QueryCache fluidQueryCache, final Fluid canonicalFluid, final float own,
        final float axisA, final float axisB, final BlockPos diagonalPos) {
        if (axisA >= 1.0f || axisB >= 1.0f) {
            return 1.0f;
        }
        final float[] acc = {0.0f, 0.0f};
        if (axisA > 0.0f || axisB > 0.0f) {
            final float diag = vanillaFluidHeight(level, fluidQueryCache, diagonalPos, canonicalFluid);
            if (diag >= 1.0f) {
                return 1.0f;
            }
            addWeightedHeight(acc, diag);
        }
        addWeightedHeight(acc, own);
        addWeightedHeight(acc, axisA);
        addWeightedHeight(acc, axisB);
        return acc[1] > 0.0f ? acc[0] / acc[1] : own;
    }

    private static void addWeightedHeight(final float[] acc, final float h) {
        if (h >= 0.8f) {
            acc[0] += h * 10.0f;
            acc[1] += 10.0f;
        } else if (h >= 0.0f) {
            acc[0] += h;
            acc[1] += 1.0f;
        }
        // h < 0.0f means solid (non-fluid, blocking) — contributes nothing, matching vanilla.
    }

    /** Vanilla's {@code LiquidBlockRenderer.getHeight}: 1.0 if stacked under the same fluid, this
     * block's own partial height if it's the same fluid but not stacked, 0.0 if open/non-solid and
     * not fluid, or -1.0 (excluded from the weighted average) if solid.
     *
     * <p>Deliberately does <em>not</em> apply the shipyard exclusion {@link #getRawExteriorFluidState}
     * uses for picking a legitimate starting sample point: vanilla's real renderer has no concept of
     * shipyards at all, it just reads whatever block is actually there. A neighbor of the sampled
     * water column falling inside the ship's reserved shipyard bounding region is common right at
     * the shoreline where the ship meets the water — exactly where this cap connects — and excluding
     * it (treating it as absent) would pull the weighted average down away from what vanilla actually
     * rasterizes, reintroducing the same kind of geometric mismatch this method exists to eliminate.</p>
     */
    private static float vanillaFluidHeight(final ClientLevel level,
        final FluidStateManager.QueryCache fluidQueryCache, final BlockPos pos, final Fluid canonicalFluid) {
        final BlockState state = FluidStateManager.getBlockState(level, pos, fluidQueryCache);
        final FluidState fs = state.getFluidState();
        if (fs.getType().isSame(canonicalFluid)) {
            final BlockState above = FluidStateManager.getBlockState(level, pos.above(), fluidQueryCache);
            return above.getFluidState().getType().isSame(canonicalFluid) ? 1.0f : fs.getOwnHeight();
        }
        return !state.isSolid() ? 0.0f : -1.0f;
    }

    static int scanRawExteriorFluidColumnTopY(
        final ClientLevel level,
        final BlockPos pos,
        final Fluid canonicalFluid,
        final FluidStateManager.QueryCache fluidQueryCache,
        final BlockPos.MutableBlockPos scanPos
    ) {
        scanPos.set(pos);
        final int maxYExclusive = level.getMaxBuildHeight();
        while (scanPos.getY() < maxYExclusive) {
            final FluidState current = getRawExteriorFluidState(level, scanPos, fluidQueryCache);
            if (current == null || canonicalSource(current.getType()) != canonicalFluid) {
                break;
            }
            scanPos.move(0, 1, 0);
        }
        return scanPos.getY() - 1;
    }

    private static @Nullable FluidState getRawExteriorFluidState(
        final ClientLevel level,
        final BlockPos pos,
        final FluidStateManager.QueryCache fluidQueryCache
    ) {
        if (VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            return null;
        }
        final FluidState rawFluid = FluidStateManager.getFluidState(level, pos, fluidQueryCache);
        return shouldUseExteriorFluidSample(false, rawFluid.isEmpty()) ? rawFluid : null;
    }

    public static void ensureExteriorFluidCacheLevel(final ClientLevel level) {
        if (lastSurfaceCacheLevel == level) return;
        lastSurfaceCacheLevel = level;
        clear();
    }

    private static void invalidateExteriorFluidChunk(final int chunkX, final int chunkZ) {
        final long chunkKey = exteriorFluidChunkKeyFromChunk(chunkX, chunkZ);
        EXTERIOR_FLUID_CHUNK_REVISIONS.put(chunkKey, currentExteriorFluidChunkRevision(chunkKey) + 1L);
    }

    private static long exteriorFluidChunkKey(final int blockX, final int blockZ) {
        return ChunkPos.asLong(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
    }

    private static long exteriorFluidChunkKeyFromChunk(final int chunkX, final int chunkZ) {
        return ChunkPos.asLong(chunkX, chunkZ);
    }

    private static long currentExteriorFluidChunkRevision(final long chunkKey) {
        return EXTERIOR_FLUID_CHUNK_REVISIONS.get(chunkKey);
    }

    static boolean shouldUseExteriorFluidSample(final boolean inShipyard, final boolean emptyFluid) {
        return !inShipyard && !emptyFluid;
    }

    static float rawExteriorFluidHeight(final FluidState fluidState) {
        return fluidState.getOwnHeight();
    }

    public static double sampleSurfaceY(final ClientLevel level, final BlockPos.MutableBlockPos fluidPos,
        final BlockPos.MutableBlockPos scanPos, final FluidStateManager.QueryCache fluidQueryCache,
        final double localX, final double localY, final double localZ,
        final double m00, final double m10, final double m20,
        final double m01, final double m11, final double m21,
        final double m02, final double m12, final double m22,
        final double tX, final double tY, final double tZ) {
        final double worldX = m00 * localX + m10 * localY + m20 * localZ + tX;
        final double worldY = m01 * localX + m11 * localY + m21 * localZ + tY;
        final double worldZ = m02 * localX + m12 * localY + m22 * localZ + tZ;
        final FluidSurfaceSample sample = findExteriorFluidSurface(level, fluidPos, scanPos, fluidQueryCache, worldX, worldY, worldZ);
        return sample != null ? sample.surfaceY : Double.NaN;
    }
}
