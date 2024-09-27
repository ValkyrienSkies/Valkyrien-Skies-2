package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.ListIterator;
import java.util.WeakHashMap;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixin.mod_compat.optifine.RenderChunkInfoAccessorOptifine;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererVanilla {
    @Unique
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

    @Unique
    private ObjectList<RenderChunkInfo> renderChunksGeneratedByVanilla = new ObjectArrayList<>();

    /**
     * Fix the distance to render chunks, so that MC doesn't think ship chunks are too far away
     */
    @Redirect(
        method = "compileChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"
        ),
        require = 0
    )
    private double includeShipChunksInNearChunks(final BlockPos b, final Vec3i v) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            level, b.getX(), b.getY(), b.getZ(), v.getX(), v.getY(), v.getZ()
        );
    }

    /**
     * Force frustum update if the ship moves and the camera doesn't
     */
    @ModifyExpressionValue(
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicBoolean;compareAndSet(ZZ)Z"
        ),
        method = "setupRender"
    )
    private boolean needsFrustumUpdate(final boolean needsFrustumUpdate) {
        final Player player = minecraft.player;

        // force frustum update if default behaviour says to OR if the player is mounted to a ship
        return needsFrustumUpdate ||
            (player != null && VSGameUtilsKt.getShipMountedTo(player) != null);
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

            shipObject.getActiveChunksSet().forEach((x, z) -> {
                final LevelChunk levelChunk = level.getChunk(x, z);
                for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final ChunkRenderDispatcher.RenderChunk renderChunk =
                        chunkStorageAccessor.callGetRenderChunkAt(tempPos);
                    if (renderChunk != null) {
                        // If the chunk section is empty then skip it
                        final LevelChunkSection levelChunkSection = levelChunk.getSection(y - level.getMinSection());
                        if (levelChunkSection.hasOnlyAir()) {
                            continue;
                        }

                        // If the chunk isn't in the frustum then skip it
                        final AABBd b2 = new AABBd((x << 4) - 6e-1, (y << 4) - 6e-1, (z << 4) - 6e-1,
                            (x << 4) + 15.6, (y << 4) + 15.6, (z << 4) + 15.6)
                            .transform(shipObject.getRenderTransform().getShipToWorld());

                        if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(b2))) {
                            continue;
                        }

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

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V"
        ),
        method = "*"
    )
    private void redirectRenderChunkLayer(final LevelRenderer receiver,
        final RenderType renderType, final PoseStack poseStack, final double camX, final double camY, final double camZ,
        final Matrix4f matrix4f, final Operation<Void> renderChunkLayer) {

        renderChunkLayer.call(receiver, renderType, poseStack, camX, camY, camZ, matrix4f);

        VSGameEvents.INSTANCE.getShipsStartRendering().emit(new VSGameEvents.ShipStartRenderEvent(
            receiver, renderType, poseStack, camX, camY, camZ, matrix4f
        ));

        shipRenderChunks.forEach((ship, chunks) -> {
            poseStack.pushPose();
            final Vector3dc center = ship.getRenderTransform().getPositionInShip();
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), poseStack,
                center.x(), center.y(), center.z(),
                camX, camY, camZ);

            final var event = new VSGameEvents.ShipRenderEvent(
                receiver, renderType, poseStack, camX, camY, camZ, matrix4f, ship, chunks
            );

            VSGameEvents.INSTANCE.getRenderShip().emit(event);
            renderChunkLayer(renderType, poseStack, center.x(), center.y(), center.z(), matrix4f, chunks);
            VSGameEvents.INSTANCE.getPostRenderShip().emit(event);

            poseStack.popPose();
        });
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunksInFrustum:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
        ),
        method = "renderChunkLayer"
    )
    private ObjectArrayList<RenderChunkInfo> redirectRenderChunksInFrustum(final LevelRenderer instance) {
        return (ObjectArrayList<RenderChunkInfo>) renderChunksGeneratedByVanilla;
    }

    @Unique
    private void renderChunkLayer(final RenderType renderType, final PoseStack poseStack, final double d,
        final double e, final double f,
        final Matrix4f matrix4f, final ObjectList<RenderChunkInfo> chunksToRender) {
        RenderSystem.assertOnRenderThread();
        renderType.setupRenderState();
        this.minecraft.getProfiler().push("filterempty");
        this.minecraft.getProfiler().popPush(() -> {
            return "render_" + renderType;
        });
        boolean bl = renderType != RenderType.translucent();
        final ListIterator objectListIterator = chunksToRender.listIterator(bl ? 0 : chunksToRender.size());
        ShaderInstance shaderInstance = RenderSystem.getShader();

        for(int k = 0; k < 12; ++k) {
            int l = RenderSystem.getShaderTexture(k);
            shaderInstance.setSampler("Sampler" + k, l);
        }

        if (shaderInstance.MODEL_VIEW_MATRIX != null) {
            shaderInstance.MODEL_VIEW_MATRIX.set(poseStack.last().pose());
        }

        if (shaderInstance.PROJECTION_MATRIX != null) {
            shaderInstance.PROJECTION_MATRIX.set(matrix4f);
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
        Uniform uniform = shaderInstance.CHUNK_OFFSET;

        while(true) {
            if (bl) {
                if (!objectListIterator.hasNext()) {
                    break;
                }
            } else if (!objectListIterator.hasPrevious()) {
                break;
            }

            RenderChunkInfo renderChunkInfo2 = bl ? (RenderChunkInfo)objectListIterator.next() : (RenderChunkInfo)objectListIterator.previous();
            ChunkRenderDispatcher.RenderChunk renderChunk = renderChunkInfo2.chunk;
            if (!renderChunk.getCompiledChunk().isEmpty(renderType)) {
                VertexBuffer vertexBuffer = renderChunk.getBuffer(renderType);
                BlockPos blockPos = renderChunk.getOrigin();
                if (uniform != null) {
                    uniform.set((float)((double)blockPos.getX() - d), (float)((double)blockPos.getY() - e), (float)((double)blockPos.getZ() - f));
                    uniform.upload();
                }

                vertexBuffer.bind();
                vertexBuffer.draw();
            }
        }

        if (uniform != null) {
            uniform.set(new Vector3f());
        }

        shaderInstance.clear();
        VertexBuffer.unbind();
        this.minecraft.getProfiler().pop();
        renderType.clearRenderState();
    }

}
