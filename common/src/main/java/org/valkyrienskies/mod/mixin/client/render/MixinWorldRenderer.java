package org.valkyrienskies.mod.mixin.client.render;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

/**
 * This mixin allows {@link WorldRenderer} to render ship chunks.
 */
@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Shadow
    @Final
    private ObjectList<WorldRenderer.ChunkInfo> visibleChunks;
    @Shadow
    private ClientWorld world;
    @Shadow
    private BuiltChunkStorage chunks;

    /**
     * This mixin tells the {@link WorldRenderer} to render ship chunks.
     */
    @Inject(method = "setupTerrain", at = @At(
        value = "INVOKE",
        target = "Lit/unimi/dsi/fastutil/objects/ObjectList;iterator()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"
    ))
    private void addShipVisibleChunks(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator, CallbackInfo ci) {
        final WorldRenderer self = WorldRenderer.class.cast(this);
        final BlockPos.Mutable tempPos = new BlockPos.Mutable();
        for (ShipObject shipObject : VSGameUtilsKt.getShipObjectWorld(world).getUuidToShipObjectMap().values()) {
            shipObject.getShipData().getShipActiveChunksSet().iterateChunkPos((x, z) -> {
                for (int y = 0; y < 16; y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final ChunkBuilder.BuiltChunk renderChunk = chunks.getRenderedChunk(tempPos);
                    if (renderChunk != null) {
                        final WorldRenderer.ChunkInfo newChunkInfo = MixinWorldRendererChunkInfo.invoker$new(self, renderChunk, null, 0);
                        visibleChunks.add(newChunkInfo);
                    }
                }
                return null;
            });
        }
    }

    /**
     * TODO: This doesn't work
     */
    @Inject(
        method = "render",
        locals = LocalCapture.CAPTURE_FAILHARD,
        at = @At(
            value = "NEW",
            target = "net/minecraft/client/render/OverlayVertexConsumer",
            ordinal = 0
        )
    )
    public void renderBlockDamage(
        MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera,
        GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f,
        CallbackInfo ci, Profiler profiler, Vec3d vec3d, double d, double e, double f, Matrix4f matrix4f2,
        boolean bl, Frustum frustum2, boolean bl3, VertexConsumerProvider.Immediate immediate, ObjectListIterator var39,
        WorldRenderer.ChunkInfo chunkInfo, List list, Iterator var42, BlockEntity blockEntity, BlockPos blockPos,
        VertexConsumerProvider vertexConsumerProvider3, SortedSet sortedSet, int w, MatrixStack.Entry entry
    ) {
        final ShipObject ship = VSGameUtils.getShipObjectManagingPos(world, blockPos);
        if (ship != null) {
            VectorConversionsMCKt.multiply(matrices, ship.getRenderTransform().getShipToWorldMatrix());
        }
    }


    /**
     * This mixin tells the game where to render chunks; which allows us to render ship chunks in arbitrary positions.
     */
    @Inject(method = "renderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/VertexBuffer;bind()V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void renderShipChunk(RenderLayer renderLayer, MatrixStack matrixStack, double playerCameraX, double playerCameraY, double playerCameraZ, CallbackInfo ci,
                                 boolean bl, ObjectListIterator<?> objectListIterator, WorldRenderer.ChunkInfo chunkInfo2, ChunkBuilder.BuiltChunk builtChunk, VertexBuffer vertexBuffer) {
        final BlockPos renderChunkOrigin = builtChunk.getOrigin();
        final ShipObject getShipObjectManagingPos = VSGameUtils.getShipObjectManagingPos(world, renderChunkOrigin);
        if (getShipObjectManagingPos != null) {
            // This render chunk is a ship chunk, give it the ship chunk render transform
            final ShipTransform renderTransform = getShipObjectManagingPos.getRenderTransform();
            final Matrix4dc shipToWorldMatrix = renderTransform.getShipToWorldMatrix();

            // Create the render matrix from the render transform and player position
            final Matrix4d renderMatrix = new Matrix4d();
            renderMatrix.translate(-playerCameraX, -playerCameraY, -playerCameraZ);
            renderMatrix.mul(shipToWorldMatrix);
            renderMatrix.translate(renderChunkOrigin.getX(), renderChunkOrigin.getY(), renderChunkOrigin.getZ());

            // Update the model transform matrix to include the transformation described by [renderMatrix]
            final Matrix4f renderMatrixAsMinecraft = VectorConversionsMCKt.set(new Matrix4f(), renderMatrix);
            matrixStack.peek().getModel().multiply(renderMatrixAsMinecraft);

            // Update the model normal matrix to include the rotation described by [renderTransform]
            final Quaterniondc shipTransformRotation = renderTransform.getShipCoordinatesToWorldCoordinatesRotation();
            final Quaternion shipTransformRotationMinecraft = new Quaternion((float) shipTransformRotation.x(), (float) shipTransformRotation.y(), (float) shipTransformRotation.z(), (float) shipTransformRotation.w());
            matrixStack.peek().getNormal().multiply(shipTransformRotationMinecraft);
        } else {
            // Restore MC default behavior (that was removed by cancelDefaultTransform())
            matrixStack.translate(renderChunkOrigin.getX() - playerCameraX, renderChunkOrigin.getY() - playerCameraY, renderChunkOrigin.getZ() - playerCameraZ);
        }
    }

    /**
     * This mixin removes the vanilla code that determines where each chunk renders.
     */
    @Redirect(method = "renderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V"))
    private void cancelDefaultTransform(MatrixStack matrixStack, double x, double y, double z) {
        // Do nothing
    }

    /**
     * This mixin makes {@link BlockEntity} in the ship render in the correct place.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"))
    private void renderShipChunkBlockEntity(BlockEntityRenderDispatcher blockEntityRenderDispatcher, BlockEntity blockEntity, float tickDelta, MatrixStack matrix, VertexConsumerProvider vertexConsumerProvider,
                                            MatrixStack methodMatrices, float methodTickDelta, long methodLimitTime, boolean methodRenderBlockOutline, Camera methodCamera, GameRenderer methodGameRenderer, LightmapTextureManager methodLightmapTextureManager, Matrix4f methodMatrix4f) {
        final BlockPos blockEntityPos = blockEntity.getPos();
        final ShipObject getShipObjectManagingPos = VSGameUtils.getShipObjectManagingPos(world, blockEntityPos);
        if (getShipObjectManagingPos != null) {
            // Remove the vanilla render transform
            matrix.pop();

            // Add the VS render transform
            matrix.push();

            final ShipTransform renderTransform = getShipObjectManagingPos.getRenderTransform();
            final Matrix4dc shipToWorldMatrix = renderTransform.getShipToWorldMatrix();

            // Create the render matrix from the render transform and player position
            final Matrix4d renderMatrix = new Matrix4d();
            final Vec3d cameraPos = methodCamera.getPos();
            renderMatrix.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
            renderMatrix.mul(shipToWorldMatrix);
            renderMatrix.translate(blockEntityPos.getX(), blockEntityPos.getY(), blockEntityPos.getZ());

            // Update the model transform matrix to include the transformation described by [renderMatrix]
            final Matrix4f renderMatrixAsMinecraft = VectorConversionsMCKt.set(new Matrix4f(), renderMatrix);
            matrix.peek().getModel().multiply(renderMatrixAsMinecraft);
        }
        blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrix, vertexConsumerProvider);
    }
}
