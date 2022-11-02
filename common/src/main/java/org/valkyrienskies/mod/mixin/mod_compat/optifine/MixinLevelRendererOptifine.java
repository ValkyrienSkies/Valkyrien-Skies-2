package org.valkyrienskies.mod.mixin.mod_compat.optifine;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.optifine.Config;
import net.optifine.render.VboRegion;
import org.joml.Matrix4d;
import org.lwjgl.BufferUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;

@Mixin(LevelRenderer.class)
public class MixinLevelRendererOptifine {
    @Shadow
    private ClientLevel level;

    /**
     * Fix the distance to render chunks, so that Optifine doesn't think ship chunks are too far away
     */
    @Redirect(
        method = "setupRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    private double includeShipChunksInNearChunks(final Vec3 vec1, final Vec3 vec2) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            level, vec1.x(), vec1.y(), vec1.z(), vec2.x(), vec2.y(), vec2.z()
        );
    }

    /**
     * This mixin tells Optifine where to render chunks; which allows us to render ship chunks in arbitrary positions.
     */
    @Inject(method = "renderChunkLayer",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;bind()V"),
        locals = LocalCapture.CAPTURE_FAILHARD)
    @SuppressWarnings("InvalidInjectorMethodSignature")
    private void renderShipChunk(
        final RenderType renderLayer, final PoseStack matrixStack, final double playerCameraX,
        final double playerCameraY, final double playerCameraZ, final CallbackInfo ci,
        final boolean isShaders, final boolean smartAnimations, final boolean flag,
        final ObjectListIterator<?> objectListIterator, final LevelRenderer.RenderChunkInfo chunkInfo2,
        final ChunkRenderDispatcher.RenderChunk builtChunk, final VertexBuffer vertexBuffer, final BlockPos blockpos
    ) {
        final int playerChunkX = ((int) playerCameraX) >> 4;
        final int playerChunkZ = ((int) playerCameraZ) >> 4;
        // Don't apply the ship render transform if the player is in the shipyard
        final boolean isPlayerInShipyard = ChunkAllocator.isChunkInShipyard(playerChunkX, playerChunkZ);

        final BlockPos renderChunkOrigin = builtChunk.getOrigin();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(level, renderChunkOrigin);
        if (!isPlayerInShipyard && shipObject != null) {
            final Matrix4d chunkTransformMatrix = new Matrix4d()
                .translate(-playerCameraX, -playerCameraY, -playerCameraZ)
                .mul(shipObject.getRenderTransform().getShipToWorldMatrix())
                .translate(renderChunkOrigin.getX(), renderChunkOrigin.getY(), renderChunkOrigin.getZ());
            final FloatBuffer chunkTransformMatrixAsFb = chunkTransformMatrix.get(BufferUtils.createFloatBuffer(16));
            GlStateManager._multMatrix(chunkTransformMatrixAsFb);
        } else {
            // Restore Optifine default behavior (that was removed by cancelDefaultTransform())
            GlStateManager._translated(
                renderChunkOrigin.getX() - playerCameraX,
                renderChunkOrigin.getY() - playerCameraY,
                renderChunkOrigin.getZ() - playerCameraZ
            );
        }
    }

    /**
     * This mixin removes the Optifine code that determines where each chunk renders.
     */
    @Redirect(
        method = "renderChunkLayer",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_translated(DDD)V")
    )
    private void cancelDefaultTransformInRenderShipChunk(final double x, final double y, final double z) {
        // Do nothing
    }

    // region Fix render regions setting
    /**
     * This mixin tells Optifine where to render chunks; which allows us to render ship chunks in arbitrary positions.
     */
    @Inject(
        method = "drawRegion",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_translated(DDD)V")
    )
    private void applyShipChunkTransformForRenderRegions(
        final int regionX, final int regionY, final int regionZ, final double xIn, final double yIn, final double zIn,
        final VboRegion vboRegion, final CallbackInfo ci
    ) {
        final ShipObjectClient shipObject =
            VSGameUtilsKt.getShipObjectManagingPos(level, new BlockPos(regionX, regionY, regionZ));
        if (shipObject != null) {
            final Matrix4d chunkTransformMatrix = new Matrix4d()
                .translate(-xIn, -yIn, -zIn)
                .mul(shipObject.getRenderTransform().getShipToWorldMatrix())
                .translate(regionX, regionY, regionZ);
            final FloatBuffer chunkTransformMatrixAsFb = chunkTransformMatrix.get(BufferUtils.createFloatBuffer(16));
            GlStateManager._multMatrix(chunkTransformMatrixAsFb);
        } else {
            GlStateManager._translated(
                regionX - xIn,
                regionY - yIn,
                regionZ - zIn
            );
        }
    }

    /**
     * This mixin removes the Optifine code that determines where each chunk renders.
     */
    @Redirect(
        method = "drawRegion",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_translated(DDD)V"),
        remap = false
    )
    private void cancelDefaultTransformInDrawRegion(final double x, final double y, final double z) {
        // Do nothing
    }
    // endregion

    @Shadow
    private ViewArea viewArea;
    @Shadow(remap = false)
    private List<LevelRenderer.RenderChunkInfo> renderInfosEntities;
    @Shadow(remap = false)
    private List<LevelRenderer.RenderChunkInfo> renderInfosTileEntities;

    /**
     * This mixin tells the {@link LevelRenderer} to render ship chunks.
     */
    @Inject(
        method = "setupRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/optifine/Config;isFogOn()Z"
        )
    )
    private void addShipVisibleChunksForBlockEntitiesAndEntities(
        final Camera camera, final Frustum frustum, final boolean hasForcedFrustum, final int frame,
        final boolean spectator, final CallbackInfo ci) {
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
                        final LevelRenderer.RenderChunkInfo newChunkInfo =
                            RenderChunkInfoAccessorOptifine.vs$new(renderChunk, null, 0);
                        renderInfosEntities.add(newChunkInfo);
                        renderInfosTileEntities.add(newChunkInfo);
                    }
                }
                return null;
            });
        }
    }

    @Shadow
    private boolean needsUpdate;

    @Inject(method = "setupRender", at = @At(value = "HEAD"))
    private void addShipVisibleChunks(final Camera activeRenderInfo, final Frustum camera, final boolean debugCamera,
        final int frameCount, final boolean playerSpectator, final CallbackInfo ci) {
        // This fixes shadows acting strangely when using shaders
        if (Config.isShaders()) {
            needsUpdate = true;
        }
    }
}
