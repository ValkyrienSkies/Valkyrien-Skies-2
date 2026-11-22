package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Walks a brain mob back to a POI-block memory (HOME bed, JOB_SITE workstation, MEETING_POINT, etc.). Vanilla builds the direction vector for DefaultRandomPos.getPosTowards from Vec3.atBottomCenterOf(globalPos.pos()), which is shipyard for ship POIs — the direction points to the void, the random-pos search fails, and accessor.erase() wipes the memory each tick. Project the POI to world.
@Mixin(SetWalkTargetFromBlockMemory.class)
public class MixinSetWalkTargetFromBlockMemory {

    @WrapOperation(
        method = "method_47101",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private static Vec3 vs$atBottomCenterOfWorldProjected(
        final Vec3i pos, final Operation<Vec3> original,
        @Local(argsOnly = true) final Villager villager
    ) {
        if (pos instanceof BlockPos bp) {
            return VSGameUtilsKt.toWorldCoordinates(villager.level(), Vec3.atBottomCenterOf(bp));
        }
        return original.call(pos);
    }
}
