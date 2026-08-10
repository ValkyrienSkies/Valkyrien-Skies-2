package org.valkyrienskies.mod.mixin.feature.fluid_render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.fluid.client.ShipFluidRenderTypes;
import org.valkyrienskies.mod.common.fluid.client.ShipInteriorFogRenderer;

@Mixin(value = LevelRenderer.class, priority = 900)
public abstract class MixinLevelRenderer {

    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    protected abstract void renderChunkLayer(RenderType arg, PoseStack arg2, double d, double e, double f,
        Matrix4f matrix4f);

    /**
     * Draws the culled world-fluid layer just before vanilla water would have gone out.
     *
     * <p>Tripwire is the hook because it is the last chunk layer vanilla renders, so by the time it
     * runs everything the cull needs to depth-test against is already in the buffer.</p>
     */
    @Inject(
        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
        at = @At("HEAD"),
        require = 1
    )
    private void vs$renderCulledFluidLayer(final RenderType renderType, final PoseStack poseStack,
        final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final CallbackInfo ci) {
        if (!VSGameConfig.CLIENT.getUnderwater().getEnableWaterCulling()) return;
        if (renderType != RenderType.tripwire()) return;
        if (this.level == null) return;

        renderChunkLayer(ShipFluidRenderTypes.AIR_CULL_RENDER_TYPE, poseStack, camX, camY, camZ, projectionMatrix);
    }

    @Inject(
        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
        at = @At("TAIL"),
        require = 0
    )
    private void vs$renderInteriorFog(final RenderType renderType, final PoseStack poseStack,
        final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final CallbackInfo ci) {
        if (!VSGameConfig.CLIENT.getUnderwater().getEnableCustomFluidFog()) return;
        if (renderType != RenderType.tripwire()) return;
        final Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (this.level == null || camera == null) return;

        final Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        final VertexSorting oldVertexSorting = RenderSystem.getVertexSorting();

        final PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        modelViewStack.mulPoseMatrix(poseStack.last().pose());

        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.applyModelViewMatrix();
        try {
            ShipInteriorFogRenderer.render(camera, projectionMatrix, poseStack.last().pose());
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection, oldVertexSorting);
        }
    }
}
