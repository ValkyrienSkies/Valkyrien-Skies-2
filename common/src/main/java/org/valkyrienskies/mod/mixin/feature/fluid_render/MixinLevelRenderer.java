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
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.fluid.client.ShipFluidRenderTypes;
import org.valkyrienskies.mod.common.fluid.client.ShipInteriorFogRenderer;
import org.valkyrienskies.mod.common.fluid.client.ShipPocketWorldWaterOccluder;

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
     *
     * <p>Under a shaderpack the depth pre-pass runs first: Iris swaps out the core shaders, so the
     * uniform-mask occlusion never reaches the fragments and the only thing left that every pipeline
     * still respects is the shared depth buffer. Without a shaderpack the mask handles it and the
     * pre-pass would just be redundant geometry.</p>
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

        // The depth pre-pass is not run from here. It has to draw while Iris still has the water phase
        // active, or its caps get the flat gbuffers_basic program and sit at the undisplaced waterline
        // while the surface they are standing in for waves past them. On Sodium that window is inside
        // drawChunkLayer(translucent) -- see MixinSodiumWorldRenderer.
        renderChunkLayer(ShipFluidRenderTypes.AIR_CULL_RENDER_TYPE, poseStack, camX, camY, camZ, projectionMatrix);
    }

    /**
     * Emits the ship fluid overlay while the translucent layer is still set up.
     *
     * <p>Placement is the whole point: a shaderpack resolves the terrain translucent shader to
     * {@code gbuffers_water}, but only when the current rendering phase is not entities or block
     * entities — those resolve to {@code MOVING_BLOCK} instead. Injecting before the layer tears its
     * state down keeps us inside the terrain-translucent phase, so the pack's water vertex shader
     * gets to animate and shade this geometry alongside the world's own surface. Drawing it after
     * the layer, as a standalone pass, would have picked up the wrong program.</p>
     */
//    @Inject(
//        method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
//        at = @At(
//            value = "INVOKE",
//            target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V",
//            shift = At.Shift.BEFORE
//        ),
//        // Required: silently missing this point would leave the overlay drawn with the wrong
//        // program, or not drawn at all, with nothing to indicate why.
//        require = 1
//    )
//    private void vs$renderShipFluidOverlay(final RenderType renderType, final PoseStack poseStack,
//        final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
//        final CallbackInfo ci) {
//        if (renderType != RenderType.translucent()) return;
//        if (this.level == null) return;
//
//        final Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
//        final VertexSorting oldVertexSorting = RenderSystem.getVertexSorting();
//
//        final PoseStack modelViewStack = RenderSystem.getModelViewStack();
//        modelViewStack.pushPose();
//        modelViewStack.setIdentity();
//        modelViewStack.mulPoseMatrix(poseStack.last().pose());
//
//        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
//        RenderSystem.applyModelViewMatrix();
//        try {
//            ShipWaterPocketLiquidOverlay.render(camX, camY, camZ);
//        } finally {
//            modelViewStack.popPose();
//            RenderSystem.applyModelViewMatrix();
//            RenderSystem.setProjectionMatrix(oldProjection, oldVertexSorting);
//        }
//    }

    // The custom interior fog pass is not wired up. Fog is left to the upstream path
    // (fluid_camera_fix's MixinCamera, gated on renderFloodedFluidFog), which is what this branch is
    // meant to sit on top of rather than replace. ShipInteriorFogRenderer and its shaders are still in
    // the tree, unreferenced, if the screen-space version is wanted back.
}
