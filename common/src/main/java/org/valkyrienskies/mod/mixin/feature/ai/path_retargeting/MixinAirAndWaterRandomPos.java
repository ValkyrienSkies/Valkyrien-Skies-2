package org.valkyrienskies.mod.mixin.feature.ai.path_retargeting;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(AirAndWaterRandomPos.class)
public class MixinAirAndWaterRandomPos {
    @Inject(method = "generateRandomPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/util/GoalUtils;isOutsideLimits(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/PathfinderMob;)Z"),
        cancellable = true)
    private static void preGenerateRandomPos(PathfinderMob pathfinderMob, int i, int j, int k, double d, double e,
        double f, boolean bl, CallbackInfoReturnable<BlockPos> cir, @Local(ordinal = 1) BlockPos blockPos2) {
        if (pathfinderMob.level != null) {
            if (blockPos2 == null) {
                return;
            }
            AABB checker = new AABB(blockPos2);
            Iterable<LoadedShip> ships = VSGameUtilsKt.getShipObjectWorld(pathfinderMob.level).getLoadedShips().getIntersecting(
                VectorConversionsMCKt.toJOML(checker), VSGameUtilsKt.getDimensionId(pathfinderMob.level));
            if (ships.iterator().hasNext()) {
                for (LoadedShip ship : ships) {
                    Vector3d posInShip = ship.getWorldToShip()
                        .transformPosition(VectorConversionsMCKt.toJOMLD(blockPos2), new Vector3d());
                    BlockPos blockPosInShip = new BlockPos(VectorConversionsMCKt.toMinecraft(posInShip));
                    if (!GoalUtils.isRestricted(bl, pathfinderMob, blockPosInShip) &&
                        !GoalUtils.hasMalus(pathfinderMob, blockPos2 = RandomPos.moveUpOutOfSolid(blockPos2, pathfinderMob.level.getMaxBuildHeight(), arg2 -> GoalUtils.isSolid(pathfinderMob, arg2)))) {
                        cir.setReturnValue(blockPosInShip);
                        break;
                    }
                }
            }
        }
    }

}
