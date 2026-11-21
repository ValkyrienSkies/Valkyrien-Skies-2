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
import org.valkyrienskies.mod.common.fluid.FluidOcclusionTextureState;

@Mixin(ShaderInstance.class)
public abstract class MixinShaderInstance {
    @Shadow
    private int programId;

    @Unique
    private boolean vs$fluidOcclusionChecked;
    @Unique
    private boolean vs$fluidOcclusionSupported;

    @Unique
    private FluidOcclusionTextureState vs$fluidTextures;

    @Unique
    private void vs$restoreFluidTextures() {
        if (vs$fluidTextures == null) return;
        vs$fluidTextures.close();
        vs$fluidTextures = null;
    }

    @Inject(method = "clear()V", at = @At("HEAD"))
    private void vs$clearFluidTextures(final CallbackInfo ci) {
        vs$restoreFluidTextures();
    }

    @Inject(method = "close()V", at = @At("HEAD"))
    private void vs$closeFluidOcclusion(final CallbackInfo ci) {
        vs$restoreFluidTextures();
        FluidOcclusionRenderer.onProgramClosed(programId);
        vs$fluidOcclusionChecked = false;
        vs$fluidOcclusionSupported = false;
    }

    @Inject(method = "apply()V", at = @At("TAIL"), require = 0)
    private void vs$applyFluidOcclusion(final CallbackInfo ci) {
        if (!vs$fluidOcclusionChecked) {
            vs$fluidOcclusionChecked = true;
            vs$fluidOcclusionSupported =
                FluidOcclusionRenderer.supportsProgram(programId);
        }
        if (!vs$fluidOcclusionSupported) return;

        vs$restoreFluidTextures();
        final ClientLevel level = FluidOcclusionRenderContext.getLevel();
        if (FluidOcclusionRenderContext.isActive() && level != null) {
            vs$fluidTextures = new FluidOcclusionTextureState();
            try {
                FluidOcclusionRenderer.setupForProgram(
                    programId,
                    level,
                    FluidOcclusionRenderContext.getCameraX(),
                    FluidOcclusionRenderContext.getCameraY(),
                    FluidOcclusionRenderContext.getCameraZ()
                );
            } catch (final RuntimeException exception) {
                vs$restoreFluidTextures();
                throw exception;
            }
        } else {
            FluidOcclusionRenderer.disableForProgram(programId);
        }
    }
}
