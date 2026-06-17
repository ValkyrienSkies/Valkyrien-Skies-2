package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.pathfinding.NearbyShip;
import org.valkyrienskies.mod.common.pathfinding.PathPerFrameRegistry;
import org.valkyrienskies.mod.common.pathfinding.PathTypeDanger;
import org.valkyrienskies.mod.common.pathfinding.PathfindStateCache;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;

// Pathfinder block/fluid-state overlay across frames: for InShip-frame paths, reads from the ship's local cells (with snapshot-projected world fallback for chunk-claim cells the ship doesn't physically occupy); for World-frame paths, overlays nearby ship blocks at the queried world cell so the path correctly "sees" ship hulls.
@Mixin(PathNavigationRegion.class)
public abstract class MixinPathNavigationRegion {

    @Unique
    private static final ThreadLocal<Boolean> vs$REENTRY = ThreadLocal.withInitial(() -> false);

    @Unique private static final double[] SAMPLE_DX = {0.5, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0};
    @Unique private static final double[] SAMPLE_DY = {0.5, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0};
    @Unique private static final double[] SAMPLE_DZ = {0.5, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 1.0, 1.0};

    @Unique
    private static final ThreadLocal<Vector3d> vs$VEC_SCRATCH =
        ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> vs$INSHIP_PROJ =
        ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<long[]> vs$SEEN_SCRATCH =
        ThreadLocal.withInitial(() -> new long[16]);

    // Point-in-AABB on the cell center is too tight at rotated-ship perimeters.
    @Unique
    private static boolean vs$cellOverlapsShipAABB(final double x0, final double y0, final double z0,
        final AABBdc shipAabb) {
        return shipAabb.maxX() >= x0 && shipAabb.minX() <= x0 + 1.0
            && shipAabb.maxY() >= y0 && shipAabb.minY() <= y0 + 1.0
            && shipAabb.maxZ() >= z0 && shipAabb.minZ() <= z0 + 1.0;
    }

    // Per-ship CENTER GATE: corner-only hits represent <50% coverage and shouldn't
    // promote the cell.
    @Unique
    private static List<BlockPos> vs$collectShipCandidates(
        final Level level, final NearbyShip[] nearbyShips,
        final double x0, final double y0, final double z0,
        final boolean requireBlocksMotion
    ) {
        if (nearbyShips.length == 0) return null;
        final Vector3d scratch = vs$VEC_SCRATCH.get();
        final long[] seen = vs$SEEN_SCRATCH.get();
        List<BlockPos> candidates = null;

        for (final NearbyShip ns : nearbyShips) {
            if (!vs$cellOverlapsShipAABB(x0, y0, z0, ns.worldAABB)) continue;
            final Matrix4dc worldToShip = ns.worldToShip;
            int seenCount = 0;

            scratch.set(x0 + SAMPLE_DX[0], y0 + SAMPLE_DY[0], z0 + SAMPLE_DZ[0]);
            worldToShip.transformPosition(scratch);
            final BlockPos centerPos = BlockPos.containing(scratch.x, scratch.y, scratch.z);
            final BlockState centerState = level.getBlockState(centerPos);
            final boolean centerOk = requireBlocksMotion ? centerState.blocksMotion() : !centerState.isAir();
            if (!centerOk) continue;

            seen[seenCount++] = centerPos.asLong();
            if (candidates == null) candidates = new ArrayList<>(4);
            candidates.add(centerPos);

            for (int i = 1; i < SAMPLE_DX.length; i++) {
                scratch.set(x0 + SAMPLE_DX[i], y0 + SAMPLE_DY[i], z0 + SAMPLE_DZ[i]);
                worldToShip.transformPosition(scratch);
                final BlockPos localPos = BlockPos.containing(scratch.x, scratch.y, scratch.z);
                final long key = localPos.asLong();
                boolean dup = false;
                for (int j = 0; j < seenCount; j++) {
                    if (seen[j] == key) { dup = true; break; }
                }
                if (dup) continue;
                seen[seenCount++] = key;
                final BlockState shipState = level.getBlockState(localPos);
                final boolean ok = requireBlocksMotion ? shipState.blocksMotion() : !shipState.isAir();
                if (!ok) continue;
                candidates.add(localPos);
            }
        }
        return candidates;
    }

    @Unique
    private static FluidState vs$findShipFluidAt(
        final Level level, final NearbyShip[] nearbyShips,
        final double x0, final double y0, final double z0
    ) {
        if (nearbyShips.length == 0) return null;
        final Vector3d scratch = vs$VEC_SCRATCH.get();
        for (final NearbyShip ns : nearbyShips) {
            if (!vs$cellOverlapsShipAABB(x0, y0, z0, ns.worldAABB)) continue;
            scratch.set(x0 + 0.5, y0 + 0.5, z0 + 0.5);
            ns.worldToShip.transformPosition(scratch);
            final FluidState fluid = level.getFluidState(BlockPos.containing(scratch.x, scratch.y, scratch.z));
            if (!fluid.isEmpty()) return fluid;
        }
        return null;
    }

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void vs$frameAwareGetBlockState(final BlockPos pos, final CallbackInfoReturnable<BlockState> cir) {
        if (vs$REENTRY.get() || !VSGameConfig.SERVER.getAiOnShips()) return;
        final PathfindingFrame frame = PathfindingFrame.current(null);
        if (frame == null) return;

        final long cacheKey = pos.asLong();
        final BlockState cached = PathfindStateCache.getBlock(cacheKey);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }

        final Level level = ((PathNavigationRegionAccessor) this).getLevel();
        if (level == null) return;

        vs$REENTRY.set(true);
        try {
            final NearbyShip[] nearbyShips = PathPerFrameRegistry.getNearbyShips();

            if (frame instanceof PathfindingFrame.InShip inShip) {
                final Ship ship = inShip.getShip();
                final boolean inClaim =
                    ship.getChunkClaim().contains(pos.getX() >> 4, pos.getZ() >> 4);

                if (inClaim) {
                    final BlockState vanilla = cir.getReturnValue();
                    if (!vanilla.isAir()) {
                        PathfindStateCache.putBlock(cacheKey, vanilla);
                        return;
                    }
                    // Chunk claim covers more cells than the ship physically occupies;
                    // projected world content is the correct read for those cells.
                }

                final Vector3d w = inShip.getShipToWorldSnapshot().transformPosition(
                    vs$INSHIP_PROJ.get().set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                );
                final BlockPos wp = BlockPos.containing(w.x, w.y, w.z);
                final BlockState vanillaAtWp = level.getBlockState(wp);
                BlockState resolved = vanillaAtWp;
                if (!vanillaAtWp.blocksMotion()) {
                    final List<BlockPos> overlay = vs$collectShipCandidates(
                        level, nearbyShips, wp.getX(), wp.getY(), wp.getZ(), false
                    );
                    if (overlay != null) {
                        resolved = PathTypeDanger.pickMostDangerousState(level, overlay);
                    }
                }
                PathfindStateCache.putBlock(cacheKey, resolved);
                cir.setReturnValue(resolved);
                return;
            }

            // Pass-through for water/lava/foliage/snow/waterlogged so a ship block at the
            // same world cell can still win against them.
            final BlockState vanilla = cir.getReturnValue();
            if (vanilla.blocksMotion()) {
                PathfindStateCache.putBlock(cacheKey, vanilla);
                return;
            }

            final List<BlockPos> overlay = vs$collectShipCandidates(
                level, nearbyShips, pos.getX(), pos.getY(), pos.getZ(), false
            );
            if (overlay != null) {
                final BlockState picked = PathTypeDanger.pickMostDangerousState(level, overlay);
                PathfindStateCache.putBlock(cacheKey, picked);
                cir.setReturnValue(picked);
            } else {
                PathfindStateCache.putBlock(cacheKey, vanilla);
            }
        } catch (final Throwable t) {
            // Swallow + fall back to vanilla.
        } finally {
            vs$REENTRY.set(false);
        }
    }

    @Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true)
    private void vs$frameAwareGetFluidState(final BlockPos pos, final CallbackInfoReturnable<FluidState> cir) {
        if (vs$REENTRY.get() || !VSGameConfig.SERVER.getAiOnShips()) return;
        final PathfindingFrame frame = PathfindingFrame.current(null);
        if (frame == null) return;

        final long cacheKey = pos.asLong();
        final FluidState cached = PathfindStateCache.getFluid(cacheKey);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }

        final Level level = ((PathNavigationRegionAccessor) this).getLevel();
        if (level == null) return;

        vs$REENTRY.set(true);
        try {
            final NearbyShip[] nearbyShips = PathPerFrameRegistry.getNearbyShips();

            if (frame instanceof PathfindingFrame.InShip inShip) {
                final Ship ship = inShip.getShip();
                final boolean inClaim =
                    ship.getChunkClaim().contains(pos.getX() >> 4, pos.getZ() >> 4);

                if (inClaim) {
                    final FluidState vanilla = cir.getReturnValue();
                    if (!vanilla.isEmpty()) {
                        PathfindStateCache.putFluid(cacheKey, vanilla);
                        return;
                    }
                }

                final Vector3d w = inShip.getShipToWorldSnapshot().transformPosition(
                    vs$INSHIP_PROJ.get().set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                );
                final BlockPos wp = BlockPos.containing(w.x, w.y, w.z);
                final FluidState vanillaAtWp = level.getFluidState(wp);
                FluidState resolved = vanillaAtWp;
                if (vanillaAtWp.isEmpty()) {
                    final FluidState found = vs$findShipFluidAt(
                        level, nearbyShips, wp.getX(), wp.getY(), wp.getZ()
                    );
                    if (found != null) resolved = found;
                }
                PathfindStateCache.putFluid(cacheKey, resolved);
                cir.setReturnValue(resolved);
                return;
            }

            final FluidState vanilla = cir.getReturnValue();
            if (!vanilla.isEmpty()) {
                // A ship block at the same world cell DISPLACES the fluid for pathing —
                // SwimNodeEvaluator only inspects fluid state, so without this swap the
                // swim path threads through solids.
                final List<BlockPos> hullCandidates = vs$collectShipCandidates(
                    level, nearbyShips, pos.getX(), pos.getY(), pos.getZ(), true
                );
                if (hullCandidates != null) {
                    final BlockState hull = PathTypeDanger.pickMostDangerousState(level, hullCandidates);
                    PathfindStateCache.putBlock(cacheKey, hull);
                    final FluidState empty = Fluids.EMPTY.defaultFluidState();
                    PathfindStateCache.putFluid(cacheKey, empty);
                    cir.setReturnValue(empty);
                    return;
                }
                PathfindStateCache.putFluid(cacheKey, vanilla);
                return;
            }

            final FluidState found = vs$findShipFluidAt(
                level, nearbyShips, pos.getX(), pos.getY(), pos.getZ()
            );
            if (found != null) {
                PathfindStateCache.putFluid(cacheKey, found);
                cir.setReturnValue(found);
            } else {
                PathfindStateCache.putFluid(cacheKey, vanilla);
            }
        } catch (final Throwable t) {
            // Swallow + fall back to vanilla.
        } finally {
            vs$REENTRY.set(false);
        }
    }
}
