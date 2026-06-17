package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.pathfinding.IPathfindingFrameProvider;
import org.valkyrienskies.mod.common.pathfinding.NearbyShip;
import org.valkyrienskies.mod.common.pathfinding.NearbyShipsForPathfinding;
import org.valkyrienskies.mod.common.pathfinding.PathPerFrameRegistry;
import org.valkyrienskies.mod.common.pathfinding.PathfindStateCache;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;

@Mixin(PathNavigation.class)
public class MixinPathNavigation {

    @Shadow
    @Final
    protected Level level;

    @Shadow
    @Final
    protected Mob mob;

    @WrapOperation(
        method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/pathfinder/PathFinder;findPath(Lnet/minecraft/world/level/PathNavigationRegion;Lnet/minecraft/world/entity/Mob;Ljava/util/Set;FIF)Lnet/minecraft/world/level/pathfinder/Path;"
        )
    )
    private Path vs$wrapFindPath(
        final PathFinder pathFinder,
        final PathNavigationRegion region,
        final Mob mob,
        final Set<BlockPos> targets,
        final float searchRange,
        final int accuracy,
        final float maxVisitedNodesMultiplier,
        final Operation<Path> original,
        @Local(argsOnly = true, ordinal = 0) final int distance
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) {
            return original.call(pathFinder, region, mob, targets, searchRange, accuracy, maxVisitedNodesMultiplier);
        }

        PathfindStateCache.reset();

        final PathfindingFrame frame;
        try {
            frame = PathfindingFrame.resolveForMob(mob);
        } catch (final Throwable t) {
            return original.call(pathFinder, region, mob, targets, searchRange, accuracy, maxVisitedNodesMultiplier);
        }

        final PathfindingFrame priorCurrent = PathfindingFrame.getCURRENT().get();
        final IPathfindingFrameProvider frameProvider =
            (mob instanceof IPathfindingFrameProvider p) ? p : null;
        final PathfindingFrame priorMobFrame =
            (frameProvider != null) ? frameProvider.vs$getPathfindingFrame() : null;
        final NearbyShip[] priorNearby = PathPerFrameRegistry.getNearbyShips();

        final Set<BlockPos> frameTargets;
        final NearbyShip[] nearbyShips;
        final PathNavigationRegion effectiveRegion;
        try {
            frameTargets = vs$transformTargetsForFrame(level, frame, targets);
            nearbyShips = vs$collectNearbyShips(mob, searchRange);
            effectiveRegion = (frame instanceof PathfindingFrame.InShip inShip)
                ? vs$buildShipyardRegion(level, mob, inShip, searchRange, distance, region)
                : region;
        } catch (final Throwable t) {
            return original.call(pathFinder, region, mob, targets, searchRange, accuracy, maxVisitedNodesMultiplier);
        }

        PathfindingFrame.getCURRENT().set(frame);
        if (frameProvider != null) frameProvider.vs$setPathfindingFrame(frame);
        PathPerFrameRegistry.setNearbyShips(nearbyShips);

        final Path result;
        try {
            result = original.call(pathFinder, effectiveRegion, mob, frameTargets, searchRange, accuracy, maxVisitedNodesMultiplier);
        } catch (final Throwable t) {
            return null;
        } finally {
            PathfindingFrame.getCURRENT().set(priorCurrent);
            if (frameProvider != null) frameProvider.vs$setPathfindingFrame(priorMobFrame);
            PathPerFrameRegistry.setNearbyShips(priorNearby);
        }

        if (result != null) {
            PathPerFrameRegistry.register(result, frame);
        }
        return result;
    }

    /**
     * Center the region on the mob's shipyard projection so vanilla's region array-index
     * fast path hits the right chunks instead of routing every read through the peek
     * mixin's level.getBlockState redirect.
     */
    @Unique
    private static PathNavigationRegion vs$buildShipyardRegion(
        final Level level, final Mob mob, final PathfindingFrame.InShip inShip,
        final float searchRange, final int distance, final PathNavigationRegion fallback
    ) {
        try {
            final Vector3d shipyardPos = inShip.getShip().getTransform().getWorldToShip().transformPosition(
                new Vector3d(mob.getX(), mob.getY(), mob.getZ()), new Vector3d()
            );
            final int range = (int) (searchRange + (float) distance);
            final int sx = (int) shipyardPos.x;
            final int sy = (int) shipyardPos.y;
            final int sz = (int) shipyardPos.z;
            return new PathNavigationRegion(
                level,
                new BlockPos(sx - range, sy - range, sz - range),
                new BlockPos(sx + range, sy + range, sz + range)
            );
        } catch (final Throwable t) {
            return fallback;
        }
    }

    @Unique
    private static Set<BlockPos> vs$transformTargetsForFrame(
        final Level level, final PathfindingFrame frame, final Set<BlockPos> inputTargets
    ) {
        if (frame instanceof PathfindingFrame.InShip inShip) {
            final LoadedShip ship = inShip.getShip();
            final HashSet<BlockPos> out = new HashSet<>(inputTargets.size());
            for (final BlockPos target : inputTargets) {
                if (ship.getChunkClaim().contains(target.getX() >> 4, target.getZ() >> 4)) {
                    out.add(target);
                    continue;
                }
                final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
                    new Vector3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
                    new Vector3d()
                );
                out.add(BlockPos.containing(local.x, local.y, local.z));
            }
            return out;
        }
        if (frame instanceof PathfindingFrame.World) {
            final HashSet<BlockPos> out = new HashSet<>(inputTargets.size());
            for (final BlockPos target : inputTargets) {
                final Ship targetShip = VSGameUtilsKt.getLoadedShipManagingPos(level, target);
                if (targetShip != null) {
                    final Vector3d world = targetShip.getTransform().getShipToWorld().transformPosition(
                        new Vector3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
                        new Vector3d()
                    );
                    out.add(BlockPos.containing(world.x, world.y, world.z));
                } else {
                    out.add(target);
                }
            }
            return out;
        }
        return inputTargets;
    }

    @Unique
    private static NearbyShip[] vs$collectNearbyShips(final Mob mob, final float searchRange) {
        final int pad = (int) searchRange + 16;
        final BlockPos mobPos = mob.blockPosition();
        return NearbyShipsForPathfinding.collect(
            mob.level(),
            mobPos.offset(-pad, -pad, -pad),
            mobPos.offset(pad, pad, pad)
        );
    }

    /**
     * Vanilla getGroundY snaps wantedY to the world floor under the projected node Y.
     * For ship-mounted mobs that floor is unrelated to the actual ship surface, so the
     * snap drifts wantedY toward whatever world terrain happens to be below the ship.
     */
    @org.spongepowered.asm.mixin.injection.Inject(
        method = "getGroundY",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vs$bypassGroundSnapForShipMobs(
        final net.minecraft.world.phys.Vec3 vec3,
        final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double> cir
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        try {
            if (this.mob instanceof org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider provider
                && provider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
                cir.setReturnValue(vec3.y);
            }
        } catch (final Throwable t) {
            // swallow + fall back to vanilla
        }
    }

    @WrapOperation(
        method = "isStableDestination",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$getBlockStateIsNotStable(
        final Level instance, final BlockPos blockPos, final Operation<BlockState> original
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return original.call(instance, blockPos);
        try {
            return vs$getBlockStateIsNotStableImpl(instance, blockPos, original);
        } catch (final Throwable t) {
            return original.call(instance, blockPos);
        }
    }

    @Unique
    private BlockState vs$getBlockStateIsNotStableImpl(
        final Level instance, final BlockPos blockPos, final Operation<BlockState> original
    ) {
        final PathfindingFrame frame = PathfindingFrame.current(this.mob);
        if (frame instanceof PathfindingFrame.InShip inShip) {
            if (inShip.getShip().getChunkClaim().contains(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
                return original.call(instance, blockPos);
            }
            final Vector3d world = inShip.getShipToWorldSnapshot().transformPosition(
                new Vector3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5)
            );
            return original.call(instance, BlockPos.containing(world.x, world.y, world.z));
        }

        final BlockState originalState = original.call(instance, blockPos);
        if (originalState.isSolidRender(instance, blockPos)) return originalState;

        final double cx = blockPos.getX() + 0.5;
        final double cy = blockPos.getY() + 0.5;
        final double cz = blockPos.getZ() + 0.5;
        final Vector3d belowCenter = new Vector3d(cx, cy, cz);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(
            instance, new org.joml.primitives.AABBd(cx - 0.5, cy - 0.5, cz - 0.5, cx + 0.5, cy + 0.5, cz + 0.5))) {
            if (!ship.getWorldAABB().containsPoint(belowCenter)) continue;
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                belowCenter, new Vector3d()
            );
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState shipBlock = original.call(instance, shipLocalPos);
            if (shipBlock.isSolidRender(instance, shipLocalPos)) {
                return shipBlock;
            }
        }
        return originalState;
    }
}
