package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.ai.behavior.StrollToPoiList;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// POI-list version of MixinStrollToPoi (villagers walk toward each entry in a POI-list memory). Same corner-safe world-projection fix.
@Mixin(StrollToPoiList.class)
public class MixinStrollToPoiList {

    @WrapOperation(
        method = "method_47160",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private static boolean vs$closerProjected(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original,
        @Local(argsOnly = true) final Villager villager
    ) {
        final Vec3 cellCenterWorld = VSGameUtilsKt.toWorldCoordinates(villager.level(), Vec3.atCenterOf(instance));
        final Vec3 villagerWorld = VSGameUtilsKt.toWorldCoordinates(
            villager.level(), new Vec3(position.x(), position.y(), position.z()));
        final double dx = cellCenterWorld.x - villagerWorld.x;
        final double dy = cellCenterWorld.y - villagerWorld.y;
        final double dz = cellCenterWorld.z - villagerWorld.z;
        return dx * dx + dy * dy + dz * dz < dist * dist;
    }
}
