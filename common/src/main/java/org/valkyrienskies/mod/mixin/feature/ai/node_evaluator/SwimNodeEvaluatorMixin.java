package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import org.joml.Vector3d;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.pathfinding.NearbyShip;
import org.valkyrienskies.mod.common.pathfinding.PathPerFrameRegistry;
import org.valkyrienskies.mod.common.pathfinding.PathTypeDanger;
import org.valkyrienskies.mod.common.pathfinding.PathfindingFrame;

@Mixin(SwimNodeEvaluator.class)
public abstract class SwimNodeEvaluatorMixin extends NodeEvaluator {

    @Unique
    private static final ThreadLocal<Vector3d> VS$SCRATCH = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private FluidState vs$wrapFluidStatePathType(BlockGetter instance, BlockPos blockPos,
        Operation<FluidState> original) {
        return vs$peekFluid(instance, blockPos, original);
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private BlockState vs$wrapBlockStatePathType(BlockGetter instance, BlockPos blockPos,
        Operation<BlockState> original) {
        return vs$peekBlock(instance, blockPos, original);
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isPathfindable(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/pathfinder/PathComputationType;)Z"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private boolean vs$wrapIsPathFindable(BlockState instance, BlockGetter blockGetter, BlockPos blockPos,
        PathComputationType pathComputationType, Operation<Boolean> original) {
        final boolean vanilla = original.call(instance, blockGetter, blockPos, pathComputationType);
        if (vanilla || !VSGameConfig.SERVER.getAiOnShips()) return vanilla;
        final Level level = vs$levelFrom(blockGetter);
        if (level == null) return vanilla;

        try {
            final PathfindingFrame frame = PathfindingFrame.current(null);
            final BlockPos worldPos = vs$inShipOffClaimWorldPos(frame, blockPos);
            if (worldPos != null) {
                return original.call(level.getBlockState(worldPos), level, worldPos, pathComputationType);
            }
            if (frame instanceof PathfindingFrame.InShip) return vanilla;

            for (final NearbyShip ns : PathPerFrameRegistry.getNearbyShips()) {
                if (!vs$cellOverlapsShipAABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), ns.worldAABB)) continue;
                final BlockPos localPos = vs$projectCellToShipLocal(ns, blockPos);
                if (original.call(level.getBlockState(localPos), level, localPos, pathComputationType)) return true;
            }
            return vanilla;
        } catch (final Throwable t) {
            return vanilla;
        }
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/PathNavigationRegion;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "findAcceptedNode"
    )
    private FluidState vs$wrapFluidStateFindNode(final PathNavigationRegion instance, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        return vs$peekFluid(instance, blockPos, getFluidState);
    }

    @Unique
    private static boolean vs$cellOverlapsShipAABB(final double x0, final double y0, final double z0,
        final AABBdc shipAabb) {
        return shipAabb.maxX() >= x0 && shipAabb.minX() <= x0 + 1.0
            && shipAabb.maxY() >= y0 && shipAabb.minY() <= y0 + 1.0
            && shipAabb.maxZ() >= z0 && shipAabb.minZ() <= z0 + 1.0;
    }

    @Unique
    private static BlockPos vs$projectCellToShipLocal(final NearbyShip ns, final BlockPos worldCell) {
        final Vector3d scratch = VS$SCRATCH.get();
        scratch.set(worldCell.getX() + 0.5, worldCell.getY() + 0.5, worldCell.getZ() + 0.5);
        ns.worldToShip.transformPosition(scratch);
        return BlockPos.containing(scratch.x, scratch.y, scratch.z);
    }

    // InShip frame stepping out of its own chunk claim: the path needs to see the
    // world the ship is over, projected through the path's snapshot transform.
    @Unique
    private static BlockPos vs$inShipOffClaimWorldPos(final PathfindingFrame frame, final BlockPos blockPos) {
        if (!(frame instanceof PathfindingFrame.InShip inShip)) return null;
        final Ship ship = inShip.getShip();
        if (ship.getChunkClaim().contains(blockPos.getX() >> 4, blockPos.getZ() >> 4)) return null;
        final Vector3d w = inShip.getShipToWorldSnapshot().transformPosition(
            new Vector3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5)
        );
        return BlockPos.containing(w.x, w.y, w.z);
    }

    @Unique
    private static BlockState vs$findShipHullAt(final Level level, final BlockPos blockPos) {
        for (final NearbyShip ns : PathPerFrameRegistry.getNearbyShips()) {
            if (!vs$cellOverlapsShipAABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), ns.worldAABB)) continue;
            final BlockState shipState = level.getBlockState(vs$projectCellToShipLocal(ns, blockPos));
            if (shipState.blocksMotion()) return shipState;
        }
        return null;
    }

    @Unique
    private static Level vs$levelFrom(final BlockGetter bg) {
        if (bg instanceof PathNavigationRegion) {
            return ((PathNavigationRegionAccessor) bg).getLevel();
        } else if (bg instanceof Level l) {
            return l;
        }
        return null;
    }

    @Unique
    private static FluidState vs$peekFluid(final BlockGetter instance, final BlockPos blockPos,
        final Operation<FluidState> original) {
        final FluidState vanilla = original.call(instance, blockPos);
        if (!VSGameConfig.SERVER.getAiOnShips()) return vanilla;
        final Level level = vs$levelFrom(instance);
        if (level == null) return vanilla;

        try {
            final PathfindingFrame frame = PathfindingFrame.current(null);
            final BlockPos worldPos = vs$inShipOffClaimWorldPos(frame, blockPos);
            if (worldPos != null) return level.getFluidState(worldPos);
            if (frame instanceof PathfindingFrame.InShip) return vanilla;

            // A ship hull at this world cell displaces the world fluid for swim
            // pathfinding so swim mobs path around the ship rather than through it.
            if (!vanilla.isEmpty()) {
                return vs$findShipHullAt(level, blockPos) != null ? Fluids.EMPTY.defaultFluidState() : vanilla;
            }

            for (final NearbyShip ns : PathPerFrameRegistry.getNearbyShips()) {
                if (!vs$cellOverlapsShipAABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), ns.worldAABB)) continue;
                final FluidState shipFluid = level.getFluidState(vs$projectCellToShipLocal(ns, blockPos));
                if (!shipFluid.isEmpty()) return shipFluid;
            }
            return vanilla;
        } catch (final Throwable t) {
            return vanilla;
        }
    }

    @Unique
    private static BlockState vs$peekBlock(final BlockGetter instance, final BlockPos blockPos,
        final Operation<BlockState> original) {
        final BlockState vanilla = original.call(instance, blockPos);
        if (!VSGameConfig.SERVER.getAiOnShips()) return vanilla;
        final Level level = vs$levelFrom(instance);
        if (level == null) return vanilla;

        try {
            final PathfindingFrame frame = PathfindingFrame.current(null);
            final BlockPos worldPos = vs$inShipOffClaimWorldPos(frame, blockPos);
            if (worldPos != null) return level.getBlockState(worldPos);
            if (frame instanceof PathfindingFrame.InShip) return vanilla;
            if (vanilla.blocksMotion()) return vanilla;

            // Multiple overlapping ships can each contribute a block at the same world
            // cell; collect non-air candidates and let PathTypeDanger pick the most threatening.
            List<BlockPos> candidates = null;
            for (final NearbyShip ns : PathPerFrameRegistry.getNearbyShips()) {
                if (!vs$cellOverlapsShipAABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), ns.worldAABB)) continue;
                final BlockPos localPos = vs$projectCellToShipLocal(ns, blockPos);
                if (level.getBlockState(localPos).isAir()) continue;
                if (candidates == null) candidates = new ArrayList<>(2);
                candidates.add(localPos);
            }
            if (candidates != null) return PathTypeDanger.pickMostDangerousState(level, candidates);
            return vanilla;
        } catch (final Throwable t) {
            return vanilla;
        }
    }
}
