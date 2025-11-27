package org.valkyrienskies.mod.mixin.feature.ai.goal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ValidateNearbyPoi.class)
public class MixinValidateNearbyPoi {
    @WrapOperation(method = "method_47187(Lnet/minecraft/world/entity/ai/behavior/declarative/BehaviorBuilder$Instance;Lnet/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private static boolean onCloserToCenterThan(BlockPos instance, Position position, double v,
        Operation<Boolean> original, @Local
        LivingEntity livingEntity) {
        return original.call(BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(livingEntity.level(), instance)), position, v);
    }
}
