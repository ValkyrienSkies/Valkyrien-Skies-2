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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
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
import org.spongepowered.asm.mixin.Mutable;
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
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixinducks.client.render.LevelRendererVanillaDuck;

@Mixin(value = LevelRenderer.class, priority = 999)
public abstract class MixinLevelRendererVanilla implements LevelRendererVanillaDuck {
    @Unique
    private final WeakHashMap<ClientShip, ObjectArrayList<SectionRenderDispatcher.RenderSection>> vs$shipRenderChunks = new WeakHashMap<>();
    @Shadow
    private ClientLevel level;

    @Shadow
    @Final
    @Mutable
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow
    private @Nullable ViewArea viewArea;
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> vs$renderChunksGeneratedByVanilla = new ObjectArrayList<>();

    /**
     * Fix the distance to render chunks, so that MC doesn't think ship chunks are too far away
     */
    @Redirect(
        method = "compileSections",
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
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"
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
        method = "setupRender",
        at = @At("RETURN")
    )
    private void preSetupRender(final Camera camera, final Frustum frustum, final boolean bl, final boolean bl2, final CallbackInfo ci) {
        // This mixin never gets called for IP dimensions, instead we'll call it manually
        vs$addShipVisibleChunks(frustum);
    }

    @Override
    public void vs$addShipVisibleChunks(final Frustum frustum) {
        vs$renderChunksGeneratedByVanilla = new ObjectArrayList<>(visibleSections);

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
                    final SectionRenderDispatcher.RenderSection renderChunk =
                        chunkStorageAccessor.callGetRenderSectionAt(tempPos);
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

                        vs$shipRenderChunks.computeIfAbsent(shipObject, k -> new ObjectArrayList<>()).add(renderChunk);
                        visibleSections.add(renderChunk);
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
        vs$shipRenderChunks.forEach((ship, chunks) -> chunks.clear());
    }

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"
        ),
        method = "renderLevel"
    )
    private void redirectRenderChunkLayer(
        final LevelRenderer receiver,
        final RenderType renderType,
        final double camX,
        final double camY,
        final double camZ,
        final Matrix4f poseMatrix,
        final Matrix4f projectionMatrix,
        final Operation<Void> renderChunkLayer
    ) {
        final var originalRenderChunks = visibleSections;
        visibleSections = vs$renderChunksGeneratedByVanilla;
        renderChunkLayer.call(receiver, renderType, camX, camY, camZ, poseMatrix, projectionMatrix);
        visibleSections = originalRenderChunks;

        VSGameEvents.INSTANCE.getShipsStartRendering().emit(new VSGameEvents.ShipStartRenderEvent(
            receiver, renderType, camX, camY, camZ, poseMatrix, projectionMatrix
        ));

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(poseMatrix);

        vs$shipRenderChunks.forEach((ship, chunks) -> {
            poseStack.pushPose();
            final Vector3dc center = ship.getRenderTransform().getPositionInShip();
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), poseStack,
                center.x(), center.y(), center.z(),
                camX, camY, camZ);

            final var event = new VSGameEvents.ShipRenderEvent(
                receiver, renderType, camX, camY, camZ, poseMatrix, projectionMatrix, ship, chunks
            );

            VSGameEvents.INSTANCE.getRenderShip().emit(event);
            renderChunkLayer(renderType, poseStack, center.x(), center.y(), center.z(), poseMatrix, chunks);
            VSGameEvents.INSTANCE.getPostRenderShip().emit(event);

            poseStack.popPose();
        });
    }


    @Unique
    private void renderChunkLayer(final RenderType renderType, final PoseStack poseStack, final double d,
        final double e, final double f,
        final Matrix4f matrix4f, final ObjectList<SectionRenderDispatcher.RenderSection> chunksToRender) {
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

            final SectionRenderDispatcher.RenderSection renderChunk = bl ? (SectionRenderDispatcher.RenderSection)objectListIterator.next() : (SectionRenderDispatcher.RenderSection)objectListIterator.previous();
            final SectionRenderDispatcher.CompiledSection compiledSection = renderChunk.getCompiled();
            if (!compiledSection.isEmpty(renderType)) {
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
