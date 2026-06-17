package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
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
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.util.datastructures.BlockPos2ByteOpenHashMap;
import org.valkyrienskies.mod.common.assembly.SeamlessChunksManager;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.VSRenderTypes;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.render.ShipSectionCache;
import org.valkyrienskies.mod.common.render.ShipSectionCandidate;
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer;
import org.valkyrienskies.mod.common.render.light.VsShipWorldLightRenderContext;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.LoadedMods.FlywheelVersion;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixinducks.client.render.IVSViewAreaMethods;
import org.valkyrienskies.mod.mixin.mod_compat.optifine.RenderChunkInfoAccessorOptifine;
import org.valkyrienskies.mod.mixinducks.mod_compat.vanilla_renderer.LevelRendererDuck;
import org.valkyrienskies.mod.mixinducks.client.render.LevelRendererVanillaDuck;

@Mixin(value = LevelRenderer.class, priority = 999)
public abstract class MixinLevelRendererVanilla implements LevelRendererDuck, LevelRendererVanillaDuck {
    @Unique
    private final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> shipRenderChunks = new WeakHashMap<>();
    @Unique
    private final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> vs$shipSolidRenderChunks = new WeakHashMap<>();
    @Unique
    private final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> vs$shipCutoutMippedRenderChunks = new WeakHashMap<>();
    @Unique
    private final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> vs$shipCutoutRenderChunks = new WeakHashMap<>();
    @Unique
    private final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> vs$shipTranslucentRenderChunks = new WeakHashMap<>();
    @Unique
    private final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> vs$shipTripwireRenderChunks = new WeakHashMap<>();
    @Unique
    private final WeakHashMap<ClientShip, ShipSectionCache> vs$shipSectionCaches = new WeakHashMap<>();
    @Shadow
    private ClientLevel level;

    @Shadow
    @Final
    @Mutable
    private ObjectArrayList<RenderChunkInfo> renderChunksInFrustum;

    @Shadow
    private @Nullable ViewArea viewArea;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private AtomicBoolean needsFrustumUpdate;

    @Unique
    private BlockPos2ByteOpenHashMap vs$visibileShipChunks = new BlockPos2ByteOpenHashMap();
    @Unique
    private Long lastMountedShipId = null;
    @Unique
    private ShipTransform lastTransform = null;
    @Unique
    private boolean vs$shipChunkVisibilityDirty = true;
    @Unique
    private long vs$lastShipVisibilitySignature = Long.MIN_VALUE;
    @Unique
    private int vs$lastShipVisibilityCount = -1;
    @Unique
    private long vs$lastSetupShipVisibilitySignature = Long.MIN_VALUE;
    @Unique
    private int vs$lastSetupShipVisibilityCount = -1;
    @Unique
    private boolean vs$emittedShipsStartRenderingThisFrame = false;
    @Unique
    private boolean vs$didApplyFrustumThisFrame = false;
    @Unique
    private int vs$lastShipFrustumTailCount = 0;
    @Unique
    private boolean vs$renderableLayerCachesDirty = true;
    @Unique
    private int vs$shipSectionCacheGeneration = 1;
    // Most recent culling frustum, captured in postApplyFrustum, used for whole-ship culling of
    // BATCHED ships in the batched draw pass (redirectRenderChunkLayer has no frustum param).
    @Unique
    private Frustum vs$lastFrustum = null;

    @Unique
    private static long vs$quantizeRenderCoord(final double value) {
        return Math.round(value * 256.0);
    }

    @Unique
    private static long vs$shipVisibilitySignature(final ClientShip ship) {
        long sig = ship.getId();
        final AABBdc renderAabb = ship.getRenderAABB();
        sig = 31L * sig + vs$quantizeRenderCoord(renderAabb.minX());
        sig = 31L * sig + vs$quantizeRenderCoord(renderAabb.minY());
        sig = 31L * sig + vs$quantizeRenderCoord(renderAabb.minZ());
        sig = 31L * sig + vs$quantizeRenderCoord(renderAabb.maxX());
        sig = 31L * sig + vs$quantizeRenderCoord(renderAabb.maxY());
        sig = 31L * sig + vs$quantizeRenderCoord(renderAabb.maxZ());
        return sig;
    }

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
    private boolean getNeedsFrustumUpdate(final boolean needsFrustumUpdate) {
        boolean shouldUpdateFrustum = needsFrustumUpdate;
        if (vs$updateSetupShipVisibilitySignature()) {
            this.vs$shipChunkVisibilityDirty = true;
            this.vs$renderableLayerCachesDirty = true;
            shouldUpdateFrustum = true;
        }

        // force frustum update if default behaviour says to OR if the player is mounted to a ship
        final Player player = this.minecraft.player;
        if (player == null || !(VSGameUtilsKt.getShipMountedTo(player) instanceof final ClientShip ship)) {
            this.lastMountedShipId = null;
            this.vs$shipChunkVisibilityDirty = shouldUpdateFrustum || this.vs$shipChunkVisibilityDirty;
            return shouldUpdateFrustum;
        }
        final ShipTransform transform = ship.getRenderTransform();
        if (this.lastMountedShipId == null || this.lastMountedShipId.longValue() != ship.getId() || this.lastTransform == null) {
            this.lastMountedShipId = ship.getId();
            this.lastTransform = transform;
            this.vs$shipChunkVisibilityDirty = true;
            return true;
        }
        final boolean needUpdate = this.lastTransform != transform && !this.lastTransform.getShipToWorld().equals(transform.getShipToWorld());
        this.lastTransform = transform;
        this.vs$shipChunkVisibilityDirty = needUpdate || shouldUpdateFrustum || this.vs$shipChunkVisibilityDirty;
        return needUpdate || shouldUpdateFrustum;
    }

    @Unique
    private boolean vs$updateSetupShipVisibilitySignature() {
        long signature = 0L;
        int count = 0;
        for (final ClientShip shipObject : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (!ShipRendererKt.getUsesTerrainChunkRenderer(shipObject)) {
                continue;
            }
            count++;
            signature = 31L * signature + vs$shipVisibilitySignature(shipObject);
        }

        final boolean changed =
            count != this.vs$lastSetupShipVisibilityCount ||
                signature != this.vs$lastSetupShipVisibilitySignature;
        this.vs$lastSetupShipVisibilityCount = count;
        this.vs$lastSetupShipVisibilitySignature = signature;
        return changed;
    }

    @Override
    public void vs$setNeedsFrustumUpdate() {
        this.needsFrustumUpdate.set(true);
        this.vs$shipChunkVisibilityDirty = true;
        this.vs$renderableLayerCachesDirty = true;
    }

    @Override
    public void vs$setShipChunkVisibilityDirty() {
        this.vs$shipChunkVisibilityDirty = true;
        this.vs$renderableLayerCachesDirty = true;
        this.vs$shipSectionCacheGeneration++;
    }

    /**
     * Add ship render chunks to [renderChunks]
     */
    @Inject(
        method = "applyFrustum",
        at = @At("RETURN")
    )
    private void postApplyFrustum(Frustum frustum, CallbackInfo ci){
        this.vs$lastFrustum = frustum;
        // Gradually pre-allocate render chunk GPU buffers so they're ready when ships load
        ((IVSViewAreaMethods) viewArea).vs$fillRenderChunkPool();
        // This mixin never gets called for IP dimensions, instead we'll call it manually
        vs$addShipVisibleChunks(frustum);
        this.vs$didApplyFrustumThisFrame = true;
    }

    @Inject(
        method = "renderLevel",
        at = @At("HEAD")
    )
    private void vs$resetShipRenderFrameState(final PoseStack poseStack, final float partialTick, final long finishNanoTime,
        final boolean renderBlockOutline, final Camera camera, final GameRenderer gameRenderer,
        final LightTexture lightTexture, final Matrix4f projectionMatrix, final CallbackInfo ci) {
        this.vs$emittedShipsStartRenderingThisFrame = false;
    }

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
            ordinal = 11
        )
    )
    private void vs$renderBatchedShipBlockEntities(final PoseStack poseStack, final float partialTick,
        final long finishNanoTime, final boolean renderBlockOutline, final Camera camera,
        final GameRenderer gameRenderer, final LightTexture lightTexture, final Matrix4f projectionMatrix,
        final CallbackInfo ci, @Local final MultiBufferSource.BufferSource bufferSource) {
        ShipBatchRenderer.INSTANCE.renderBlockEntities(level, poseStack, bufferSource, camera, partialTick);
    }

    @Inject(
        method = "setupRender",
        at = @At("HEAD")
    )
    private void vs$beginSetupRender(final Camera camera, final Frustum frustum, final boolean bl, final boolean bl2,
        final CallbackInfo ci) {
        this.vs$didApplyFrustumThisFrame = false;
    }

    /**
     * Process deferred ship chunk packets BEFORE vanilla's light updates so that
     * ship chunks are loaded and their light is computed before render chunks compile.
     */
    @Inject(
        method = "setupRender",
        at = @At("HEAD")
    )
    private void drainShipChunksBeforeLightUpdate(final Camera camera, final Frustum frustum, final boolean bl, final boolean bl2, final CallbackInfo ci) {
        final SeamlessChunksManager manager = SeamlessChunksManager.get();
        if (manager != null) {
            if (LoadedMods.getFlywheel() == FlywheelVersion.V1) {
                return;
            }
            manager.drainDeferredBatch();
            // Drain all queued light updates so the light engine has the latest data
            if (!level.isLightUpdateQueueEmpty()) {
                level.pollLightUpdates();
            }
        }
    }

    @Inject(method = "allChanged", at = @At("HEAD"))
    private void vs$freeBatchedBuffersOnAllChanged(final CallbackInfo ci) {
        ShipBatchRenderer.INSTANCE.freeAll();
    }

    @SuppressWarnings("unused")
    @Unique
    private void vs$removeShipFrustumTail() {
        if (this.vs$lastShipFrustumTailCount <= 0) {
            return;
        }
        final int targetSize = Math.max(0, this.renderChunksInFrustum.size() - this.vs$lastShipFrustumTailCount);
        while (this.renderChunksInFrustum.size() > targetSize) {
            this.renderChunksInFrustum.remove(this.renderChunksInFrustum.size() - 1);
        }
        this.vs$lastShipFrustumTailCount = 0;
    }

    @Unique
    private int vs$appendShipRenderChunksToFrustumList() {
        final int[] appended = {0};
        shipRenderChunks.forEach((ship, chunks) -> {
            for (int i = 0; i < chunks.size(); i++) {
                renderChunksInFrustum.add(chunks.get(i));
                appended[0]++;
            }
        });
        return appended[0];
    }

    @Override
    public void vs$addShipVisibleChunks(final Frustum frustum) {
        long shipVisibilitySignature = 0L;
        int loadedShipCount = 0;
        for (final ClientShip shipObject : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (!ShipRendererKt.getUsesTerrainChunkRenderer(shipObject)) {
                continue;
            }
            loadedShipCount++;
            shipVisibilitySignature = 31L * shipVisibilitySignature + vs$shipVisibilitySignature(shipObject);
        }

        if (!this.vs$shipChunkVisibilityDirty &&
            this.vs$lastShipVisibilityCount == loadedShipCount &&
            this.vs$lastShipVisibilitySignature == shipVisibilitySignature
        ) {
            this.vs$lastShipFrustumTailCount = this.vs$appendShipRenderChunksToFrustumList();
            return;
        }

        shipRenderChunks.forEach((ship, chunks) -> chunks.clear());
        vs$clearRenderableChunkCaches();
        this.vs$renderableLayerCachesDirty = true;
        vs$visibileShipChunks = new BlockPos2ByteOpenHashMap();
        this.vs$lastShipVisibilityCount = loadedShipCount;
        this.vs$lastShipVisibilitySignature = shipVisibilitySignature;
        this.vs$shipChunkVisibilityDirty = false;

        final IVSViewAreaMethods shipViewArea = (IVSViewAreaMethods) viewArea;
        final AABBd tempAABB = new AABBd();
        for (final ClientShip shipObject : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (!ShipRendererKt.getUsesTerrainChunkRenderer(shipObject))
                continue;

            if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(shipObject.getRenderAABB()))) {
                continue;
            }

            final var shipToWorld = shipObject.getRenderTransform().getShipToWorld();

            for (final ShipSectionCandidate candidate : vs$getShipSectionCache(shipObject).sections) {
                final int x = candidate.x;
                final int y = candidate.y;
                final int z = candidate.z;
                // Don't add ship chunks more than once
                if (vs$visibileShipChunks.contains(x, y, z)) {
                    continue;
                }

                tempAABB.setMin((x << 4) - 6e-1, (y << 4) - 6e-1, (z << 4) - 6e-1);
                tempAABB.setMax((x << 4) + 15.6, (y << 4) + 15.6, (z << 4) + 15.6);
                tempAABB.transform(shipToWorld);

                if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(tempAABB))) {
                    continue;
                }

                // Use direct ship render chunk lookup - bypasses getShipManagingPos
                ChunkRenderDispatcher.RenderChunk renderChunk = shipViewArea.vs$getShipRenderChunk(x, y, z);
                if (renderChunk == null) {
                    renderChunk = shipViewArea.vs$getOrCreateShipRenderChunk(x, y, z);
                }
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
                    vs$visibileShipChunks.put(x, y, z, (byte) 1);
                }
            }
        }

        this.vs$lastShipFrustumTailCount = this.vs$appendShipRenderChunksToFrustumList();
    }

    @Unique
    private ShipSectionCache vs$getShipSectionCache(final ClientShip ship) {
        final long[] activeChunkSignature = {0L};
        final int[] activeChunkCount = {0};
        ship.getActiveChunksSet().forEach((x, z) -> {
            final long chunkKey = ChunkPos.asLong(x, z);
            activeChunkCount[0]++;
            activeChunkSignature[0] += chunkKey * 0x9E3779B97F4A7C15L;
            activeChunkSignature[0] ^= Long.rotateLeft(chunkKey, 32);
        });

        final ShipSectionCache cached = this.vs$shipSectionCaches.get(ship);
        if (
            cached != null
                && cached.activeChunkCount == activeChunkCount[0]
                && cached.activeChunkSignature == activeChunkSignature[0]
                && cached.dirtyGeneration == this.vs$shipSectionCacheGeneration
        ) {
            return cached;
        }

        final ArrayList<ShipSectionCandidate> sections = new ArrayList<>();
        ship.getActiveChunksSet().forEach((x, z) -> {
            final LevelChunk levelChunk = level.getChunk(x, z);
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                final LevelChunkSection levelChunkSection = levelChunk.getSection(y - level.getMinSection());
                if (!levelChunkSection.hasOnlyAir()) {
                    sections.add(new ShipSectionCandidate(x, y, z));
                }
            }
        });

        final ShipSectionCache rebuilt = new ShipSectionCache(
            sections, activeChunkCount[0], activeChunkSignature[0], this.vs$shipSectionCacheGeneration);
        this.vs$shipSectionCaches.put(ship, rebuilt);
        return rebuilt;
    }

    @Override
    public boolean vs$isShipChunkVisible(final int chunkX, final int sectionY, final int chunkZ) {
        return vs$visibileShipChunks.contains(chunkX, sectionY, chunkZ);
    }

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V"
        ),
        method = "renderLevel"
    )
    private void redirectRenderChunkLayer(final LevelRenderer receiver,
        final RenderType renderType, final PoseStack poseStack, final double camX, final double camY, final double camZ,
        final Matrix4f matrix4f, final Operation<Void> renderChunkLayer) {

        if (!this.vs$emittedShipsStartRenderingThisFrame) {
            this.vs$emittedShipsStartRenderingThisFrame = true;
            VSGameEvents.INSTANCE.getShipsStartRendering().emit(new VSGameEvents.ShipStartRenderEvent(
                receiver, renderType, poseStack, camX, camY, camZ, matrix4f
            ));
            ShipBatchRenderer.INSTANCE.beginFrame(level);
        }

        VsShipWorldLightRenderContext.beginWorldTerrainLayer(camX, camY, camZ);
        try {
            renderChunkLayer.call(receiver, renderType, poseStack, camX, camY, camZ, matrix4f);
        } finally {
            VsShipWorldLightRenderContext.endWorldTerrainLayer();
        }

        ShipBatchRenderer.INSTANCE.drawLayer(renderType, poseStack, camX, camY, camZ, matrix4f,
            this.vs$lastFrustum);

        if (this.vs$renderableLayerCachesDirty) {
            vs$rebuildRenderableChunkCaches();
        }
        final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> renderableChunkMap =
            vs$getRenderableChunkMap(renderType);
        if (renderableChunkMap != null && !renderableChunkMap.isEmpty()) {
            final ObjectArrayList<ClientShip> renderableShips = new ObjectArrayList<>();
            final ObjectArrayList<ObjectList<RenderChunkInfo>> renderableChunkLists = new ObjectArrayList<>();
            renderableChunkMap.forEach((ship, chunks) -> {
                renderableShips.add(ship);
                renderableChunkLists.add(chunks);
            });
            if (renderableShips.isEmpty()) {
                return;
            }

            for (int i = 0; i < renderableShips.size(); i++) {
                final ClientShip ship = renderableShips.get(i);
                final ObjectList<RenderChunkInfo> chunks = renderableChunkLists.get(i);
                poseStack.pushPose();
                final ShipTransform shipTransform = ship.getRenderTransform();
                final Vector3dc cameraShipSpace = shipTransform.getWorldToShip().transformPosition(new Vector3d(camX, camY, camZ));
                VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), poseStack,
                    cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z(),
                    camX, camY, camZ);

                final var event = new VSGameEvents.ShipRenderEvent(
                    receiver, renderType, poseStack, camX, camY, camZ, matrix4f, ship, chunks
                );

                VSGameEvents.INSTANCE.getRenderShip().emit(event);
                RenderSystem.setShaderTexture(2, 0);
                renderChunkLayer(renderType, poseStack, cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z(), matrix4f, chunks);
                VSGameEvents.INSTANCE.getPostRenderShip().emit(event);
                poseStack.popPose();
            }
        }
    }

    @Unique
    private void vs$rebuildRenderableChunkCaches() {
        vs$clearRenderableChunkCaches();
        shipRenderChunks.forEach((ship, chunks) -> {
            for (int i = 0; i < chunks.size(); i++) {
                final RenderChunkInfo chunkInfo = chunks.get(i);
                final ChunkRenderDispatcher.RenderChunk renderChunk =
                    ((RenderChunkInfoAccessor) chunkInfo).getChunk();
                vs$appendToLayerCaches(ship, chunkInfo, renderChunk);
            }
        });
        this.vs$renderableLayerCachesDirty = false;
    }

    @Unique
    private void vs$clearRenderableChunkCaches() {
        vs$shipSolidRenderChunks.clear();
        vs$shipCutoutMippedRenderChunks.clear();
        vs$shipCutoutRenderChunks.clear();
        vs$shipTranslucentRenderChunks.clear();
        vs$shipTripwireRenderChunks.clear();
    }

    @Unique
    private void vs$appendToLayerCaches(final ClientShip ship, final RenderChunkInfo chunkInfo,
        final ChunkRenderDispatcher.RenderChunk renderChunk) {
        vs$appendToLayerCache(ship, chunkInfo, renderChunk, RenderType.solid(), vs$shipSolidRenderChunks);
        vs$appendToLayerCache(ship, chunkInfo, renderChunk, RenderType.cutoutMipped(), vs$shipCutoutMippedRenderChunks);
        vs$appendToLayerCache(ship, chunkInfo, renderChunk, RenderType.cutout(), vs$shipCutoutRenderChunks);
        vs$appendToLayerCache(ship, chunkInfo, renderChunk, RenderType.translucent(), vs$shipTranslucentRenderChunks);
        vs$appendToLayerCache(ship, chunkInfo, renderChunk, RenderType.tripwire(), vs$shipTripwireRenderChunks);
    }

    @Unique
    private void vs$appendToLayerCache(final ClientShip ship, final RenderChunkInfo chunkInfo,
        final ChunkRenderDispatcher.RenderChunk renderChunk, final RenderType renderType,
        final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> cache) {
        if (!renderChunk.getCompiledChunk().isEmpty(renderType)) {
            cache.computeIfAbsent(ship, k -> new ObjectArrayList<>()).add(chunkInfo);
        }
    }

    @Unique
    private WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> vs$getRenderableChunkMap(final RenderType renderType) {
        if (renderType == RenderType.solid()) return vs$shipSolidRenderChunks;
        if (renderType == RenderType.cutoutMipped()) return vs$shipCutoutMippedRenderChunks;
        if (renderType == RenderType.cutout()) return vs$shipCutoutRenderChunks;
        if (renderType == RenderType.translucent()) return vs$shipTranslucentRenderChunks;
        if (renderType == RenderType.tripwire()) return vs$shipTripwireRenderChunks;
        return null;
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

        // Use custom shader for existing render types
        ShaderInstance shaderInstance = null;
        if (VSGameConfig.CLIENT.getBetterVanillaShipShading()) {
            ShaderInstance shipShader = VSRenderTypes.Companion.shipShaderFor(renderType);
            if (shipShader != null) shaderInstance = shipShader;
        }
        if (shaderInstance == null) shaderInstance = RenderSystem.getShader();

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

        // Custom
        if (shaderInstance.INVERSE_VIEW_ROTATION_MATRIX != null) {
            shaderInstance.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
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
            ChunkRenderDispatcher.RenderChunk renderChunk = ((RenderChunkInfoAccessor) renderChunkInfo2).getChunk();
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

    @Override
    public VisibleChunkData vs$captureShipVisibleChunks() {
        WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> temp = new WeakHashMap<>();
        shipRenderChunks.forEach((ship, chunks) -> {
            ObjectArrayList<RenderChunkInfo> subTemp = new ObjectArrayList<>();
            chunks.forEach(subTemp::add);
            temp.put(ship, subTemp);
        });
        return new VisibleChunkData(temp, vs$visibileShipChunks);
    }

    @Override
    public void vs$reloadShipVisibleChunks(VisibleChunkData data) {
        this.vs$visibileShipChunks = data.visibleShipChunks();
        shipRenderChunks.forEach((ship, chunks) -> chunks.clear());
        shipRenderChunks.clear();
        this.shipRenderChunks.putAll(data.shipRenderChunks());
        this.vs$shipSectionCaches.clear();
        this.vs$renderableLayerCachesDirty = true;
    }

}
