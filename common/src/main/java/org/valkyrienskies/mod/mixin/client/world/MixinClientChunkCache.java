package org.valkyrienskies.mod.mixin.client.world;

import static org.valkyrienskies.mod.common.BlockStateInfo.isSortedRegistryInitialized;
import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getApi;
import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ChunkClaim;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate;
import org.valkyrienskies.mod.air_pockets.client.ShipWaterPocketLiquidOverlay;
import org.valkyrienskies.mod.common.assembly.SeamlessChunksManager;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.multiplayer.ClientLevelAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.LevelRendererAccessor;
import org.valkyrienskies.mod.mixinducks.client.render.IVSViewAreaMethods;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;
import org.valkyrienskies.mod.mixinducks.mod_compat.vanilla_renderer.LevelRendererDuck;
import org.valkyrienskies.mod.util.ClientConnectivityUpdateQueue;

/**
 * The purpose of this mixin is to allow {@link ClientChunkCache} to store ship chunks.
 */
@Mixin(ClientChunkCache.class)
public abstract class MixinClientChunkCache implements ClientChunkCacheDuck {
    @Shadow
    volatile ClientChunkCache.Storage storage;
    @Shadow
    @Final
    public ClientLevel level;

    @Unique
    private final Long2ObjectMap<LevelChunk> shipChunks = new Long2ObjectOpenHashMap<>();

    /**
     * VS-managed client-side cache for shipyard chunks. When any code (rendering,
     * collision, block queries) asks for a shipyard chunk that hasn't arrived from
     * the server yet, we return an empty LevelChunk immediately instead of null.
     * This prevents rendering stalls and "chunk not loaded" delays during ship loading.
     *
     * When the real chunk data arrives via network packet (replaceWithPacketData),
     * this cache entry is replaced with the actual chunk containing block data.
     */
    @Unique
    private final Long2ObjectMap<LevelChunk> emptyShipChunks = new Long2ObjectOpenHashMap<>();

    @Unique
    private final it.unimi.dsi.fastutil.longs.LongSet vs$litOnce =
        new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    @Unique
    private final LongArrayFIFOQueue vs$pendingShipChunkUnloadQueue = new LongArrayFIFOQueue();

    @Unique
    private final LongOpenHashSet vs$pendingShipChunkUnloads = new LongOpenHashSet();

    @Override
    public Long2ObjectMap<LevelChunk> vs$getShipChunks() {
        return this.shipChunks;
    }

    /**
     * Notify the VS sodium light storage when the world's light engine reports a
     * section update. Without this hook, freshly placed torches / sky changes
     * near an already-tracked ship section are never reflected in the GPU light
     * buffer used by the ship shader.
     */
    @Inject(method = "onLightUpdate", at = @At("HEAD"))
    private void vs_sodium$onLightUpdate(LightLayer layer, SectionPos pos, CallbackInfo ci) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            SodiumCompat.getLightStorage().invalidateSection(pos.asLong());
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void preReplaceWithPacketData(
        final int x,
        final int z,
        final FriendlyByteBuf buf,
        final CompoundTag tag,
        final Consumer<BlockEntityTagOutput> consumer,
        final CallbackInfoReturnable<LevelChunk> cir
    ) {
        if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(x, z)) {
            return;
        }
        if (Minecraft.getInstance().levelRenderer instanceof final LevelRendererDuck levelRenderer) {
            levelRenderer.vs$setNeedsFrustumUpdate();
        }
        final ChunkPos pos = new ChunkPos(x, z);
        final long chunkPosLong = pos.toLong();
        this.vs$pendingShipChunkUnloads.remove(chunkPosLong);
        final LevelChunk oldChunk = this.shipChunks.get(chunkPosLong);
        this.vs$litOnce.remove(chunkPosLong);
        // When real data arrives from server, remove the empty placeholder from VS cache
        final LevelChunk oldEmptyChunk = this.emptyShipChunks.remove(chunkPosLong);
        final LevelChunk worldChunk;
        boolean shouldForce = false;
        if (oldChunk != null) {
            worldChunk = oldChunk;
            worldChunk.replaceWithPacketData(buf, tag, consumer);
            shouldForce = true;
        } else {
            worldChunk = oldEmptyChunk == null ? new LevelChunk(this.level, pos) : oldEmptyChunk;
            worldChunk.replaceWithPacketData(buf, tag, consumer);
            this.shipChunks.put(chunkPosLong, worldChunk);
            ((ClientChunkCacheDuck.StorageDuck) ((Object) (this.storage))).vs$incChunkCount();
        }

        final boolean connectivityEnabled = VSGameConfig.CLIENT.getConnectivity().getEnableClientConnectivity();
        boolean shouldDefer = !isSortedRegistryInitialized();
        if (connectivityEnabled) {
            if (shouldDefer) {
                ClientConnectivityUpdateQueue.queueChunkForInitialization(pos, shouldForce);
            } else {
                final VsiClientShipWorld clientShipWorld = VSGameUtilsKt.getShipObjectWorld(level);
                if (clientShipWorld != null) {
                    final LevelChunkSection[] chunkSections = worldChunk.getSections();
                    final ArrayList<VsiTerrainUpdate> voxelShapeUpdates = new ArrayList<>(chunkSections.length);
                    for (int i = 0; i < chunkSections.length; i++) {
                        final LevelChunkSection chunkSection = chunkSections[i];
                        final int sectionY = worldChunk.getSectionYFromSectionIndex(i);
                        voxelShapeUpdates.add(
                            chunkSection != null && !chunkSection.hasOnlyAir()
                                ? VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, new Vector3i(pos.x, sectionY, pos.z))
                                : getVsCore().newEmptyVoxelShapeUpdate(pos.x, sectionY, pos.z, true)
                        );
                    }
                    final String dimensionId = getApi().getDimensionId(level);
                    if (shouldForce) {
                        for (VsiTerrainUpdate update : voxelShapeUpdates) {
                            clientShipWorld.forceUpdateConnectivityChunk(
                                dimensionId,
                                update.getChunkX(),
                                update.getChunkY(),
                                update.getChunkZ(),
                                update
                            );
                        }
                    } else {
                        clientShipWorld.addTerrainUpdates(dimensionId, voxelShapeUpdates);
                    }
                }
            }
        }

        // Force MC's light engine to recompute lighting for this ship chunk.
        // The client's LevelLightEngine is synchronous — call checkBlock for each
        // non-air block so the engine computes proper sky light with shadows.
        this.relightChunk(worldChunk);

        // Flush all queued light updates NOW so that when render chunks are dirtied
        // below, they recompile with correct light data. Without this, there's a race
        // condition (~5% on Fabric) where render chunks compile before light is ready.
        // Loop because cascading propagation may queue additional work.
        final SeamlessChunksManager manager = SeamlessChunksManager.get();
        if (manager == null || !manager.inBulkDrain) {
            while (this.level.getLightEngine().runLightUpdates() > 0) {
                // keep flushing
            }
        }
        this.vs$markLitOnce(x, z);

        // Mark render chunks dirty AFTER relighting so they recompile with correct
        // light data. Include neighbors — light propagates across chunk boundaries.
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() != VSRenderer.SODIUM) {
            final IVSViewAreaMethods viewArea = (IVSViewAreaMethods)
                ((LevelRendererAccessor) ((ClientLevelAccessor) level).getLevelRenderer()).getViewArea();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int sy = level.getMinSection(); sy < level.getMaxSection(); sy++) {
                        final ChunkRenderDispatcher.RenderChunk renderChunk = viewArea.vs$getShipRenderChunk(x + dx, sy, z + dz);
                        if (renderChunk != null) {
                            renderChunk.setDirty(true);
                        }
                    }
                }
            }
        }

        this.level.onChunkLoaded(pos);
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            // getVSRenderer() only returns SODIUM if the mod is installed.
            // Methods of SodiumCompat check if Sodium is present but calling them
            // is not safe anyway as the class references Sodium classes so the game
            // crashes with NoClassDefFoundError.
            SodiumCompat.onChunkAdded(this.level, x, z);
        }
        cir.setReturnValue(worldChunk);
    }

    @Override
    public void vs$removeShip(final ClientShip ship) {
        final ChunkClaim chunks = ship.getChunkClaim();
        final int[] queued = new int[] {0};
        ship.getActiveChunksSet().forEach((x, z) -> {
            if (this.vs$enqueueShipChunkUnload(x, z)) {
                queued[0]++;
            }
        });
        if (queued[0] > 0) {
            return;
        }

        this.vs$enqueueCachedShipChunksInClaim(chunks, this.shipChunks.keySet().iterator());
        this.vs$enqueueCachedShipChunksInClaim(chunks, this.emptyShipChunks.keySet().iterator());
    }

    @Override
    public void vs$drainShipChunkUnloadQueue() {
        if (this.vs$pendingShipChunkUnloadQueue.isEmpty()) {
            return;
        }

        final VsiClientShipWorld clientShipWorld = VSGameUtilsKt.getShipObjectWorld(level);
        final boolean updateConnectivity =
            clientShipWorld != null && VSGameConfig.CLIENT.getConnectivity().getEnableClientConnectivity();
        final ArrayList<VsiTerrainUpdate> voxelShapeUpdates = updateConnectivity ? new ArrayList<>() : null;

        int removedChunks = 0;
        final int maxRemovedChunks =
            Math.max(1, Math.min(1024, VSGameConfig.CLIENT.getPerformance().getShipChunkUnloadBatchSize()));
        while (removedChunks < maxRemovedChunks && !this.vs$pendingShipChunkUnloadQueue.isEmpty()) {
            final long chunkPos = this.vs$pendingShipChunkUnloadQueue.dequeueLong();
            if (!this.vs$pendingShipChunkUnloads.remove(chunkPos)) {
                continue;
            }
            final int chunkX = (int) (chunkPos >> 32);
            final int chunkZ = (int) chunkPos;
            final LevelChunk removedChunk = this.removeShipChunk(chunkX, chunkZ);
            if (removedChunk == null) {
                continue;
            }
            removedChunks++;
            if (voxelShapeUpdates != null) {
                for (int sectionY = removedChunk.getMinSection(); sectionY < removedChunk.getMaxSection(); sectionY++) {
                    voxelShapeUpdates.add(getVsCore().newDeleteTerrainUpdate(chunkX, sectionY, chunkZ));
                }
            }
        }

        if (voxelShapeUpdates != null && !voxelShapeUpdates.isEmpty()) {
            clientShipWorld.addTerrainUpdates(getApi().getDimensionId(level), voxelShapeUpdates);
        }
    }

    @Unique
    private boolean vs$enqueueShipChunkUnload(final int chunkX, final int chunkZ) {
        final long chunkPos = ChunkPos.asLong(chunkX, chunkZ);
        if (!this.shipChunks.containsKey(chunkPos) && !this.emptyShipChunks.containsKey(chunkPos)) {
            return false;
        }
        if (!this.vs$pendingShipChunkUnloads.add(chunkPos)) {
            return false;
        }
        this.vs$pendingShipChunkUnloadQueue.enqueue(chunkPos);
        return true;
    }

    @Unique
    private void vs$enqueueCachedShipChunksInClaim(final ChunkClaim claim, final LongIterator chunkPositions) {
        while (chunkPositions.hasNext()) {
            final long chunkPos = chunkPositions.nextLong();
            if (this.vs$isChunkInClaim(chunkPos, claim)) {
                final int chunkX = (int) (chunkPos >> 32);
                final int chunkZ = (int) chunkPos;
                this.vs$enqueueShipChunkUnload(chunkX, chunkZ);
            }
        }
    }

    @Unique
    private boolean vs$isChunkInClaim(final long chunkPos, final ChunkClaim claim) {
        final int chunkX = (int) (chunkPos >> 32);
        final int chunkZ = (int) chunkPos;
        return chunkX >= claim.getXStart() && chunkX <= claim.getXEnd()
            && chunkZ >= claim.getZStart() && chunkZ <= claim.getZEnd();
    }

    @Unique
    private LevelChunk removeShipChunk(final int chunkX, final int chunkZ) {
        ShipWaterPocketLiquidOverlay.invalidateExteriorFluidChunk(this.level, chunkX, chunkZ);
        final LevelChunk chunk = this.shipChunks.remove(ChunkPos.asLong(chunkX, chunkZ));
        this.emptyShipChunks.remove(ChunkPos.asLong(chunkX, chunkZ));
        this.vs$litOnce.remove(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return null;
        }
        ((ClientChunkCacheDuck.StorageDuck) ((Object) (this.storage))).vs$decChunkCount();
        this.level.unload(chunk);
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() != VSRenderer.SODIUM) {
            ((IVSViewAreaMethods) ((LevelRendererAccessor) ((ClientLevelAccessor) level).getLevelRenderer()).getViewArea())
                .unloadChunk(chunkX, chunkZ);
        } else {
            SodiumCompat.onChunkRemoved(this.level, chunkX, chunkZ);
        }
        return chunk;
    }

    @Inject(
        method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
        at = @At("HEAD"),
        cancellable = true
    )
    public void preGetChunk(
        final int chunkX,
        final int chunkZ,
        final ChunkStatus chunkStatus,
        final boolean bl,
        final CallbackInfoReturnable<LevelChunk> cir
    ) {
        if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            return;
        }
        // First check real ship chunks (received from server with block data)
        final LevelChunk shipChunk = this.shipChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (shipChunk != null) {
            cir.setReturnValue(shipChunk);
            return;
        }

        // For shipyard positions without data yet, return a cached empty chunk.
        // This prevents null returns that cause rendering stalls and "chunk not loaded"
        // issues while the server is still sending chunk packets.
        cir.setReturnValue(getOrCreateEmptyChunk(chunkX, chunkZ));
    }

    /**
     * Force MC's light engine to recompute sky light for a ship chunk.
     * Mirrors the server-side initSkyLightForShip: initializes section status,
     * propagates light sources, then checks every non-air block.
     */
    @Unique
    private void relightChunk(LevelChunk chunk) {
        relightChunk(chunk, false);
    }

    @Override
    public void vs$relightShipChunk(final int chunkX, final int chunkZ) {
        this.vs$relightShipChunk(chunkX, chunkZ, false);
    }

    @Override
    public void vs$relightShipChunk(final int chunkX, final int chunkZ, final boolean sweepAirCells) {
        final LevelChunk chunk = this.shipChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return;
        }
        relightChunk(chunk, sweepAirCells);
        while (this.level.getLightEngine().runLightUpdates() > 0) {
            // keep flushing
        }
    }

    @Override
    public void vs$preEnableShipChunkNeighborhood(final int chunkX, final int chunkZ) {
        final LevelLightEngine lightEngine = this.level.getLightEngine();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final int ncx = chunkX + dx;
                final int ncz = chunkZ + dz;
                if (VSGameUtilsKt.isChunkInShipyard(this.level, ncx, ncz)) {
                    lightEngine.setLightEnabled(new ChunkPos(ncx, ncz), true);
                }
            }
        }
    }

    @Override
    public boolean vs$hasBeenLitOnce(final int chunkX, final int chunkZ) {
        return this.vs$litOnce.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    @Override
    public void vs$markLitOnce(final int chunkX, final int chunkZ) {
        this.vs$litOnce.add(ChunkPos.asLong(chunkX, chunkZ));
    }

    @Unique
    private void relightChunk(LevelChunk chunk, final boolean sweepAirCells) {
        try {
            final LevelLightEngine lightEngine = this.level.getLightEngine();
            final ChunkPos cp = chunk.getPos();
            final int baseX = cp.getMinBlockX();
            final int baseZ = cp.getMinBlockZ();

            final LevelChunkSection[] sections = chunk.getSections();

            lightEngine.setLightEnabled(cp, true);

            // Step 1: Tell the light engine about non-empty sections (mirrors server's updateSectionStatus)
            for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                final LevelChunkSection section = sections[sIdx];
                if (section != null && !section.hasOnlyAir()) {
                    final int sectionY = chunk.getSectionYFromSectionIndex(sIdx);
                    lightEngine.updateSectionStatus(SectionPos.of(cp.x, sectionY, cp.z), false);
                }
            }

            // Step 2: Propagate light sources for this chunk and neighbors (mirrors server)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    lightEngine.propagateLightSources(new ChunkPos(cp.x + dx, cp.z + dz));
                }
            }

            // Step 3: checkBlock for every non-air block
            for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                final LevelChunkSection section = sections[sIdx];
                if (section == null || section.hasOnlyAir()) continue;
                final int sectionY = chunk.getSectionYFromSectionIndex(sIdx);
                final int baseY = sectionY << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            if (sweepAirCells || !section.getBlockState(lx, ly, lz).is(Blocks.AIR)) {
                                lightEngine.checkBlock(new BlockPos(baseX + lx, baseY + ly, baseZ + lz));
                            }
                        }
                    }
                }
            }
        } catch (final Exception ignored) {
            // Don't crash chunk loading if light recompute fails
        }
    }

    /**
     * Get or create an empty LevelChunk for a shipyard position.
     * These are lightweight placeholders — actual block data arrives later via
     * replaceWithPacketData and is stored in shipChunks.
     */
    @Unique
    private LevelChunk getOrCreateEmptyChunk(int x, int z) {
        final long posLong = ChunkPos.asLong(x, z);
        LevelChunk cached = this.emptyShipChunks.get(posLong);
        if (cached != null) {
            return cached;
        }
        // Create empty chunk — no blocks, just a container
        LevelChunk chunk = new LevelChunk(this.level, new ChunkPos(x, z));
        this.emptyShipChunks.put(posLong, chunk);
        return chunk;
    }

    @Mixin(ClientChunkCache.Storage.class)
    public static class MixinStorage implements ClientChunkCacheDuck.StorageDuck {
        @Shadow
        int chunkCount;

        @Override
        public void vs$incChunkCount() {
            this.chunkCount++;
        }

        @Override
        public void vs$decChunkCount() {
            this.chunkCount--;
        }
    }
}
