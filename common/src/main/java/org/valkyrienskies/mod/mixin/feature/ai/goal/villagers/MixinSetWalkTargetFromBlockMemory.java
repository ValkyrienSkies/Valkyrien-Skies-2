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

// Walks a brain mob back to a POI-block memory (HOME bed, JOB_SITE workstation, MEETING_POINT, etc.). Vanilla compares globalPos.pos() (could be shipyard) to villager.blockPosition() (world) via distManhattan and uses Vec3.atBottomCenterOf to build a direction vector for DefaultRandomPos.getPosTowards — for ship POIs the distance reads in the thousands, the direction vector points to the void, the random-pos search fails 1000 attempts, and accessor.erase() wipes the memory each tick. Project the POI's cell center to world for both reads (using the Vec3 overload of toWorldCoordinates so the BlockPos corner-issue doesn't apply).
@Mixin(SetWalkTargetFromBlockMemory.class)
public class MixinSetWalkTargetFromBlockMemory {

    @WrapOperation(
        method = "method_47101",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distManhattan(Lnet/minecraft/core/Vec3i;)I"
        )
    )
    private static int vs$distManhattanOnWorldProjected(
        final BlockPos instance, final Vec3i other, final Operation<Integer> original,
        @Local(argsOnly = true) final Villager villager
    ) {
        final Vec3 worldCenter = VSGameUtilsKt.toWorldCoordinates(
            villager.level(), Vec3.atCenterOf(instance));
        return original.call(BlockPos.containing(worldCenter), other);
    }

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
