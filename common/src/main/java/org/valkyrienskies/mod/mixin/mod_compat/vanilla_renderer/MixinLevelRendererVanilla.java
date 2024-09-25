package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import org.joml.Vector3dc;
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
import org.valkyrienskies.mod.compat.DhCompat;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixin.mod_compat.optifine.RenderChunkInfoAccessorOptifine;
import org.valkyrienskies.mod.mixinducks.client.render.VsViewArea;

@Mixin(value = LevelRenderer.class)
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

        vs$clearChunks();

        final boolean dh = LoadedMods.getDh();

        renderChunksGeneratedByVanilla = new ObjectArrayList<>(renderChunksInFrustum);

        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

        assert viewArea != null;
        final ViewAreaAccessor chunkStorageAccessor = (ViewAreaAccessor) viewArea;
        final VsViewArea vsViewArea = (VsViewArea) viewArea;

        for (final ClientShip shipObject : VSGameUtilsKt.getShipObjectWorld(level).getAllShips()) {
            final var renderAABB = shipObject.getRenderAABB();

            // TODO: for dh: use furstum too (but with extended render distance)
            if (!dh && !frustum.isVisible(VectorConversionsMCKt.toMinecraft(renderAABB))) {
                continue;
            }

            shipObject.getActiveChunksSet().forEach((x, z) -> {
                final LevelChunk levelChunk = level.getChunk(x, z);
                for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final ChunkRenderDispatcher.RenderChunk renderChunk;
                    if (dh) {
                        renderChunk = vsViewArea.vs$addExtra(tempPos);
                    } else {
                        renderChunk = chunkStorageAccessor.callGetRenderChunkAt(tempPos);
                    }

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

                        // TODO: for dh: use furstum too (but with extended render distance)
                        if (!dh && !frustum.isVisible(VectorConversionsMCKt.toMinecraft(b2))) {
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

    @Unique
    private void vs$clearChunks() {
        if (vs$chunksOverwrite != null)
            throw new RuntimeException("wtf");

        if (viewArea != null) {
            final VsViewArea vsViewArea = (VsViewArea) viewArea;
            vsViewArea.vs$clearExtra();
        }

        shipRenderChunks.forEach((ship, chunks) -> chunks.clear());
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void setLevel(final ClientLevel clientLevel, final CallbackInfo ci) {
        if (clientLevel == null) {
            vs$clearChunks();
        }
    }

    @Inject(method = "allChanged", at = @At("TAIL"))
    private void allChanged(final CallbackInfo ci) {
        if (level != null) {
            vs$clearChunks();
        }
    }

    @Unique
    private int vs$getViewDistance() {
        if (LoadedMods.getDh()) {
            return DhCompat.dhViewDistance() << 4;
        } else {
            return Minecraft.getInstance().options.getEffectiveRenderDistance();
        }
    }

    @Unique
    @Nullable
    private ObjectArrayList<LevelRenderer. RenderChunkInfo> vs$chunksOverwrite = null;

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

        renderChunkLayer.call(receiver, renderType, poseStack, camX, camY, camZ, matrix4f);

        VSGameEvents.INSTANCE.getShipsStartRendering().emit(new VSGameEvents.ShipStartRenderEvent(
            receiver, renderType, poseStack, camX, camY, camZ, matrix4f
        ));

        if (shipRenderChunks.isEmpty())
            return;

        vs$chunksOverwrite = new ObjectArrayList<>();

        final int viewDistance = vs$getViewDistance();

        shipRenderChunks.forEach((ship, chunks) -> {
            if (chunks.isEmpty()) return; // TODO: should this even happen?

            final var dist2cam = ship.getRenderTransform()
                .getPositionInWorld()
                .distance(camX, camY, camZ);

            if ((int) dist2cam > viewDistance) {
                return;
            }

            poseStack.pushPose();
            final Vector3dc center = ship.getRenderTransform().getPositionInShip();
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), poseStack,
                center.x(), center.y(), center.z(),
                camX, camY, camZ);

            final var event = new VSGameEvents.ShipRenderEvent(
                receiver, renderType, poseStack, camX, camY, camZ, matrix4f, ship, chunks
            );

            VSGameEvents.INSTANCE.getRenderShip().emit(event);

            vs$chunksOverwrite.clear();
            vs$chunksOverwrite.addAll(chunks);
            renderChunkLayer.call(receiver, renderType, poseStack, center.x(), center.y(), center.z(), matrix4f);

            VSGameEvents.INSTANCE.getPostRenderShip().emit(event);

            poseStack.popPose();
        });

        vs$chunksOverwrite = null;
    }

    @Redirect(
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunksInFrustum:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
        ),
        method = "renderChunkLayer"
    )
    private ObjectArrayList<RenderChunkInfo> redirectRenderChunksInFrustum(final LevelRenderer instance) {
        if (vs$chunksOverwrite != null) {
            return vs$chunksOverwrite;
        }

        return (ObjectArrayList<RenderChunkInfo>) renderChunksGeneratedByVanilla;
    }
}
