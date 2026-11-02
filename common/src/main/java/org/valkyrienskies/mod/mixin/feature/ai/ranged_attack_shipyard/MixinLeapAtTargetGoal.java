package org.valkyrienskies.mod.mixin.feature.ai.ranged_attack_shipyard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipyardEntityProjection;

// LeapAtTargetGoal.start reads getX/getZ on both the mob and its target to compute the leap direction; project shipyard-frame entities to world.
@Mixin(LeapAtTargetGoal.class)
public abstract class MixinLeapAtTargetGoal {

    @WrapOperation(
        method = "start",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double vs$projectLivingX(final LivingEntity livingEntity, final Operation<Double> original) {
        return ShipyardEntityProjection.worldX(livingEntity);
    }

    @WrapOperation(
        method = "start",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double vs$projectLivingZ(final LivingEntity livingEntity, final Operation<Double> original) {
        return ShipyardEntityProjection.worldZ(livingEntity);
    }
}
