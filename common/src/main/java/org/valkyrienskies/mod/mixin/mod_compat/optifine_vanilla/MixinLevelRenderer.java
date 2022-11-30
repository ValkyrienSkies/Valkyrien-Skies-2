package org.valkyrienskies.mod.mixin.mod_compat.optifine_vanilla;

import static org.valkyrienskies.mod.client.McClientMathUtilKt.transformRenderWithShip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;

/**
 * This mixin allows {@link LevelRenderer} to render ship chunks.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Shadow
    private ClientLevel level;
    @Shadow
    private ViewArea viewArea;

    /**
     * Prevents ships from disappearing on f3+a
     */
    @Inject(
        method = "allChanged",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(DD)V"
        )
    )
    private void afterRefresh(final CallbackInfo ci) {
        ((ClientChunkCacheDuck) this.level.getChunkSource()).vs_getShipChunks().forEach((pos, chunk) -> {
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                viewArea.setDirty(ChunkPos.getX(pos), y, ChunkPos.getZ(pos), false);
            }
        });
    }

    /**
     * This mixin makes {@link BlockEntity} in the ship render in the correct place.
     */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void renderShipChunkBlockEntity(final BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        final BlockEntity blockEntity, final float tickDelta, final PoseStack matrix,
        final MultiBufferSource vertexConsumerProvider,
        final PoseStack methodMatrices, final float methodTickDelta, final long methodLimitTime,
        final boolean methodRenderBlockOutline,
        final Camera methodCamera, final GameRenderer methodGameRenderer,
        final LightTexture methodLightmapTextureManager,
        final Matrix4f methodMatrix4f) {

        final BlockPos blockEntityPos = blockEntity.getBlockPos();
        final ClientShip shipObject = VSGameUtilsKt.getShipObjectManagingPos(level, blockEntityPos);
        if (shipObject != null) {
            final Vec3 cam = methodCamera.getPosition();
            matrix.popPose();
            matrix.pushPose();
            transformRenderWithShip(shipObject.getRenderTransform(), matrix, blockEntityPos,
                cam.x(), cam.y(), cam.z());
        }
        blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrix, vertexConsumerProvider);
    }
}
