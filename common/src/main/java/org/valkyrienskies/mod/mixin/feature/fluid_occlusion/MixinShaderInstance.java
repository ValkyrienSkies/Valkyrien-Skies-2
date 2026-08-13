package org.valkyrienskies.mod.mixin.feature.fluid_occlusion;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderContext;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance {
    @Shadow
    private int programId;

    @Unique
    private boolean vs$fluidOcclusionChecked;
    @Unique
    private boolean vs$fluidOcclusionSupported;

    @Inject(method = "apply()V", at = @At("TAIL"), require = 0)
    private void vs$applyFluidOcclusion(final CallbackInfo ci) {
        if (!vs$fluidOcclusionChecked) {
            vs$fluidOcclusionChecked = true;
            vs$fluidOcclusionSupported =
                FluidOcclusionRenderer.supportsProgram(programId);
        }
        if (!vs$fluidOcclusionSupported) return;

        final ClientLevel level = FluidOcclusionRenderContext.getLevel();
        if (FluidOcclusionRenderContext.isActive() && level != null) {
            FluidOcclusionRenderer.setupForProgram(
                programId,
                level,
                FluidOcclusionRenderContext.getCameraX(),
                FluidOcclusionRenderContext.getCameraY(),
                FluidOcclusionRenderContext.getCameraZ()
            );
        } else {
            FluidOcclusionRenderer.disableForProgram(programId);
        }
    }
}
