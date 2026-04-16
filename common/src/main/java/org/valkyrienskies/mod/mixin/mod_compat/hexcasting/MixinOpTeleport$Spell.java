package org.valkyrienskies.mod.mixin.mod_compat.hexcasting;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Mixin(targets = "at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport$Spell")
public class MixinOpTeleport$Spell {
    @Shadow @Final private Entity teleportee;

    @WrapOperation(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;length()D"))
    private double hexxyskies$modifyLength(Vec3 instance, Operation<Double> original, @Local(argsOnly = true) CastingEnvironment env) {
        Vec3 target = ValkyrienSkies.positionToWorld(env.getWorld(), teleportee.position().add(instance));
        return original.call(target.subtract(teleportee.position()));
    }
}
