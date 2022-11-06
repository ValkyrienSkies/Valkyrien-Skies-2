package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixin.mod_compat.optifine.RenderChunkInfoAccessorOptifine;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererVanilla {
    @Shadow
    private ClientLevel level;

    @Shadow
    @Final
    private ObjectArrayList<RenderChunkInfo> renderChunksInFrustum;
    @Shadow
    private @Nullable ViewArea viewArea;

    @Shadow
    protected abstract void renderChunkLayer(RenderType renderType, PoseStack poseStack, double d, double e, double f,
        Matrix4f matrix4f);

    @Unique
    private ObjectList<RenderChunkInfo> renderChunksGeneratedByVanilla = new ObjectArrayList<>();

    @Unique
    private final WeakHashMap<ShipObjectClient, ObjectList<RenderChunkInfo>> shipRenderChunks = new WeakHashMap<>();

    @Unique
    private ObjectList<RenderChunkInfo> renderChunksToUse = new ObjectArrayList<>();

    @Unique
    private ShipTransform transformToUse;

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
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLcom/mojang/math/Matrix4f;)V"
        ),
        method = "*"
    )
    private void redirectRenderChunkLayer(
        final RenderType renderType, final PoseStack poseStack, final double d, final double e, final double f,
        final Matrix4f matrix4f) {

        this.renderChunksToUse = this.renderChunksGeneratedByVanilla;
        this.transformToUse = null;
        renderChunkLayer(renderType, poseStack, d, e, f, matrix4f);

        shipRenderChunks.forEach((ship, chunks) -> {
            this.renderChunksToUse = chunks;
            this.transformToUse = ship.getRenderTransform();
            renderChunkLayer(renderType, poseStack, d, e, f, matrix4f);
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
        return (ObjectArrayList<RenderChunkInfo>) renderChunksToUse;
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/Uniform;set(Lcom/mojang/math/Matrix4f;)V",
            ordinal = 0
        ),
        method = "renderChunkLayer"
    )
    private void injectModelViewTransform(
        final RenderType renderType, final PoseStack poseStack, final double camX, final double camY, final double camZ,
        final Matrix4f matrix4f, final CallbackInfo ci) {

        if (transformToUse != null) {
            poseStack.pushPose();
            final Vector3dc center = transformToUse.getShipPositionInShipCoordinates();
            VSClientGameUtils.transformRenderWithShip(transformToUse, poseStack, center.x(), center.y(), center.z(),
                camX, camY, camZ);
        }
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/shaders/Uniform;set(Lcom/mojang/math/Matrix4f;)V",
            ordinal = 0,
            shift = Shift.AFTER
        ),
        method = "renderChunkLayer"
    )
    private void injectRenderChunkLayer(
        final RenderType renderType, final PoseStack poseStack, final double d, final double e, final double f,
        final Matrix4f matrix4f, final CallbackInfo ci) {

        if (transformToUse != null) {
            poseStack.popPose();
        }

    }
}
