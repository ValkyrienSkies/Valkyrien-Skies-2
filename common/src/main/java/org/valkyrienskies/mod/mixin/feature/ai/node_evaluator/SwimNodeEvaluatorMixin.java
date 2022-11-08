package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(SwimNodeEvaluator.class)
public abstract class SwimNodeEvaluatorMixin extends NodeEvaluator {

    //region Single block obstacle path type
    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private FluidState getFluidStateRedirectPathType(final BlockGetter instance, final BlockPos blockPos) {
        final FluidState[] fluidState = {instance.getFluidState(blockPos)};
        Level level = null;
        if (!VSGameConfig.SERVER.getAiOnShips()) {
            if (instance instanceof PathNavigationRegion) {
                level = ((PathNavigationRegionAccessor) instance).getLevel();
            } else if (instance instanceof Level) {
                level = (Level) instance;
            }
            if (level != null && fluidState[0].isEmpty()) {

                final double origX = blockPos.getX();
                final double origY = blockPos.getY();
                final double origZ = blockPos.getZ();
                final Level finalLevel = level;
                VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                    origY, origZ, 1,
                    (x, y, z) -> {
                        final BlockPos groundPos = new BlockPos(x, y, z);
                        fluidState[0] = finalLevel.getFluidState(groundPos);
                    });
            }
        }
        return fluidState[0];
    }

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private BlockState getBlockStateRedirectPathType(final BlockGetter instance, final BlockPos blockPos) {
        final BlockState[] blockState = {instance.getBlockState(blockPos)};
        if (instance instanceof PathNavigationRegion && blockState[0].isAir() && VSGameConfig.SERVER.getAiOnShips()) {
            final Level level = ((PathNavigationRegionAccessor) instance).getLevel();

            final double origX = blockPos.getX();
            final double origY = blockPos.getY();
            final double origZ = blockPos.getZ();
            VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                origY, origZ, 1,
                (x, y, z) -> {
                    final BlockPos groundPos = new BlockPos(x, y, z);
                    blockState[0] = level.getBlockState(groundPos);
                });
        }
        return blockState[0];
    }

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isPathfindable(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/pathfinder/PathComputationType;)Z"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private boolean isPathFindableRedirectPathType(final BlockState instance, final BlockGetter blockGetter,
        final BlockPos blockPos,
        final PathComputationType pathComputationType) {
        final boolean[] isPathFindable = {instance.isPathfindable(blockGetter, blockPos, pathComputationType)};
        if (!isPathFindable[0] && VSGameConfig.SERVER.getAiOnShips()) {
            Level level = null;
            if (blockGetter instanceof PathNavigationRegion) {
                level = ((PathNavigationRegionAccessor) blockGetter).getLevel();
            } else if (blockGetter instanceof Level) {
                level = (Level) blockGetter;
            }
            if (level != null) {
                final double origX = blockPos.getX();
                final double origY = blockPos.getY();
                final double origZ = blockPos.getZ();
                final Level finalLevel = level;
                VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                    origY, origZ, 1,
                    (x, y, z) -> {
                        final BlockPos groundPos = new BlockPos(x, y, z);
                        isPathFindable[0] = instance.isPathfindable(finalLevel, groundPos, pathComputationType);
                    });
            }
        }
        return isPathFindable[0];
    }
    //endregion

    //region Area obstacle path type
    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/PathNavigationRegion;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "isFree"
    )
    private FluidState getFluidStateRedirectIsFree(final PathNavigationRegion instance, final BlockPos pos) {
        final FluidState[] fluidState = {instance.getFluidState(pos)};
        if (fluidState[0].isEmpty() && VSGameConfig.SERVER.getAiOnShips()) {
            final Level level = ((PathNavigationRegionAccessor) instance).getLevel();

            final double origX = pos.getX();
            final double origY = pos.getY();
            final double origZ = pos.getZ();
            VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                origY, origZ, 1,
                (x, y, z) -> {
                    final BlockPos groundPos = new BlockPos(x, y, z);
                    fluidState[0] = level.getFluidState(groundPos);
                });
        }
        return fluidState[0];
    }

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/PathNavigationRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        method = "isFree"
    )
    private BlockState getBlockStateRedirectIsFree(final PathNavigationRegion instance, final BlockPos pos) {
        final BlockState[] blockState = {instance.getBlockState(pos)};
        if (blockState[0].isAir() && VSGameConfig.SERVER.getAiOnShips()) {
            final Level level = ((PathNavigationRegionAccessor) instance).getLevel();

            final double origX = pos.getX();
            final double origY = pos.getY();
            final double origZ = pos.getZ();
            VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                origY, origZ, 1,
                (x, y, z) -> {
                    final BlockPos groundPos = new BlockPos(x, y, z);
                    blockState[0] = level.getBlockState(groundPos);
                });
        }
        return blockState[0];
    }

    @Redirect(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isPathfindable(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/pathfinder/PathComputationType;)Z"),
        method = "isFree"
    )
    private boolean isPathFindableRedirectIsFree(final BlockState instance, final BlockGetter blockGetter,
        final BlockPos blockPos,
        final PathComputationType pathComputationType) {
        final boolean[] isPathFindable = {instance.isPathfindable(blockGetter, blockPos, pathComputationType)};
        if (!isPathFindable[0] && VSGameConfig.SERVER.getAiOnShips()) {
            Level level = null;
            if (blockGetter instanceof PathNavigationRegion) {
                level = ((PathNavigationRegionAccessor) blockGetter).getLevel();
            } else if (blockGetter instanceof Level) {
                level = (Level) blockGetter;
            }
            if (level != null) {
                final double origX = blockPos.getX();
                final double origY = blockPos.getY();
                final double origZ = blockPos.getZ();
                final Level finalLevel = level;
                VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                    origY, origZ, 1,
                    (x, y, z) -> {
                        final BlockPos groundPos = new BlockPos(x, y, z);
                        isPathFindable[0] = instance.isPathfindable(finalLevel, groundPos, pathComputationType);
                    });
            }
        }
        return isPathFindable[0];
    }
    //endregion
}
