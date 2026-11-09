package org.valkyrienskies.mod.mixin.feature.ai.ranged_attack_shipyard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipyardEntityProjection;

// Llama.spit reads getX/getY(0.333)/getZ on both the llama and its target for the spit projectile trajectory; project shipyard-frame entities to world.
@Mixin(Llama.class)
public abstract class MixinLlamaSpit {

    @WrapOperation(
        method = "spit",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double vs$projectLivingX(final LivingEntity livingEntity, final Operation<Double> original) {
        return ShipyardEntityProjection.worldX(livingEntity);
    }

    @WrapOperation(
        method = "spit",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY(D)D")
    )
    private double vs$projectLivingYAt(final LivingEntity livingEntity, final double f, final Operation<Double> original) {
        return ShipyardEntityProjection.worldYAt(livingEntity, f);
    }

    @WrapOperation(
        method = "spit",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double vs$projectLivingZ(final LivingEntity livingEntity, final Operation<Double> original) {
        return ShipyardEntityProjection.worldZ(livingEntity);
    }
}
