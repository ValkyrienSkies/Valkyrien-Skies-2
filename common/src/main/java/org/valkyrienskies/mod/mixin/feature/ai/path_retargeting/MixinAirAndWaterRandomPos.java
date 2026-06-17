package org.valkyrienskies.mod.mixin.feature.ai.path_retargeting;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.level.Level;
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
    @Inject(method = "generateRandomPos", at = @At("TAIL"), cancellable = true)
    private static void vs$retryWithShipProjection(
        PathfinderMob mob, int i, int j, int k, double d, double e, double f, boolean isRestricted,
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
            BlockPos bumped = RandomPos.moveUpOutOfSolid(shipLocalPos, level.getMaxBuildHeight(),
                p -> GoalUtils.isSolid(mob, p));
            if (GoalUtils.isRestricted(isRestricted, mob, worldCandidate)) continue;
            if (GoalUtils.hasMalus(mob, bumped)) continue;

            Vector3d worldPos = ship.getShipToWorld().transformPosition(
                new Vector3d(bumped.getX() + 0.5, bumped.getY() + 0.5, bumped.getZ() + 0.5),
                new Vector3d()
            );
            cir.setReturnValue(BlockPos.containing(worldPos.x, worldPos.y, worldPos.z));
            return;
        }
    }
}
