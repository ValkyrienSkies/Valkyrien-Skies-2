package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.SetClosestHomeAsWalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SetClosestHomeAsWalkTarget.class)
public class MixinSetClosestHomeAsWalkTarget {
    @WrapOperation(method = "method_47054", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    private static double onDistSqr(BlockPos instance, Vec3i vec3i, Operation<Double> original,
        @Local(argsOnly = true) PathfinderMob livingEntity) {
        return original.call(BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(livingEntity.level(), instance)), vec3i);
    }
}
