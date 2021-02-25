package org.valkyrienskies.mod.common.mixin.client.render;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Quaternion;
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
import org.valkyrienskies.mod.common.MixinInterfaces;
import org.valkyrienskies.core.game.ShipObject;
import org.valkyrienskies.core.game.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtils;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Shadow
    @Final
    private ObjectList<WorldRenderer.ChunkInfo> visibleChunks;
    @Shadow
    private ClientWorld world;
    @Shadow
    private BuiltChunkStorage chunks;

    private final WorldRenderer vs$thisAsWorldRenderer = WorldRenderer.class.cast(this);

    @Inject(method = "setupTerrain", at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectList;iterator()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"
    ))
    private void addShipVisibleChunks(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator, CallbackInfo ci) {
        final BlockPos.Mutable tempPos = new BlockPos.Mutable();
        for (ShipObject shipObject : VSGameUtils.INSTANCE.getShipObjectWorldFromWorld(world).getUuidToShipObjectMap().values()) {
            shipObject.getShipData().getShipActiveChunksSet().iterateChunkPos((x, z) -> {
                for (int y = 0; y < 16; y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final ChunkBuilder.BuiltChunk renderChunk = chunks.getRenderedChunk(tempPos);
                    if (renderChunk != null) {
                        final WorldRenderer.ChunkInfo newChunkInfo = MixinWorldRendererChunkInfo.invoker$new(vs$thisAsWorldRenderer, renderChunk, null, 0);
                        visibleChunks.add(newChunkInfo);
                    }
                }
                return null;
            });
        }
    }

    /**
     * This mixin tells the game where to render ship chunks.
     */
    @Inject(method = "renderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/VertexBuffer;bind()V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void renderShipChunk(RenderLayer renderLayer, MatrixStack matrixStack, double playerCameraX, double playerCameraY, double playerCameraZ, CallbackInfo ci,
                                 boolean bl, ObjectListIterator objectListIterator, WorldRenderer.ChunkInfo chunkInfo2, ChunkBuilder.BuiltChunk builtChunk, VertexBuffer vertexBuffer) {
        final BlockPos renderChunkOrigin = builtChunk.getOrigin();
        final ShipObject getShipObjectManagingPos = VSGameUtils.INSTANCE.getShipObjectManagingPos(world, renderChunkOrigin);
        if (getShipObjectManagingPos != null) {
            final ShipTransform renderTransform = getShipObjectManagingPos.getRenderTransform();

            final Matrix4dc shipToWorldMatrix = renderTransform.getShipToWorldMatrix();

            final Matrix4d renderMatrix = new Matrix4d();
            renderMatrix.translate(-playerCameraX, -playerCameraY, -playerCameraZ);
            renderMatrix.mul(shipToWorldMatrix);
            renderMatrix.translate(renderChunkOrigin.getX(), renderChunkOrigin.getY(), renderChunkOrigin.getZ());

            // Update the model transform matrix
            final Matrix4f renderMatrixAsMinecraft = new Matrix4f();
            MixinInterfaces.ISetMatrix4fFromJOML.class.cast(renderMatrixAsMinecraft).vs$setFromJOML(renderMatrix);
            matrixStack.peek().getModel().multiply(renderMatrixAsMinecraft);

            // Update the model normal matrix
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
}
