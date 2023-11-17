package org.valkyrienskies.mod.fabric.mixin.client.render;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixin.accessors.client.render.OverlayVertexConsumerAccessor;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Shadow
    private ClientLevel level;

    /**
     * This mixin makes block damage render on ships.
     */
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void renderBlockDamage(final BlockRenderDispatcher blockRenderManager, final BlockState state,
        final BlockPos blockPos, final BlockAndTintGetter blockRenderWorld, final PoseStack matrix,
        final VertexConsumer vertexConsumer, final Operation<Void> renderBreakingTexture, final PoseStack matrixStack,
        final float methodTickDelta, final long methodLimitTime, final boolean methodRenderBlockOutline,
        final Camera methodCamera, final GameRenderer methodGameRenderer,
        final LightTexture methodLightmapTextureManager, final Matrix4f methodMatrix4f) {

        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship != null) {
            // Remove the vanilla render transform
            matrixStack.popPose();

            // Add the VS render transform
            matrixStack.pushPose();

            final ShipTransform renderTransform = ship.getRenderTransform();
            final Vec3 cameraPos = methodCamera.getPosition();

            transformRenderWithShip(renderTransform, matrixStack, blockPos, cameraPos.x, cameraPos.y, cameraPos.z);

            final Matrix3f newNormalMatrix = new Matrix3f(matrixStack.last().normal());
            final Matrix4f newModelMatrix = new Matrix4f(matrixStack.last().pose());

            // Then update the matrices in vertexConsumer (I'm guessing vertexConsumer is responsible for mapping
            // textures, so we need to update its matrices otherwise the block damage texture looks wrong)
            final SheetedDecalTextureGenerator newVertexConsumer =
                new SheetedDecalTextureGenerator(((OverlayVertexConsumerAccessor) vertexConsumer).getDelegate(),
                    newModelMatrix, newNormalMatrix, 1.0f);

            // Finally, invoke the render damage function.
            renderBreakingTexture.call(blockRenderManager, state, blockPos, blockRenderWorld, matrix,
                newVertexConsumer);
        } else {
            // Vanilla behavior
            renderBreakingTexture.call(blockRenderManager, state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        }
    }
}
