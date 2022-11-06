package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(WalkNodeEvaluator.class)
public class WalkNodeEvaluatorMixin {

    @Unique
    private static boolean isModifyingPathType = false;

    @Shadow()
    protected static BlockPathTypes getBlockPathTypeRaw(final BlockGetter blockGetter, final BlockPos blockPos) {
        return null;
    }

    @Inject(method = "getBlockPathTypeRaw", at = @At("RETURN"), cancellable = true)
    private static void getBlockPathTypeForShips(final BlockGetter blockGetter, final BlockPos blockPos,
        final CallbackInfoReturnable<BlockPathTypes> cir) {
        if (isModifyingPathType) {
            return;
        }

        isModifyingPathType = true;

        final double origX = blockPos.getX();
        final double origY = blockPos.getY();
        final double origZ = blockPos.getZ();

        if (blockGetter instanceof PathNavigationRegion) {
            VSGameUtilsKt.transformToNearbyShipsAndWorld(((PathNavigationRegionAccessor) blockGetter).getLevel(), origX,
                origY, origZ, 1,
                (x, y, z) -> {
                    cir.setReturnValue(
                        BlockPathTypes.WALKABLE);//getBlockPathTypeRaw(blockGetter, new BlockPos(x, y, z)));
                });
        }

        isModifyingPathType = false;
    }
}
