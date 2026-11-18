package org.valkyrienskies.mod.mixin.feature.fluid_occlusion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderContext;

@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class MixinLevelRenderer {
    @Shadow
    private @Nullable ClientLevel level;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vs$beginExteriorFog(final PoseStack poseStack, final float partialTick, final long finishTime,
        final boolean blockOutline, final net.minecraft.client.Camera camera,
        final net.minecraft.client.renderer.GameRenderer renderer,
        final net.minecraft.client.renderer.LightTexture lightTexture, final Matrix4f projection, final CallbackInfo ci) {
        org.valkyrienskies.mod.common.fluid.ExteriorFluidFog.beginFrame(projection, poseStack.last().pose());
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void vs$dirtyExteriorFluid(final int x, final int y, final int z, final boolean immediate, final CallbackInfo ci) {
        org.valkyrienskies.mod.common.fluid.ExteriorFluidFog.dirtySection(x, y, z);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderType;translucent()Lnet/minecraft/client/renderer/RenderType;"))
    private void vs$exteriorFogBeforeTranslucency(final CallbackInfo ci) {
        org.valkyrienskies.mod.common.fluid.ExteriorFluidFog.beforeTranslucency();
    }

    @Inject(
        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
        at = @At("HEAD"),
        require = 0
    )
    private void vs$beginFluidOcclusionLayer(
        final RenderType renderType,
        final PoseStack poseStack,
        final double cameraX,
        final double cameraY,
        final double cameraZ,
        final Matrix4f projection,
        final CallbackInfo ci
    ) {
        if (level != null) {
            FluidOcclusionRenderContext.begin(
                level, renderType, cameraX, cameraY, cameraZ
            );
        }
    }

    @Inject(
        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void vs$endFluidOcclusionLayer(
        final RenderType renderType,
        final PoseStack poseStack,
        final double cameraX,
        final double cameraY,
        final double cameraZ,
        final Matrix4f projection,
        final CallbackInfo ci
    ) {
        FluidOcclusionRenderContext.end(renderType);
    }
}
