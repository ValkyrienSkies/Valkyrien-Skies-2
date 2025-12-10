package org.valkyrienskies.mod.mixin.mod_compat.hexcasting.ephemera;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.beholderface.ephemera.casting.patterns.link.OpNetworkTeleport;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(OpNetworkTeleport.class)
public class MixinOpNetworkTeleport {
    @WrapOperation(method = "execute", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D", ordinal = 0))
    private double valkyrienskies$transformTargetDistance(Vec3 instance, Vec3 vec3, Operation<Double> original, @Local(argsOnly = true) CastingEnvironment env) {
        return ValkyrienSkies.distance(env.getWorld(), instance, vec3);
    }

    @WrapOperation(method = "execute", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D", ordinal = 1))
    private double valkyrienskies$transformDistance(Vec3 instance, Vec3 vec3, Operation<Double> original, @Local(argsOnly = true) CastingEnvironment env) {
        return Math.pow(ValkyrienSkies.distance(env.getWorld(), instance, vec3), 2);
    }
}
