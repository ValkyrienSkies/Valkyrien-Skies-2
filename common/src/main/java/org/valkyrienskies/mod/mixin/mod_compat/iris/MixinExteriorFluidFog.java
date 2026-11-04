package org.valkyrienskies.mod.mixin.mod_compat.iris;

import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.ExteriorFluidFog;
import org.valkyrienskies.mod.common.fluid.RetainedFluidRenderer;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class MixinExteriorFluidFog {
    @Shadow @Final private RenderTargets renderTargets;
    @Shadow @Final private ImmutableSet<Integer> flippedAfterTranslucent;
    @Unique private GlFramebuffer vs$fogFramebuffer;

    @Inject(method = "beginTranslucents", at = @At("HEAD"))
    private void vs$prepareComplementaryFog(final CallbackInfo ci) {
        if (org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFluidCompat.active()) {
            RetainedFluidRenderer.renderOpaqueShaderFrame();
            org.valkyrienskies.mod.common.fluid.FluidParcelRenderer.renderOpaqueShaderFrame();
        }
        org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFog.prepare();
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void vs$clearComplementaryFog(final CallbackInfo ci) {
        org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFog.clear();
    }

    @Inject(method = "beginTranslucents", at = @At("RETURN"))
    private void vs$fogBeforeTranslucents(final CallbackInfo ci) {
        final var color = renderTargets.get(0);
        if (color == null) return;
        final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            if (vs$fogFramebuffer == null) {
                vs$fogFramebuffer = renderTargets.createGbufferFramebuffer(flippedAfterTranslucent, new int[]{0});
            }
            if (!org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFluidCompat.active())
                ExteriorFluidFog.renderToShaderTarget(vs$fogFramebuffer.getId(), color.getWidth(), color.getHeight());
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, vs$fogFramebuffer.getId());
            RetainedFluidRenderer.renderShaderFrame();
            org.valkyrienskies.mod.common.fluid.FluidParcelRenderer.renderShaderFrame();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }
}
