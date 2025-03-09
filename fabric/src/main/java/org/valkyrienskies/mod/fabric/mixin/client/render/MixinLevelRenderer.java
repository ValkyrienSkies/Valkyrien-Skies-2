package org.valkyrienskies.mod.fabric.mixin.client.render;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Shadow
    private ClientLevel level;

    /**
     * This mixin makes block damage render on ships.
     */
    @WrapOperation(
        method = "renderLevel",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"
        )
    )
    private void renderBlockDamage(BlockRenderDispatcher blockRenderManager, BlockState state, BlockPos blockPos,
        BlockAndTintGetter blockAndTintGetter, PoseStack poseStack, VertexConsumer vertexConsumer,
        Operation<Void> renderBreakingTexture, DeltaTracker deltaTracker, boolean bl, Camera camera
    ) {

        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship != null) {
            // Remove the vanilla render transform
            poseStack.popPose();

            // Add the VS render transform
            poseStack.pushPose();

            final ShipTransform renderTransform = ship.getRenderTransform();
            final Vec3 cameraPos = camera.getPosition();

            transformRenderWithShip(renderTransform, poseStack, blockPos, cameraPos.x, cameraPos.y, cameraPos.z);

            // Then update the matrices in vertexConsumer (I'm guessing vertexConsumer is responsible for mapping
            // textures, so we need to update its matrices otherwise the block damage texture looks wrong)
            final SheetedDecalTextureGenerator newVertexConsumer =
                new SheetedDecalTextureGenerator(((SheetedDecalTextureGeneratorAccessor) ((SheetedDecalTextureGenerator) vertexConsumer)).getDelegate(), poseStack.last(), 1.0F);

            // Finally, invoke the render damage function.
            renderBreakingTexture.call(blockRenderManager, state, blockPos, blockAndTintGetter, poseStack, newVertexConsumer);
        } else {
            // Vanilla behavior
            renderBreakingTexture.call(blockRenderManager, state, blockPos, blockAndTintGetter, poseStack, vertexConsumer);
        }
    }
}
