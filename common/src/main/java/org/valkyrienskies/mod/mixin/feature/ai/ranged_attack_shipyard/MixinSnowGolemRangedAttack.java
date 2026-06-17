package org.valkyrienskies.mod.mixin.feature.ai.ranged_attack_shipyard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.SnowGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipyardEntityProjection;

// SnowGolem.performRangedAttack reads getX/getZ/getEyeY on both the snow golem and its target to compute the snowball trajectory; project shipyard-frame entities to world.
@Mixin(SnowGolem.class)
public abstract class MixinSnowGolemRangedAttack {

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getEyeY()D")
    )
    private double vs$projectLivingEyeY(final LivingEntity livingEntity, final Operation<Double> original) {
        return ShipyardEntityProjection.worldEyeY(livingEntity);
    }

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double vs$projectLivingX(final LivingEntity livingEntity, final Operation<Double> original) {
        return ShipyardEntityProjection.worldX(livingEntity);
    }

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double vs$projectLivingZ(final LivingEntity livingEntity, final Operation<Double> original) {
        return ShipyardEntityProjection.worldZ(livingEntity);
    }
}
