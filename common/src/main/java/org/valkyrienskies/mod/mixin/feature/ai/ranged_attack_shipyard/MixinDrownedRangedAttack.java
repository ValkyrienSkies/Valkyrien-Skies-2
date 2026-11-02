package org.valkyrienskies.mod.mixin.feature.ai.ranged_attack_shipyard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipyardEntityProjection;

/**
 * {@code Drowned.performRangedAttack} reads {@code livingEntity.getX()},
 * {@code getY(0.333)}, {@code getZ()} for the trident throw. Wrap each read to project
 * shipyard targets to world.
 */
@Mixin(Drowned.class)
public abstract class MixinDrownedRangedAttack {

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double vs$projectTargetX(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldX(target);
    }

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY(D)D")
    )
    private double vs$projectTargetYAt(final LivingEntity target, final double f, final Operation<Double> original) {
        return ShipyardEntityProjection.worldYAt(target, f);
    }

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double vs$projectTargetZ(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldZ(target);
    }
}
