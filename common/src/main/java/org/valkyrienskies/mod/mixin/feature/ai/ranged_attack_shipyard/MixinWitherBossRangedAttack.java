package org.valkyrienskies.mod.mixin.feature.ai.ranged_attack_shipyard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipyardEntityProjection;

@Mixin(WitherBoss.class)
public abstract class MixinWitherBossRangedAttack {

    @WrapOperation(
        method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double vs$projectTargetX(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldX(target);
    }

    @WrapOperation(
        method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY()D")
    )
    private double vs$projectTargetY(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldY(target);
    }

    @WrapOperation(
        method = "performRangedAttack(ILnet/minecraft/world/entity/LivingEntity;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double vs$projectTargetZ(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldZ(target);
    }
}
