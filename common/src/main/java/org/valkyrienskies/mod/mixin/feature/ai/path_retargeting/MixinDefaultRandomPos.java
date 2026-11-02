package org.valkyrienskies.mod.mixin.feature.ai.path_retargeting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * @author Tomato
 * Should allow for mobs to pathfind on ships.
 */
@Mixin(DefaultRandomPos.class)
public class MixinDefaultRandomPos {
    @Inject(method = "generateRandomPosTowardDirection", at = @At("TAIL"), cancellable = true)
    private static void vs$retryWithShipProjection(
        PathfinderMob mob, int i, boolean isRestricted, BlockPos seed,
        CallbackInfoReturnable<BlockPos> cir,
        @Local(ordinal = 1) BlockPos worldCandidate
    ) {
        if (cir.getReturnValue() != null || worldCandidate == null) return;
        Level level = mob.level();
        if (level == null) return;

        List<LoadedShip> ships = new ArrayList<>();
        VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()
            .getIntersecting(VectorConversionsMCKt.toJOML(new AABB(worldCandidate)), VSGameUtilsKt.getDimensionId(level))
            .forEach(ships::add);
        ships.sort(Comparator.comparingLong(LoadedShip::getId));

        for (LoadedShip ship : ships) {
            Vector3d posInShip = ship.getWorldToShip()
                .transformPosition(VectorConversionsMCKt.toJOMLD(worldCandidate), new Vector3d());
            BlockPos shipLocalPos = BlockPos.containing(posInShip.x, posInShip.y, posInShip.z);
            if (GoalUtils.isRestricted(isRestricted, mob, worldCandidate)) continue;
            if (GoalUtils.isNotStable(mob.getNavigation(), shipLocalPos)) continue;
            if (GoalUtils.hasMalus(mob, shipLocalPos)) continue;

            Vector3d worldPos = ship.getShipToWorld().transformPosition(
                new Vector3d(shipLocalPos.getX() + 0.5, shipLocalPos.getY() + 0.5, shipLocalPos.getZ() + 0.5),
                new Vector3d()
            );
            cir.setReturnValue(BlockPos.containing(worldPos.x, worldPos.y, worldPos.z));
            return;
        }
    }

    @WrapOperation(method = {"getPos", "getPosTowards"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/util/RandomPos;generateRandomPos(Lnet/minecraft/world/entity/PathfinderMob;Ljava/util/function/Supplier;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 vs$projectGenerateRandomPosToWorld(PathfinderMob mob, Supplier<BlockPos> supplier,
        Operation<Vec3> original) {
        Vec3 result = original.call(mob, supplier);
        return result == null ? null : VSGameUtilsKt.toWorldCoordinates(mob.level(), result);
    }
}
