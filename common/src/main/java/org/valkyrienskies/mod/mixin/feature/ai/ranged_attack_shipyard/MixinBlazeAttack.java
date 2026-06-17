package org.valkyrienskies.mod.mixin.feature.ai.ranged_attack_shipyard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipyardEntityProjection;

// Blaze$BlazeAttackGoal.tick aims SmallFireball trajectories from getX/getY(0.5)/getZ
// reads on the target and shooter. For a shipyard target the raw reads return shipyard
// coords (~10⁷ blocks), so fireballs disappear into the void. Wrap each positional read
// to project shipyard entities to their world-rendered position.
@Mixin(targets = "net.minecraft.world.entity.monster.Blaze$BlazeAttackGoal")
public abstract class MixinBlazeAttack {

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D")
    )
    private double vs$projectTargetX(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldX(target);
    }

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY()D")
    )
    private double vs$projectTargetY(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldY(target);
    }

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY(D)D")
    )
    private double vs$projectTargetYFraction(
        final LivingEntity target, final double yFraction, final Operation<Double> original
    ) {
        return ShipyardEntityProjection.worldYAt(target, yFraction);
    }

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D")
    )
    private double vs$projectTargetZ(final LivingEntity target, final Operation<Double> original) {
        return ShipyardEntityProjection.worldZ(target);
    }
}
