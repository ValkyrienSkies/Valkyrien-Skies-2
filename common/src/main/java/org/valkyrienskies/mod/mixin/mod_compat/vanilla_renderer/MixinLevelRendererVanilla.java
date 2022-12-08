package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo;
import net.minecraft.client.renderer.RenderType;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixin.mod_compat.optifine.RenderChunkInfoAccessorOptifine;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererVanilla {
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
    @Unique
    private ObjectList<RenderChunkInfo> renderChunksToUse = new ObjectArrayList<>();

    @Unique
    private boolean isRenderingShip = true;

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

            shipObject.getActiveChunksSet().forEach((x, z) -> {
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
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLcom/mojang/math/Matrix4f;)V"
        ),
        method = "*"
    )
    private void redirectRenderChunkLayer(final LevelRenderer receiver,
        final RenderType renderType, final PoseStack poseStack, final double camX, final double camY, final double camZ,
        final Matrix4f matrix4f, final Operation<Void> renderChunkLayer) {

        this.renderChunksToUse = this.renderChunksGeneratedByVanilla;
        renderChunkLayer.call(receiver, renderType, poseStack, camX, camY, camZ, matrix4f);

        shipRenderChunks.forEach((ship, chunks) -> {
            this.renderChunksToUse = chunks;
            this.isRenderingShip = true;

            poseStack.pushPose();
            final Vector3dc center = ship.getRenderTransform().getPositionInShip();
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), poseStack,
                center.x(), center.y(), center.z(),
                camX, camY, camZ);

            renderChunkLayer.call(receiver, renderType, poseStack, center.x(), center.y(), center.z(), matrix4f);
            poseStack.popPose();

            this.isRenderingShip = false;
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

    @ModifyExpressionValue(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;translucent()Lnet/minecraft/client/renderer/RenderType;"
        ),
        method = "renderChunkLayer"
    )
    private RenderType redirectRenderChunkLayer(final RenderType original) {
        if (isRenderingShip) {
            return null;
        }

        return original;
    }
}
