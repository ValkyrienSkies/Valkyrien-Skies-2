package org.valkyrienskies.mod.mixin.mod_compat.common_create;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.simibubi.create.content.contraptions.ControlledContraptionEntity")
public abstract class MixinControlledContraptionEntity {
    //Region start - fix actors in the center of a bearing contraption not triggering correctly (vanilla create bug)
    @Shadow
    protected float angleDelta;

    @Redirect(
        method = "*",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;motion:Lnet/minecraft/world/phys/Vec3;")
    )
    private void redirectPutMotion(
        MovementContext context, Vec3 value,
        @Local Direction facing
    ) {
        Vec3i dir = facing.getNormal();
        double scalar = Math.abs(angleDelta / 360.0) * Math.signum(dir.getX() + dir.getY() + dir.getZ());
        context.motion = new Vec3(Math.abs(dir.getX()), Math.abs(dir.getY()), Math.abs(dir.getZ())).scale(scalar);
    }
    //Region end
}
