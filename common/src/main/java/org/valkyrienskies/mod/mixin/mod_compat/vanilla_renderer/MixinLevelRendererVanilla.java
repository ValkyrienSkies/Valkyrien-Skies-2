package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.List;
import java.util.ListIterator;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixin.mod_compat.optifine.RenderChunkInfoAccessorOptifine;

@Mixin(LevelRenderer.class)
public class MixinLevelRendererVanilla {
    private final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> shipRenderChunks = new WeakHashMap<>();
    @Shadow
    private ClientLevel level;
    @Shadow
    @Final
    private ObjectArrayList<RenderChunkInfo> renderChunksInFrustum;
    @Shadow
    private @Nullable ViewArea viewArea;
    @Shadow
    @Final
    private Minecraft minecraft;

    private ObjectList<RenderChunkInfo> renderChunksGeneratedByVanilla = new ObjectArrayList<>();

    /**
     * Fix the distance to render chunks, so that MC doesn't think ship chunks are too far away
     */
    @Redirect(
        method = "compileChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"
        )
    )
    private double includeShipChunksInNearChunks(final BlockPos b, final Vec3i v) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            level, b.getX(), b.getY(), b.getZ(), v.getX(), v.getY(), v.getZ()
        );
    }

    /**
     * Force frustum update if the ship moves and the camera doesn't
     */
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicBoolean;compareAndSet(ZZ)Z"
        ),
        method = "setupRender"
    )
    private boolean needsFrustumUpdate(final AtomicBoolean needsFrustumUpdate, final boolean expectedValue,
        final boolean newValue) {
        final Player player = minecraft.player;

        // force frustum update if default behaviour says to OR if the player is mounted to a ship
        return (needsFrustumUpdate != null && needsFrustumUpdate.compareAndSet(expectedValue, newValue)) ||
            (player != null && VSGameUtilsKt.getShipObjectEntityMountedTo(level, player) != null);
    }

    /**
     * Add ship render chunks to [renderChunks]
     */
    @Inject(
        method = "applyFrustum",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V"
        )
    )
    private void addShipVisibleChunks(
        final Frustum frustum, final CallbackInfo ci) {
        renderChunksGeneratedByVanilla = new ObjectArrayList<>(renderChunksInFrustum);

        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
        final ViewAreaAccessor chunkStorageAccessor = (ViewAreaAccessor) viewArea;
        for (final ClientShip shipObject : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            // Don't bother rendering the ship if its AABB isn't visible to the frustum
            if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(shipObject.getRenderAABB()))) {
                continue;
            }

            shipObject.getShipActiveChunksSet().iterateChunkPos((x, z) -> {
                for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
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
                                RenderChunkInfoAccessor.vs$new(renderChunk, null, 0);
                        }
                        shipRenderChunks.computeIfAbsent(shipObject, k -> new ObjectArrayList<>()).add(newChunkInfo);
                        renderChunksInFrustum.add(newChunkInfo);
                    }
                }
                return null;
            });
        }
    }

    @Inject(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;clear()V"
        )
    )
    private void clearShipChunks(final CallbackInfo ci) {
        shipRenderChunks.forEach((ship, chunks) -> chunks.clear());
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunksInFrustum:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
        ),
        method = "renderChunkLayer"
    )
    private ObjectArrayList<RenderChunkInfo> redirectRenderChunkLayer(final LevelRenderer instance) {
        return (ObjectArrayList<RenderChunkInfo>) renderChunksGeneratedByVanilla;
    }

    @Inject(
        at = @At("TAIL"),
        method = "renderChunkLayer"
    )
    private void afterRenderChunkLayer(
        final RenderType renderType, final PoseStack poseStack, final double d, final double e, final double f,
        final Matrix4f matrix4f, final CallbackInfo ci) {

        shipRenderChunks.forEach(
            (ship, chunks) -> renderChunkLayerVanilla(renderType, poseStack, d, e, f, matrix4f, chunks,
                ship.getRenderTransform()));
    }

    private void renderChunkLayerVanilla(
        final RenderType renderType, final PoseStack modelView, final double camX, final double camY, final double camZ,
        final Matrix4f projection, final List<RenderChunkInfo> chunksToRender, final ShipTransform transform) {
        if (chunksToRender.isEmpty()) {
            return;
        }

        final Vector3dc center = transform.getShipPositionInShipCoordinates();

        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();
//        if (renderType == RenderType.translucent()) {
//            this.minecraft.getProfiler().push("translucent_sort");
//            final double g = camX - this.xTransparentOld;
//            final double h = camY - this.yTransparentOld;
//            final double i = camZ - this.zTransparentOld;
//            if (g * g + h * h + i * i > 1.0) {
//                this.xTransparentOld = camX;
//                this.yTransparentOld = camY;
//                this.zTransparentOld = camZ;
//                int j = 0;
//                for (final RenderChunkInfo renderChunkInfo : chunksToRender) {
//                    if (j >= 15 || !((RenderChunkInfoAccessor) renderChunkInfo).getChunk()
//                        .resortTransparency(renderType, this.chunkRenderDispatcher)) {
//                        continue;
//                    }
//                    ++j;
//                }
//            }
//            this.minecraft.getProfiler().pop();
//        }
        this.minecraft.getProfiler().push("filterempty");
        this.minecraft.getProfiler().popPush(() -> "render_" + renderType);
        final boolean bl = renderType != RenderType.translucent();
        final ListIterator objectListIterator =
            chunksToRender.listIterator(bl ? 0 : chunksToRender.size());
        final VertexFormat vertexFormat = renderType.format();
        final ShaderInstance shaderInstance = RenderSystem.getShader();
        BufferUploader.reset();
        for (int k = 0; k < 12; ++k) {
            final int l = RenderSystem.getShaderTexture(k);
            shaderInstance.setSampler("Sampler" + k, l);
        }

        if (shaderInstance.MODEL_VIEW_MATRIX != null) {
            modelView.pushPose();
            VSClientGameUtils.transformRenderWithShip(transform, modelView, center.x(), center.y(), center.z(), camX,
                camY, camZ);
            shaderInstance.MODEL_VIEW_MATRIX.set(modelView.last().pose());
            modelView.popPose();
        }

        if (shaderInstance.PROJECTION_MATRIX != null) {
            shaderInstance.PROJECTION_MATRIX.set(projection);
        }
        if (shaderInstance.COLOR_MODULATOR != null) {
            shaderInstance.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        if (shaderInstance.FOG_START != null) {
            shaderInstance.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (shaderInstance.FOG_END != null) {
            shaderInstance.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        if (shaderInstance.FOG_COLOR != null) {
            shaderInstance.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        if (shaderInstance.FOG_SHAPE != null) {
            shaderInstance.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        if (shaderInstance.TEXTURE_MATRIX != null) {
            shaderInstance.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        if (shaderInstance.GAME_TIME != null) {
            shaderInstance.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }
        RenderSystem.setupShaderLights(shaderInstance);
        shaderInstance.apply();
        final Uniform uniform = shaderInstance.CHUNK_OFFSET;
        boolean bl2 = false;
        while (bl ? objectListIterator.hasNext() : objectListIterator.hasPrevious()) {
            final RenderChunkInfo renderChunkInfo2 =
                bl ? (RenderChunkInfo) objectListIterator.next() : (RenderChunkInfo) objectListIterator.previous();
            final ChunkRenderDispatcher.RenderChunk renderChunk =
                ((RenderChunkInfoAccessor) renderChunkInfo2).getChunk();
            if (renderChunk.getCompiledChunk().isEmpty(renderType)) {
                continue;
            }
            final VertexBuffer vertexBuffer = renderChunk.getBuffer(renderType);
            final BlockPos blockPos = renderChunk.getOrigin();

            if (uniform != null) {
                uniform.set((float) ((double) blockPos.getX() - center.x()),
                    (float) ((double) blockPos.getY() - center.y()),
                    (float) ((double) blockPos.getZ() - center.z()));
                uniform.upload();
            }
            vertexBuffer.drawChunkLayer();
            bl2 = true;
        }
        if (uniform != null) {
            uniform.set(Vector3f.ZERO);
        }
        shaderInstance.clear();
        if (bl2) {
            vertexFormat.clearBufferState();
        }
        VertexBuffer.unbind();
        VertexBuffer.unbindVertexArray();
        this.minecraft.getProfiler().pop();
        renderType.clearRenderState();
    }

    /**
     * This mixin tells the game where to render chunks; which allows us to render ship chunks in arbitrary positions.
     */
//    @Inject(
//        method = "renderChunkLayer",
//        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;upload()V"),
//        locals = LocalCapture.CAPTURE_FAILHARD
//    )
//    @SuppressWarnings("InvalidInjectorMethodSignature")
//    private void renderShipChunk(final RenderType renderLayer, final PoseStack matrixStack,
//        final double playerCameraX,
//        final double playerCameraY, final double playerCameraZ, final Matrix4f matrix4f, final CallbackInfo ci,
//        final boolean bl, final ObjectListIterator<?> objectListIterator,
//        final VertexFormat format, final ShaderInstance shader,
//        final Uniform uniform, final boolean bl2, final LevelRenderer.RenderChunkInfo info,
//        final ChunkRenderDispatcher.RenderChunk builtChunk, final VertexBuffer vertexBuffer) {
//
//        final int playerChunkX = ((int) playerCameraX) >> 4;
//        final int playerChunkZ = ((int) playerCameraZ) >> 4;
//        // Don't apply the ship render transform if the player is in the shipyard
//        final boolean isPlayerInShipyard = ChunkAllocator.isChunkInShipyard(playerChunkX, playerChunkZ);
//
//        final BlockPos renderChunkOrigin = builtChunk.getOrigin();
//        final ClientShip shipObject = VSGameUtilsKt.getShipObjectManagingPos(level, renderChunkOrigin);
//        if (!isPlayerInShipyard && shipObject != null) {
//            // matrixStack.pop(); so while checking for bugs this seems unusual?
//            // matrixStack.push(); but it doesn't fix sadly the bug im searching for
////            matrixStack.pushPose();
//
//            transformRenderWithShip(shipObject.getRenderTransform(), matrixStack, renderChunkOrigin,
//                playerCameraX, playerCameraY, playerCameraZ);
//            if (shader.PROJECTION_MATRIX != null) {
//                shader.PROJECTION_MATRIX.set(matrixStack.last().pose());
//            }
////            matrixStack.popPose();
//        } else {
//            // Restore MC default behavior (that was removed by cancelDefaultTransform())
//            uniform.set((float) (renderChunkOrigin.getX() - playerCameraX),
//                (float) (renderChunkOrigin.getY() - playerCameraY),
//                (float) (renderChunkOrigin.getZ() - playerCameraZ));
//        }
//    }

//    /**
//     * This mixin removes the vanilla code that determines where each chunk renders.
//     */
//    @Redirect(
//        method = "renderChunkLayer",
//        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;set(FFF)V")
//    )
//    private void cancelDefaultTransform(final Uniform instance, final float f, final float g, final float h) {
//        // Do nothing
//    }
}
