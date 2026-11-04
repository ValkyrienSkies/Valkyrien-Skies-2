package org.valkyrienskies.mod.mixin.mod_compat.iris.complementary;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFluidCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFog;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.Program", remap = false)
public abstract class MixinComplementaryProgram {
    @Inject(method = "use", at = @At("RETURN"))
    private void vs$complementaryFog(final CallbackInfo ci) {
        if (ComplementaryFluidCompat.active()) ComplementaryFog.bind(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }
}
