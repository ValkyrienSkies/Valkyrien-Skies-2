package org.valkyrienskies.mod.mixin.mod_compat.hexcasting.hexal;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import ram.talia.hexal.api.linkable.ILinkable;
import ram.talia.hexal.common.casting.actions.spells.link.OpLinkOthers;

@Pseudo
@Mixin(OpLinkOthers.class)
public class MixinOpLinkOthers {
    @Redirect(method = "execute", at = @At(value = "INVOKE",
        target = "Lram/talia/hexal/api/linkable/ILinkable;isInRange(Lram/talia/hexal/api/linkable/ILinkable;)Z"), remap = false, require = 0)
    private boolean valkyrienskies$isInRange(ILinkable instance, ILinkable iLinkable, @Local(argsOnly = true) CastingEnvironment env) {
        ServerLevel level = env.getWorld();
        double maxDistance = 2 * (Math.sqrt(instance.maxSqrLinkRange()) + Math.sqrt(iLinkable.maxSqrLinkRange()));
        Vec3 first = instance.getPosition();
        Vec3 second = iLinkable.getPosition();
        return ValkyrienSkies.distance(level, first, second) <= maxDistance;
    }
}
