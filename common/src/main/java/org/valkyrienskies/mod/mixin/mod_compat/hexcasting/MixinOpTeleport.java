package org.valkyrienskies.mod.mixin.mod_compat.hexcasting;

import at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Mixin(OpTeleport.class)
public class MixinOpTeleport {
    @ModifyVariable(method = "teleportRespectSticky", at = @At("STORE"), name = "target")
    private Vec3 hexxyskies$modifyTarget(Vec3 target, @Local(argsOnly = true) ServerLevel world) {
        return ValkyrienSkies.positionToWorld(world, target);
    }
}
