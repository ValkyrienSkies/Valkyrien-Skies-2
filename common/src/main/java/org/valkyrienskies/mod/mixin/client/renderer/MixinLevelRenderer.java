package org.valkyrienskies.mod.mixin.client.renderer;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.client.IVSCamera;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Shadow
    @Final
    private SectionOcclusionGraph sectionOcclusionGraph;

    @Unique
    private ShipTransform valkyrienskies$prevShipMountedToTransform = null;

    /**
     * @reason This mixin forces the game to always render block damage.
     */
    @ModifyConstant(
        method = "renderLevel",
        constant = @Constant(
            doubleValue = 1024,
            ordinal = 0
        ))
    private double disableBlockDamageDistanceCheck(final double originalBlockDamageDistanceConstant) {
        return Double.MAX_VALUE;
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void preRenderLevel(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer,
        LightTexture lightTexture, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        final ShipTransform shipMountedRenderTransform = ((IVSCamera) camera).getShipMountedRenderTransform();
        if (valkyrienskies$prevShipMountedToTransform != shipMountedRenderTransform) {
            if (valkyrienskies$prevShipMountedToTransform != null && shipMountedRenderTransform != null) {
                // Compute the angle between rotations
                double rotDot = Math.abs(valkyrienskies$prevShipMountedToTransform.getShipToWorldRotation().dot(shipMountedRenderTransform.getShipToWorldRotation()));
                rotDot = Math.min(rotDot, 1.0);
                double angle = 2.0 * Math.acos(rotDot);
                if (Math.toDegrees(angle) > 1.0) {
                    valkyrienskies$prevShipMountedToTransform = shipMountedRenderTransform;
                    sectionOcclusionGraph.invalidate();
                }
            } else {
                valkyrienskies$prevShipMountedToTransform = shipMountedRenderTransform;
                sectionOcclusionGraph.invalidate();
            }
        }
    }

    /**
     * This mixin makes block damage render on ships.
     */
    /*
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void renderBlockDamage(final BlockRenderDispatcher blockRenderManager, final BlockState state,
        final BlockPos blockPos, final BlockAndTintGetter blockRenderWorld, final PoseStack matrix,
        final VertexConsumer vertexConsumer, final Operation<Void> renderBreakingTexture) {


        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship != null) {
            // Remove the vanilla render transform
            matrixStack.popPose();

            // Add the VS render transform
            matrixStack.pushPose();

            final ShipTransform renderTransform = ship.getRenderTransform();
            final Vec3 cameraPos = methodCamera.getPosition();

            transformRenderWithShip(renderTransform, matrixStack, blockPos, cameraPos.x, cameraPos.y, cameraPos.z);

            final Matrix3f newNormalMatrix = matrixStack.last().normal().copy();
            final Matrix4f newModelMatrix = matrixStack.last().pose().copy();

            // Then update the matrices in vertexConsumer (I'm guessing vertexConsumer is responsible for mapping
            // textures, so we need to update its matrices otherwise the block damage texture looks wrong)
            final SheetedDecalTextureGenerator newVertexConsumer =
                new SheetedDecalTextureGenerator(((OverlayVertexConsumerAccessor) vertexConsumer).getDelegate(),
                    newModelMatrix, newNormalMatrix);

            // Finally, invoke the render damage function.
            renderBreakingTexture.call(blockRenderManager, state, blockPos, blockRenderWorld, matrix,
                newVertexConsumer);
        } else {
            // Vanilla behavior
            renderBreakingTexture.call(blockRenderManager, state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        }
    }

     */

}
