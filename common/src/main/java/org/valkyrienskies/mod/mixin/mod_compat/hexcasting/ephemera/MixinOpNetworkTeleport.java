package org.valkyrienskies.mod.mixin.mod_compat.hexcasting.ephemera;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.llamalad7.mixinextras.sugar.Local;
import net.beholderface.ephemera.casting.patterns.link.OpNetworkTeleport;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(OpNetworkTeleport.class)
public class MixinOpNetworkTeleport {
    @Redirect(method = "execute", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D", ordinal = 0), require = 0)
    private double valkyrienskies$transformTargetDistance(Vec3 instance, Vec3 d, @Local(argsOnly = true) CastingEnvironment env) {
        return ValkyrienSkies.distance(env.getWorld(), instance, d);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D", ordinal = 1), require = 0)
    private double valkyrienskies$transformDistance(Vec3 instance, Vec3 d, @Local(argsOnly = true) CastingEnvironment env) {
        return Math.pow(ValkyrienSkies.distance(env.getWorld(), instance, d), 2);
    }
}
