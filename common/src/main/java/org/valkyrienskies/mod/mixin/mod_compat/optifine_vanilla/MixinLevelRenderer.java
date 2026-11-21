package org.valkyrienskies.mod.mixin.mod_compat.optifine_vanilla;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.render.NativeShipPieces;
import org.valkyrienskies.mod.common.render.batched.ShipPieceBuffers;
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
        // This can happen when immersive portals is installed
        if (!(this.level.getChunkSource() instanceof final ClientChunkCacheDuck chunks)) return;

        chunks.vs$getShipChunks().forEach((pos, chunk) -> {
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                viewArea.setDirty(ChunkPos.getX(pos), y, ChunkPos.getZ(pos), false);
            }
        });
    }

    /**
     * This mixin makes {@link BlockEntity} in the ship render in the correct place.
     */
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void renderShipChunkBlockEntity(final BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        final BlockEntity blockEntity, final float tickDelta, final PoseStack matrix,
        final MultiBufferSource vertexConsumerProvider, final Operation<Void> original,
        @Local(argsOnly = true) final Camera methodCamera) {

        final BlockPos blockEntityPos = blockEntity.getBlockPos();
        final ClientShip shipObject = VSGameUtilsKt.getLoadedShipManagingPos(level, blockEntityPos);
        if (shipObject != null) {
            final Vec3 cam = methodCamera.getPosition();
            matrix.popPose();
            matrix.pushPose();
            final var pieces = NativeShipPieces.pieces(level, shipObject.getId());
            if (!pieces.isEmpty()) {
                if (ShipRenderViews.shouldRenderShip(shipObject)) {
                    ShipPieceBuffers.draw(pieces, blockEntityPos, matrix, cam, vertexConsumerProvider,
                        (pose, buffers) -> original.call(blockEntityRenderDispatcher, blockEntity, tickDelta, pose, buffers));
                }
                return;
            }
            transformRenderWithShip(shipObject.getRenderTransform(), matrix, blockEntityPos,
                cam.x(), cam.y(), cam.z());
        }
        original.call(blockEntityRenderDispatcher, blockEntity, tickDelta, matrix, vertexConsumerProvider);
    }
}
