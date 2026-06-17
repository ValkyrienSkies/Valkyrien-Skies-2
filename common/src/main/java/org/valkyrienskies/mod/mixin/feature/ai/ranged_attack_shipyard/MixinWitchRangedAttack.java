package org.valkyrienskies.mod.mixin.feature.ai.ranged_attack_shipyard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipyardEntityProjection;

@Mixin(Witch.class)
public abstract class MixinWitchRangedAttack {

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double vs$projectTargetX(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldX(target);
    }

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getEyeY()D")
    )
    private double vs$projectTargetEyeY(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldEyeY(target);
    }

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double vs$projectTargetZ(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldZ(target);
    }
}
