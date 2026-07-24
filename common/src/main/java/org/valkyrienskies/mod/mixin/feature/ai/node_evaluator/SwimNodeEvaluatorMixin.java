package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.ShipPathfindingUtils;
import org.valkyrienskies.mod.util.FluidStateManager;

@Mixin(SwimNodeEvaluator.class)
public abstract class SwimNodeEvaluatorMixin extends NodeEvaluator {

    //region Single block obstacle path type
    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private FluidState getFluidStateRedirectPathType(BlockGetter instance, BlockPos blockPos,
        Operation<FluidState> original) {
        final FluidState[] fluidState = {original.call(instance, blockPos)};
        final FluidStateManager.QueryCache queryCache = new FluidStateManager.QueryCache();
        final BlockPos.MutableBlockPos groundPos = new BlockPos.MutableBlockPos();
        Level level = null;
        if (VSGameConfig.SERVER.getAiOnShips()) {
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
                    origY, origZ, 2,
                    (x, y, z) -> {
                        groundPos.set(net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(y),
                            net.minecraft.util.Mth.floor(z));
                        final FluidState tempFluidState = ShipPathfindingUtils.getShipAwareFluidState(finalLevel, groundPos, queryCache);
                        if (!tempFluidState.isEmpty()) { // Skip any empty results for the case of intersecting ships
                            fluidState[0] = tempFluidState;
                        }
                    });
            }
        }
        return fluidState[0];
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private BlockState getBlockStateRedirectPathType(BlockGetter instance, BlockPos blockPos,
        Operation<BlockState> original) {
        final BlockState[] blockState = {original.call(instance, blockPos)};
        if (instance instanceof PathNavigationRegion && blockState[0].isAir() && VSGameConfig.SERVER.getAiOnShips()) {
            final Level level = ((PathNavigationRegionAccessor) instance).getLevel();

            final double origX = blockPos.getX();
            final double origY = blockPos.getY();
            final double origZ = blockPos.getZ();
            VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                origY, origZ, 1,
                (x, y, z) -> {
                    final BlockPos groundPos = BlockPos.containing(x, y, z);
                    final BlockState tempBlockState = original.call(level, groundPos);
                    if (!tempBlockState.isAir()) { // Skip any empty results for the case of intersecting ships
                        blockState[0] = tempBlockState;
                    }
                });
        }
        return blockState[0];
    }

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isPathfindable(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/pathfinder/PathComputationType;)Z"),
        method = "getBlockPathType(Lnet/minecraft/world/level/BlockGetter;IIILnet/minecraft/world/entity/Mob;)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"
    )
    private boolean isPathFindableRedirectPathType(BlockState instance, BlockGetter blockGetter, BlockPos blockPos,
        PathComputationType pathComputationType, Operation<Boolean> original) {
        final boolean[] isPathFindable = {original.call(instance, blockGetter, blockPos, pathComputationType)};
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
                        final BlockPos groundPos = BlockPos.containing(x, y, z);
                        final boolean pathfindable =
                            original.call(instance, finalLevel, groundPos, pathComputationType);
                        if (pathfindable) { // Try to give a true result, not 100% accurate but method expects a single result
                            isPathFindable[0] = true;
                        }
                    });
            }
        }
        return isPathFindable[0];
    }
    //endregion

    @WrapOperation(
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/PathNavigationRegion;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"),
        method = "findAcceptedNode"
    )
    private FluidState getFluidStateRedirectGetNode(final PathNavigationRegion instance, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState[] fluidState = {getFluidState.call(instance, blockPos)};
        final Level level = ((PathNavigationRegionAccessor) instance).getLevel();
        final FluidStateManager.QueryCache queryCache = new FluidStateManager.QueryCache();
        final BlockPos.MutableBlockPos groundPos = new BlockPos.MutableBlockPos();
        if (VSGameConfig.SERVER.getAiOnShips()) {
            if (level != null && fluidState[0].isEmpty()) {
                final double origX = blockPos.getX();
                final double origY = blockPos.getY();
                final double origZ = blockPos.getZ();
                VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX,
                    origY, origZ, 1,
                    (x, y, z) -> {
                        groundPos.set(net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(y),
                            net.minecraft.util.Mth.floor(z));
                        final FluidState tempFluidState = ShipPathfindingUtils.getShipAwareFluidState(level, groundPos, queryCache);
                        if (!tempFluidState.isEmpty()) { // Skip any empty results for the case of intersecting ships
                            fluidState[0] = tempFluidState;
                        }
                    });
            }
        }
        return fluidState[0];
    }

    // 1.20.1 inlines the old isFree checks into getBlockPathType/findAcceptedNode-style flows,
    // so the PathNavigationRegion and path-type hooks above are the ship-aware equivalent.
}
