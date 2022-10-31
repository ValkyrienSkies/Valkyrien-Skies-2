package org.valkyrienskies.mod.mixin.client.renderer;

import static org.valkyrienskies.mod.client.McClientMathUtilKt.transformRenderWithShip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.render.OverlayVertexConsumerAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixin.mod_compat.optifine.RenderChunkInfoAccessorOptifine;
import org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer.RenderChunkInfoAccessor;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;

/**
 * This mixin allows {@link LevelRenderer} to render ship chunks.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Shadow
    @Final
    private ObjectList<LevelRenderer.RenderChunkInfo> renderChunks;
    @Shadow
    private ClientLevel level;
    @Shadow
    private ViewArea viewArea;

    @Shadow
    private static void renderShape(final PoseStack matrixStack, final VertexConsumer vertexConsumer,
        final VoxelShape voxelShape, final double d, final double e, final double f, final float red, final float green,
        final float blue, final float alpha) {
        throw new AssertionError();
    }

    /**
     * This mixin tells the {@link LevelRenderer} to render ship chunks.
     */
    @Inject(
        method = "setupRender",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectList;iterator()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"
        )
    )
    private void addShipVisibleChunks(
        final Camera camera, final Frustum frustum, final boolean hasForcedFrustum, final int frame,
        final boolean spectator, final CallbackInfo ci) {

        final LevelRenderer self = LevelRenderer.class.cast(this);
        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
        final ViewAreaAccessor chunkStorageAccessor = (ViewAreaAccessor) viewArea;
        for (final ShipObjectClient shipObject : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            // Don't bother rendering the ship if its AABB isn't visible to the frustum
            if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(shipObject.getRenderAABB()))) {
                continue;
            }

            shipObject.getShipData().getShipActiveChunksSet().iterateChunkPos((x, z) -> {
                for (int y = 0; y < 16; y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final ChunkRenderDispatcher.RenderChunk renderChunk =
                        chunkStorageAccessor.callGetRenderChunkAt(tempPos);
                    if (renderChunk != null) {
                        final LevelRenderer.RenderChunkInfo newChunkInfo;
                        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.OPTIFINE) {
                            newChunkInfo =
                                RenderChunkInfoAccessorOptifine.vs$new(renderChunk, null, 0);
                        } else {
                            newChunkInfo =
                                RenderChunkInfoAccessor.vs$new(self, renderChunk, null, 0);
                        }
                        renderChunks.add(newChunkInfo);
                    }
                }
                return null;
            });
        }
    }

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
            for (int y = 0; y < 16; y++) {
                viewArea.setDirty(ChunkPos.getX(pos), y, ChunkPos.getZ(pos), false);
            }
        });
    }

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

    /**
     * @reason mojank developers who wrote this don't quite understand what a matrixstack is apparently
     * @author Rubydesic
     */
    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void preRenderHitOutline(final PoseStack matrixStack, final VertexConsumer vertexConsumer,
        final Entity entity, final double camX, final double camY, final double camZ, final BlockPos blockPos,
        final BlockState blockState, final CallbackInfo ci) {
        ci.cancel();
        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship != null) {
            matrixStack.pushPose();
            transformRenderWithShip(ship.getRenderTransform(), matrixStack, blockPos, camX, camY, camZ);
            renderShape(matrixStack, vertexConsumer,
                blockState.getShape(this.level, blockPos, CollisionContext.of(entity)),
                0d, 0d, 0d, 0.0F, 0.0F, 0.0F, 0.4F);
            matrixStack.popPose();
        } else {
            // vanilla
            renderShape(matrixStack, vertexConsumer,
                blockState.getShape(this.level, blockPos, CollisionContext.of(entity)),
                (double) blockPos.getX() - camX,
                (double) blockPos.getY() - camY,
                (double) blockPos.getZ() - camZ,
                0.0F, 0.0F, 0.0F, 0.4F);
        }
    }

    /**
     * This mixin makes block damage render on ships.
     */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void renderBlockDamage(final BlockRenderDispatcher blockRenderManager, final BlockState state,
        final BlockPos blockPos,
        final BlockAndTintGetter blockRenderWorld, final PoseStack matrix, final VertexConsumer vertexConsumer,
        final PoseStack matrixStack, final float methodTickDelta, final long methodLimitTime,
        final boolean methodRenderBlockOutline,
        final Camera methodCamera, final GameRenderer methodGameRenderer,
        final LightTexture methodLightmapTextureManager,
        final Matrix4f methodMatrix4f) {

        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship != null) {
            // Remove the vanilla render transform
            matrixStack.popPose();

            // Add the VS render transform
            matrixStack.pushPose();

            final ShipTransform renderTransform = ship.getRenderTransform();
            final Vec3 cameraPos = methodCamera.getPosition();

            transformRenderWithShip(renderTransform, matrixStack, blockPos, cameraPos.x, cameraPos.y, cameraPos.z);

            // Then update the matrices in vertexConsumer (I'm guessing vertexConsumer is responsible for mapping
            // textures, so we need to update its matrices otherwise the block damage texture looks wrong)
            final OverlayVertexConsumerAccessor vertexConsumerAccessor = (OverlayVertexConsumerAccessor) vertexConsumer;

            final Matrix3f newNormalMatrix = matrixStack.last().normal().copy();
            newNormalMatrix.invert();

            final Matrix4f newModelMatrix = matrixStack.last().pose().copy();
            // newModelMatrix.invert(); // DISABLED because Matrix4f.invert() doesn't work! Mojang code SMH >.<
            final Matrix4d newModelMatrixAsJoml = VectorConversionsMCKt.toJOML(newModelMatrix);
            newModelMatrixAsJoml.invert();
            VectorConversionsMCKt.set(newModelMatrix, newModelMatrixAsJoml);

            vertexConsumerAccessor.setNormalMatrix(newNormalMatrix);
            vertexConsumerAccessor.setTextureMatrix(newModelMatrix);

            // Finally, invoke the render damage function.
            blockRenderManager.renderBreakingTexture(state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        } else {
            // Vanilla behavior
            blockRenderManager.renderBreakingTexture(state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        }
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
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(level, blockEntityPos);
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
