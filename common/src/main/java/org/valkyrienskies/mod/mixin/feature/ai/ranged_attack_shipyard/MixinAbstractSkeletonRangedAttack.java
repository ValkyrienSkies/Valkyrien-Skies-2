package org.valkyrienskies.mod.mixin.feature.ai.ranged_attack_shipyard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipyardEntityProjection;

// Vanilla bow trajectory reads target's raw shipyard coords for shipyard-resident
// targets — arrow shoots into the void. Project the target's getters to world.
// Covers Skeleton, Stray, WitherSkeleton.
@Mixin(AbstractSkeleton.class)
public abstract class MixinAbstractSkeletonRangedAttack {

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D", ordinal = 0)
    )
    private double vs$projectTargetX(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldX(target);
    }

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY(D)D", ordinal = 0)
    )
    private double vs$projectTargetYAt(final LivingEntity target, final double f, final Operation<Double> original) {
        return ShipyardEntityProjection.worldYAt(target, f);
    }

    @WrapOperation(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D", ordinal = 0)
    )
    private double vs$projectTargetZ(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldZ(target);
    }
}
