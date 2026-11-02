package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.ShipPathfindingUtils;

@Mixin(WalkNodeEvaluator.class)
public class WalkNodeEvaluatorMixin {

    @Unique
    private static boolean isModifyingPathType = false;

    //Several node evaluators use getBlockPathTypeRaw, including WalkNodeEvaluator's specialized getBlockPathTypeStatic.
    //Mojang should really put this in the base NodeEvaluator.
    @Shadow()
    protected static BlockPathTypes getBlockPathTypeRaw(final BlockGetter blockGetter, final BlockPos blockPos) {
        return null;
    }

    @Unique
    private static BlockPathTypes vs$resolveCandidatePathType(final Level level, final BlockPos blockPos) {
        BlockPathTypes pathType = getBlockPathTypeRaw(level, blockPos);
        if (pathType == BlockPathTypes.OPEN || pathType == BlockPathTypes.BLOCKED) {
            final BlockPathTypes belowType = getBlockPathTypeRaw(level, blockPos.below());
            if (vs$scorePathType(belowType) > vs$scorePathType(pathType)) {
                pathType = belowType;
            }
        }
        return pathType;
    }

    @Unique
    private static int vs$scorePathType(final BlockPathTypes pathType) {
        if (pathType == null) {
            return Integer.MIN_VALUE;
        }
        if (pathType == BlockPathTypes.OPEN) {
            return 0;
        }
        if (pathType == BlockPathTypes.BLOCKED) {
            return 1;
        }
        return 2;
    }

    @Inject(method = "getBlockPathTypeRaw", at = @At("RETURN"), cancellable = true)
    private static void getBlockPathTypeForShips(final BlockGetter blockGetter, final BlockPos blockPos,
        final CallbackInfoReturnable<BlockPathTypes> cir) {
        if (isModifyingPathType || !VSGameConfig.SERVER.getAiOnShips()) {
            return;
        }

        final Level level = ShipPathfindingUtils.getLevel(blockGetter);
        if (level == null) {
            return;
        }

        isModifyingPathType = true;
        try {
            final BlockPathTypes[] bestType = {cir.getReturnValue()};
            final int[] bestScore = {vs$scorePathType(bestType[0])};

            VSGameUtilsKt.transformToNearbyShipsAndWorld(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), 0.5,
                (x, y, z) -> {
                    final BlockPathTypes candidateType =
                        vs$resolveCandidatePathType(level, BlockPos.containing(x, y, z));
                    final int candidateScore = vs$scorePathType(candidateType);
                    if (candidateScore > bestScore[0]) {
                        bestType[0] = candidateType;
                        bestScore[0] = candidateScore;
                    }
                });

            if (bestType[0] != null && bestType[0] != cir.getReturnValue()) {
                cir.setReturnValue(bestType[0]);
            }
        } finally {
            isModifyingPathType = false;
        }
    }
}
