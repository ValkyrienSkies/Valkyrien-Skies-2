package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import me.jellysquid.mods.sodium.client.gl.GlObject;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer;

@Mixin(value = GlProgram.class, remap = false)
public abstract class MixinFluidGlProgram extends GlObject {
    @Inject(method = "delete", at = @At("HEAD"))
    private void vs$releaseFluidProgram(final CallbackInfo ci) {
        FluidOcclusionRenderer.onProgramClosed(handle());
    }
}
