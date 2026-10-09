package org.valkyrienskies.mod.common.fluid.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.BitSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;
import org.valkyrienskies.mod.common.fluid.FloodedFluidClientCache;
import org.valkyrienskies.mod.common.fluid.FluidTopologyClientCache;

/**
 * Dense per-ship grid view over the sparse flood snapshots synchronized from vs-core.
 *
 * <p>The client fluid renderers were written against a dense, ship-local voxel grid: bounds plus a
 * handful of parallel {@link BitSet}s they walk by flat index. vs-core instead streams two sparse
 * snapshots — {@link FluidTopologyClientCache} for the simulation domain and its connectivity flags,
 * {@link FloodedFluidClientCache} for what is actually wet — so this class materializes the dense
 * form once per ship per snapshot revision and hands the same shape to every renderer.</p>
 *
 * <p>Cell classification:</p>
 * <ul>
 *   <li>{@code interior} — the cell is part of the ship's fluid simulation domain (a topology voxel
 *       exists for it). Cells outside the domain are the surrounding world.</li>
 *   <li>{@code open} — fluid could occupy some part of the cell, derived from block shapes via
 *       {@link ShipFluidCullBridge#isOpenCell}.</li>
 *   <li>{@code waterReachable} — the cell holds fluid: the streamed fill for domain cells, the
 *       level's own fluid state for the surrounding ring.</li>
 *   <li>{@code unreachableVoid} — a sealed-interior domain cell that is currently dry, i.e. an
 *       air pocket.</li>
 * </ul>
 *
 * <p>The grid is the domain's bounding box grown by {@link #EXTERIOR_MARGIN}, so the renderers'
 * six-neighbour boundary scans always have a ring of world cells to compare the hull against.</p>
 */
public final class ShipFluidRenderSnapshot {

    /** Cells of surrounding world kept around the domain so boundary scans have somewhere to look. */
    private static final int EXTERIOR_MARGIN = 2;

    private static final Long2ObjectOpenHashMap<ShipFluidRenderSnapshot> CACHE = new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet LOADED_SHIP_IDS = new LongOpenHashSet();
    private static @Nullable ClientLevel lastLevel = null;

    private final long geometryRevision;
    private final Fluid floodFluid;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BitSet open;
    private final BitSet interior;
    private final BitSet waterReachable;
    private final BitSet unreachableVoid;
    /** Streamed fill per cell, 0-255, indexed like the bit sets. Zero outside the domain. */
    private final byte[] fillAmount;

    private ShipFluidRenderSnapshot(final long geometryRevision, final Fluid floodFluid, final int minX,
        final int minY, final int minZ, final int sizeX, final int sizeY, final int sizeZ, final BitSet open,
        final BitSet interior, final BitSet waterReachable, final BitSet unreachableVoid, final byte[] fillAmount) {
        this.geometryRevision = geometryRevision;
        this.floodFluid = floodFluid;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.open = open;
        this.interior = interior;
        this.waterReachable = waterReachable;
        this.unreachableVoid = unreachableVoid;
        this.fillAmount = fillAmount;
    }

    public long getGeometryRevision() {
        return geometryRevision;
    }

    public Fluid getFloodFluid() {
        return floodFluid;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public BitSet getOpen() {
        return open;
    }

    public BitSet getInterior() {
        return interior;
    }

    public BitSet getWaterReachable() {
        return waterReachable;
    }

    public BitSet getUnreachableVoid() {
        return unreachableVoid;
    }

    /** Streamed fill for a cell, 0-255. 255 for world fluid outside the domain. */
    public int getFillAmount(final int idx) {
        return idx < 0 || idx >= fillAmount.length ? 0 : fillAmount[idx] & 0xFF;
    }

    /** Fraction of the cell that is submerged, 0-1. */
    public double getFillFraction(final int idx) {
        return getFillAmount(idx) / 255.0;
    }

    public int index(final int lx, final int ly, final int lz) {
        return lx + sizeX * (ly + sizeY * lz);
    }

    /**
     * The dense grid for this ship, rebuilt only when either streamed snapshot advances.
     *
     * @return {@code null} when the ship has no usable topology snapshot, in which case there is
     *     nothing for the fluid renderers to draw.
     */
    public static @Nullable ShipFluidRenderSnapshot get(final ClientLevel level, final long shipId) {
        if (lastLevel != level) {
            CACHE.clear();
            lastLevel = level;
        }

        final VsiClientShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        final FluidTopologyClientCache.CachedSnapshot topology = FluidTopologyClientCache.get(shipWorld, shipId);
        if (topology == null) {
            CACHE.remove(shipId);
            return null;
        }
        final FloodedFluidClientCache.CachedSnapshot flooding = FloodedFluidClientCache.get(shipWorld, shipId);

        final long revision = revisionOf(topology, flooding);
        final ShipFluidRenderSnapshot cached = CACHE.get(shipId);
        if (cached != null && cached.geometryRevision == revision) {
            return cached;
        }

        final ShipFluidRenderSnapshot built = build(level, revision, topology, flooding);
        if (built == null) {
            CACHE.remove(shipId);
            return null;
        }
        CACHE.put(shipId, built);
        return built;
    }

    /** Drops grids for ships that are no longer loaded. */
    public static void prune(final VsiClientShipWorld shipWorld) {
        LOADED_SHIP_IDS.clear();
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            LOADED_SHIP_IDS.add(ship.getId());
        }
        CACHE.keySet().removeIf(shipId -> !LOADED_SHIP_IDS.contains(shipId));
    }

    public static void clear() {
        CACHE.clear();
        lastLevel = null;
    }

    private static long revisionOf(final FluidTopologyClientCache.CachedSnapshot topology,
        final @Nullable FloodedFluidClientCache.CachedSnapshot flooding) {
        final long topologySequence = topology.getSnapshot().getStreamSequence();
        final long floodingSequence = flooding == null ? -1L : flooding.getSnapshot().getStreamSequence();
        return topologySequence * 31L + floodingSequence;
    }

    private static @Nullable ShipFluidRenderSnapshot build(final ClientLevel level, final long revision,
        final FluidTopologyClientCache.CachedSnapshot topology,
        final @Nullable FloodedFluidClientCache.CachedSnapshot flooding) {

        final var topologyVoxels = topology.getSnapshot().getVoxels();
        if (topologyVoxels.isEmpty()) return null;

        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highY = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (final VsiFluidTopologyVoxel voxel : topologyVoxels) {
            lowX = Math.min(lowX, voxel.getPositionX());
            lowY = Math.min(lowY, voxel.getPositionY());
            lowZ = Math.min(lowZ, voxel.getPositionZ());
            highX = Math.max(highX, voxel.getPositionX());
            highY = Math.max(highY, voxel.getPositionY());
            highZ = Math.max(highZ, voxel.getPositionZ());
        }

        final int minX = lowX - EXTERIOR_MARGIN;
        final int minY = lowY - EXTERIOR_MARGIN;
        final int minZ = lowZ - EXTERIOR_MARGIN;
        final int sizeX = (highX - lowX + 1) + 2 * EXTERIOR_MARGIN;
        final int sizeY = (highY - lowY + 1) + 2 * EXTERIOR_MARGIN;
        final int sizeZ = (highZ - lowZ + 1) + 2 * EXTERIOR_MARGIN;
        final int volume = sizeX * sizeY * sizeZ;
        if (volume <= 0) return null;

        final BitSet open = new BitSet(volume);
        final BitSet interior = new BitSet(volume);
        final BitSet waterReachable = new BitSet(volume);
        final BitSet unreachableVoid = new BitSet(volume);
        final byte[] fillAmount = new byte[volume];

        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Fluid floodFluid = Fluids.WATER;
        boolean sawFloodFluid = false;

        int idx = 0;
        for (int lz = 0; lz < sizeZ; lz++) {
            for (int ly = 0; ly < sizeY; ly++) {
                for (int lx = 0; lx < sizeX; lx++, idx++) {
                    final int x = minX + lx;
                    final int y = minY + ly;
                    final int z = minZ + lz;
                    pos.set(x, y, z);
                    final BlockState state = level.getBlockState(pos);

                    if (ShipFluidCullBridge.isOpenCell(level, pos, state)) {
                        open.set(idx);
                    }

                    final VsiFluidTopologyVoxel topologyVoxel = topology.get(x, y, z);
                    if (topologyVoxel == null) {
                        // Outside the simulation domain: the level's own fluid is the truth.
                        if (!state.getFluidState().isEmpty()) {
                            waterReachable.set(idx);
                            fillAmount[idx] = (byte) 255;
                        }
                        continue;
                    }

                    interior.set(idx);

                    final VsiFluidFloodedVoxel floodedVoxel = flooding == null ? null : flooding.get(x, y, z);
                    if (floodedVoxel != null && floodedVoxel.getFillAmount() > 0) {
                        waterReachable.set(idx);
                        fillAmount[idx] = (byte) Math.min(255, floodedVoxel.getFillAmount());
                        if (!sawFloodFluid) {
                            final Fluid resolved =
                                MassDatapackResolver.INSTANCE.getFlowingFluid(floodedVoxel.getFluidId());
                            if (resolved != null) {
                                floodFluid = resolved;
                                sawFloodFluid = true;
                            }
                        }
                    } else if (topologyVoxel.getSealedInterior()) {
                        unreachableVoid.set(idx);
                    }
                }
            }
        }

        return new ShipFluidRenderSnapshot(revision, floodFluid, minX, minY, minZ, sizeX, sizeY, sizeZ,
            open, interior, waterReachable, unreachableVoid, fillAmount);
    }

    /**
     * Whether a world position sits inside a sealed, currently dry volume of some ship.
     *
     * <p>This is the "the camera is in an air pocket" test the interior fog uses.</p>
     */
    public static boolean isWorldPosInShipAirPocket(final @Nullable ClientLevel level, final double worldX,
        final double worldY, final double worldZ) {
        return classifyWorldPos(level, worldX, worldY, worldZ, true);
    }

    /**
     * Whether a world position sits inside any dry domain cell, sealed or not.
     *
     * <p>These are the volumes where the surrounding world's fluid must not be drawn.</p>
     */
    public static boolean isWorldPosInShipWorldFluidSuppressionZone(final @Nullable ClientLevel level,
        final double worldX, final double worldY, final double worldZ) {
        return classifyWorldPos(level, worldX, worldY, worldZ, false);
    }

    private static boolean classifyWorldPos(final @Nullable ClientLevel level, final double worldX,
        final double worldY, final double worldZ, final boolean requireSealed) {
        if (level == null) return false;

        final VsiClientShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        final Vector3d local = new Vector3d();
        for (final ClientShip ship : shipWorld.getLoadedShips()) {
            if (!ship.getRenderAABB().containsPoint(worldX, worldY, worldZ)) continue;

            final FluidTopologyClientCache.CachedSnapshot topology =
                FluidTopologyClientCache.get(shipWorld, ship.getId());
            if (topology == null) continue;

            ship.getRenderTransform().getWorldToShip().transformPosition(worldX, worldY, worldZ, local);
            final int voxelX = floorToInt(local.x);
            final int voxelY = floorToInt(local.y);
            final int voxelZ = floorToInt(local.z);

            final VsiFluidTopologyVoxel voxel = topology.get(voxelX, voxelY, voxelZ);
            if (voxel == null) continue;
            if (requireSealed && !voxel.getSealedInterior()) continue;

            final FloodedFluidClientCache.CachedSnapshot flooding =
                FloodedFluidClientCache.get(shipWorld, ship.getId());
            final VsiFluidFloodedVoxel flooded = flooding == null ? null : flooding.get(voxelX, voxelY, voxelZ);
            if (flooded != null && isLocallySubmerged(flooded.getFillAmount(), local.y - voxelY)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** Mirrors {@code FloodedFluidClientCache}'s fill test, which is not visible from this package. */
    private static boolean isLocallySubmerged(final int fillAmount, final double localHeight) {
        if (fillAmount <= 0) return false;
        if (fillAmount >= 255) return true;
        return localHeight <= fillAmount / 255.0;
    }

    private static int floorToInt(final double value) {
        final int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
