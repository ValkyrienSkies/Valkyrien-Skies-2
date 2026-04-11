package org.valkyrienskies.mod.mixin.client.world;

import static org.valkyrienskies.mod.common.BlockStateInfo.isSortedRegistryInitialized;
import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getApi;
import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3i;
import org.joml.Vector3ic;
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
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.SodiumCompat;
import org.valkyrienskies.mod.compat.VSRenderer;
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
    @Final
    public ClientLevel level;

    public LongObjectMap<LevelChunk> vs$getShipChunks() {
        return vs$shipChunks;
    }

    @Unique
    private final LongObjectMap<LevelChunk> vs$shipChunks = new LongObjectHashMap<>();

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
    private final LongObjectMap<LevelChunk> vs$shipyardChunkCache = new LongObjectHashMap<>();

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void preLoadChunkFromPacket(final int x, final int z,
        final FriendlyByteBuf buf,
        final CompoundTag tag,
        final Consumer<BlockEntityTagOutput> consumer,
        final CallbackInfoReturnable<LevelChunk> cir
    ) {
        if (VSGameUtilsKt.isChunkInShipyard(level, x, z)) {
            // When real data arrives from server, remove the empty placeholder from VS cache
            vs$shipyardChunkCache.remove(ChunkPos.asLong(x, z));
            if (Minecraft.getInstance().levelRenderer instanceof final LevelRendererDuck levelRenderer) {
                levelRenderer.vs$setNeedsFrustumUpdate();
            }
            final ChunkPos pos = new ChunkPos(x, z);
            final long chunkPosLong = pos.toLong();
            final LevelChunk oldChunk = vs$shipChunks.get(chunkPosLong);
            final LevelChunk worldChunk;
            boolean shouldForce = false;
            if (oldChunk != null) {
                worldChunk = oldChunk;
                oldChunk.replaceWithPacketData(buf, tag, consumer);
                shouldForce = true;
            } else {
                worldChunk = new LevelChunk(this.level, pos);
                worldChunk.replaceWithPacketData(buf, tag, consumer);
                vs$shipChunks.put(chunkPosLong, worldChunk);
            }

            boolean shouldDefer = !isSortedRegistryInitialized();
            if (shouldDefer) {
                ClientConnectivityUpdateQueue.queueChunkForInitialization(pos, shouldForce);
            }

            VsiClientShipWorld clientShipWorld = VSGameUtilsKt.getShipObjectWorld(level);
            if (clientShipWorld != null && VSGameConfig.CLIENT.getConnectivity().getEnableClientConnectivity() && !shouldDefer) {
                ArrayList<VsiTerrainUpdate> voxelShapeUpdates = new ArrayList<>();


                final LevelChunkSection[] chunkSections = worldChunk.getSections();

                for (int sectionY = 0; sectionY < chunkSections.length; sectionY++) {
                    final LevelChunkSection chunkSection = chunkSections[sectionY];
                    final Vector3ic chunkPos =
                        new Vector3i(pos.x, worldChunk.getSectionYFromSectionIndex(sectionY), pos.z);

                    if (chunkSection != null && !chunkSection.hasOnlyAir()) {
                        // Add this chunk to the ground rigid body
                        final VsiTerrainUpdate voxelShapeUpdate =
                            VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, chunkPos);
                        voxelShapeUpdates.add(voxelShapeUpdate);
                    } else {
                        final VsiTerrainUpdate emptyVoxelShapeUpdate = getVsCore()
                            .newEmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), true);
                        voxelShapeUpdates.add(emptyVoxelShapeUpdate);
                    }
                }
                if (!shouldForce) {
                    clientShipWorld.addTerrainUpdates(getApi().getDimensionId(level), voxelShapeUpdates);
                } else {
                    for (VsiTerrainUpdate update : voxelShapeUpdates) {
                        clientShipWorld.forceUpdateConnectivityChunk(
                            getApi().getDimensionId(level),
                            update.getChunkX(),
                            update.getChunkY(),
                            update.getChunkZ(),
                            update
                        );
                    }
                }

            }

            // Force MC's light engine to recompute lighting for this ship chunk.
            // The client's LevelLightEngine is synchronous — call checkBlock for each
            // non-air block so the engine computes proper sky light with shadows.
            vs$relightChunk(worldChunk);

            // Flush all queued light updates NOW so that when render chunks are dirtied
            // below, they recompile with correct light data. Without this, there's a race
            // condition (~5% on Fabric) where render chunks compile before light is ready.
            // Loop because cascading propagation may queue additional work.
            while (this.level.getLightEngine().runLightUpdates() > 0) {
                // keep flushing
            }

            // Mark render chunks dirty AFTER relighting so they recompile with correct
            // light data. Include neighbors — light propagates across chunk boundaries.
            if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() != VSRenderer.SODIUM) {
                final IVSViewAreaMethods viewArea = (IVSViewAreaMethods)
                    ((LevelRendererAccessor) ((ClientLevelAccessor) level).getLevelRenderer()).getViewArea();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int sy = level.getMinSection(); sy < level.getMaxSection(); sy++) {
                            final var renderChunk = viewArea.vs$getShipRenderChunk(x + dx, sy, z + dz);
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
    }

    @Override
    public void vs$removeShip(final ClientShip ship) {
        final ChunkClaim chunks = ship.getChunkClaim();
        for (int x = chunks.getXStart(); x <= chunks.getXEnd(); x++) {
            for (int z = chunks.getZStart(); z <= chunks.getZEnd(); z++) {
                this.removeShipChunk(x, z);
                // Also clean up the VS cache for this position
                vs$shipyardChunkCache.remove(ChunkPos.asLong(x, z));
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    public void preUnload(final int chunkX, final int chunkZ, final CallbackInfo ci) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) {
            LevelChunk worldChunk = vs$shipChunks.remove(ChunkPos.asLong(chunkX, chunkZ));
            vs$shipyardChunkCache.remove(ChunkPos.asLong(chunkX, chunkZ));
            if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() != VSRenderer.SODIUM) {
                ((IVSViewAreaMethods) ((LevelRendererAccessor) ((ClientLevelAccessor) level).getLevelRenderer()).getViewArea())
                    .unloadChunk(chunkX, chunkZ);
            } else {
                SodiumCompat.onChunkRemoved(this.level, chunkX, chunkZ);
            }
            VsiClientShipWorld clientShipWorld = VSGameUtilsKt.getShipObjectWorld(level);
            if (clientShipWorld != null && VSGameConfig.CLIENT.getConnectivity().getEnableClientConnectivity()) {

                ArrayList<VsiTerrainUpdate> voxelShapeUpdates = new ArrayList<>();
                final LevelChunkSection[] chunkSections = worldChunk.getSections();

                for (int sectionY = 0; sectionY < chunkSections.length; sectionY++) {
                    final LevelChunkSection chunkSection = chunkSections[sectionY];
                    final Vector3ic chunkPos =
                        new Vector3i(chunkX, worldChunk.getSectionYFromSectionIndex(sectionY), chunkZ);

                    voxelShapeUpdates.add(getVsCore().newDeleteTerrainUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z()));
                }
                clientShipWorld.addTerrainUpdates(getApi().getDimensionId(level), voxelShapeUpdates);
            }
        }
        ci.cancel();
    }

    @Unique
    private void removeShipChunk(final int chunkX, final int chunkZ) {
        if (vs$shipChunks.remove(ChunkPos.asLong(chunkX, chunkZ)) == null) {
            return;
        }
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() != VSRenderer.SODIUM) {
            ((IVSViewAreaMethods) ((LevelRendererAccessor) ((ClientLevelAccessor) level).getLevelRenderer()).getViewArea())
                .unloadChunk(chunkX, chunkZ);
        } else {
            SodiumCompat.onChunkRemoved(this.level, chunkX, chunkZ);
        }
    }

    @Inject(
        method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
        at = @At("HEAD"), cancellable = true)
    public void preGetChunk(
        final int chunkX,
        final int chunkZ,
        final ChunkStatus chunkStatus,
        final boolean bl,
        final CallbackInfoReturnable<LevelChunk> cir
    ) {
        // First check real ship chunks (received from server with block data)
        final LevelChunk shipChunk = vs$shipChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (shipChunk != null) {
            cir.setReturnValue(shipChunk);
            return;
        }

        // For shipyard positions without data yet, return a cached empty chunk.
        // This prevents null returns that cause rendering stalls and "chunk not loaded"
        // issues while the server is still sending chunk packets.
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            cir.setReturnValue(vs$getOrCreateCachedChunk(chunkX, chunkZ));
        }
    }


    /**
     * Force MC's light engine to recompute sky light for a ship chunk.
     * Mirrors the server-side initSkyLightForShip: initializes section status,
     * propagates light sources, then checks every non-air block.
     */
    @Unique
    private void vs$relightChunk(LevelChunk chunk) {
        try {
            final var lightEngine = this.level.getLightEngine();
            final ChunkPos cp = chunk.getPos();
            final int baseX = cp.getMinBlockX();
            final int baseZ = cp.getMinBlockZ();

            final LevelChunkSection[] sections = chunk.getSections();

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
                            if (!section.getBlockState(lx, ly, lz).is(Blocks.AIR)) {
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
     * replaceWithPacketData and is stored in vs$shipChunks.
     */
    @Unique
    private LevelChunk vs$getOrCreateCachedChunk(int x, int z) {
        final long posLong = ChunkPos.asLong(x, z);
        LevelChunk cached = vs$shipyardChunkCache.get(posLong);
        if (cached != null) return cached;

        // Create empty chunk — no blocks, just a container
        LevelChunk chunk = new LevelChunk(this.level, new ChunkPos(x, z));
        vs$shipyardChunkCache.put(posLong, chunk);
        return chunk;
    }
}
