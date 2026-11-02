package org.valkyrienskies.mod.mixin.feature.ai.goal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Manual squared-distance check for StrollAroundPoi's "am I still near my POI?" gate — for ship-hosted POIs vanilla's BlockPos.containing(toWorldCoordinates(level, blockPos)) → closerToCenterThan corner-floor pattern mis-attributes by one cell on rotated/non-integer-offset ships, the gate fails, the behavior re-fires every tick and the villager keeps re-setting a walk target back toward the shipyard.
@Mixin(StrollAroundPoi.class)
public class MixinStrollAroundPoi {

    @WrapOperation(
        method = "method_47152",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private static boolean vs$closerProjected(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original,
        @Local(argsOnly = true) final PathfinderMob mob
    ) {
        final Vec3 cellCenterWorld = VSGameUtilsKt.toWorldCoordinates(mob.level(), Vec3.atCenterOf(instance));
        final Vec3 mobWorld = VSGameUtilsKt.toWorldCoordinates(
            mob.level(), new Vec3(position.x(), position.y(), position.z()));
        final double dx = cellCenterWorld.x - mobWorld.x;
        final double dy = cellCenterWorld.y - mobWorld.y;
        final double dz = cellCenterWorld.z - mobWorld.z;
        return dx * dx + dy * dy + dz * dz < dist * dist;
    }
}
