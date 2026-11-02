package org.valkyrienskies.mod.mixin.server.world;

import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
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
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Wing;
import org.valkyrienskies.core.api.ships.WingManager;
import org.valkyrienskies.core.api.util.AerodynamicUtils;
import org.valkyrienskies.core.impl.config.VSCoreConfig;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.block.WingBlock;
import org.valkyrienskies.mod.common.config.DimensionParametersResolver;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.DragInfoReporter;
import org.valkyrienskies.mod.common.util.VSServerLevel;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor;
import org.valkyrienskies.mod.mixin.accessors.server.level.DistanceManagerAccessor;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;
import org.valkyrienskies.mod.mixinducks.world.level.NoVSLevelDuck;
import org.valkyrienskies.mod.util.McMathUtilKt;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements IShipObjectWorldServerProvider, VSServerLevel {
    @Shadow
    @Final
    private ServerChunkCache chunkSource;

    @Shadow
    @NotNull
    public abstract MinecraftServer getServer();

    @Shadow
    public abstract int sectionsToVillage(SectionPos arg);


    /**
     * Allow scheduled ticks and block entity ticking in shipyard chunks that were loaded only to
     * FULL status through SHIP_CHUNK.
     *
     * Forge's Level.tickBlockEntities() calls shouldTickBlocksAt(BlockPos) before ticking each
     * block entity. Ship chunks use level 33 (FULL) tickets to minimize neighbor loading, but
     * shouldTickBlocksAt requires level ≤ 32. Without this override, furnaces/hoppers/etc won't
     * tick on ships.
     */
    @Inject(method = "shouldTickBlocksAt(J)Z", at = @At("HEAD"), cancellable = true)
    private void vs$allowShipyardBlockTicking(long packedPos, CallbackInfoReturnable<Boolean> cir) {
        // Try both BlockPos and ChunkPos decodings since Forge may use either format
        int chunkX, chunkZ;
        // First try BlockPos encoding (used by tickBlockEntities)
        chunkX = BlockPos.getX(packedPos) >> 4;
        chunkZ = BlockPos.getZ(packedPos) >> 4;
        if (org.valkyrienskies.mod.common.VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            if (chunkSource.getChunkNow(chunkX, chunkZ) != null) {
                cir.setReturnValue(true);
                return;
            }
        }
        // Also try ChunkPos encoding (used by LevelTicks for scheduled ticks)
        chunkX = ChunkPos.getX(packedPos);
        chunkZ = ChunkPos.getZ(packedPos);
        if (org.valkyrienskies.mod.common.VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            if (chunkSource.getChunkNow(chunkX, chunkZ) != null) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Allow FULL-only shipyard chunks to pass the LevelTicks tick-processing gate.
     *
     * LevelTicks uses isPositionTickingWithEntitiesLoaded as its tickCheck predicate.
     * This method requires BOTH areEntitiesLoaded() AND isPositionTicking() to be true.
     * Ship chunks may not have their entity sections loaded through the normal entity
     * management pipeline, so areEntitiesLoaded() returns false, which prevents ALL
     * scheduled ticks (repeaters, observers, torches, buttons) from being processed.
     */
    @Inject(method = "isPositionTickingWithEntitiesLoaded", at = @At("HEAD"), cancellable = true)
    private void vs$allowShipyardPositionTicking(long packedPos, CallbackInfoReturnable<Boolean> cir) {
        int chunkX = ChunkPos.getX(packedPos);
        int chunkZ = ChunkPos.getZ(packedPos);
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            cir.setReturnValue(true);
        }
    }

    // Map from ChunkPos to the list of voxel chunks that chunk owns
    @Unique
    private final Long2ObjectOpenHashMap<List<Vector3ic>> vs$knownChunks = new Long2ObjectOpenHashMap<>();

    // Maps chunk pos to number of ticks we have considered unloading the chunk
    @Unique
    private final Long2LongOpenHashMap vs$chunksToUnload = new Long2LongOpenHashMap();

    // How many ticks we wait before unloading a chunk
    @Unique
    private static final long VS$CHUNK_UNLOAD_THRESHOLD = 100;

    @Nullable
    @Override
    public VsiServerShipWorld getShipObjectWorld() {
        return ((IShipObjectWorldServerProvider) getServer()).getShipObjectWorld();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    void onInit(final CallbackInfo ci) {
        // Fake / wrapped ServerLevels are used by Create, Supplementaries and maybe others.
        if (this instanceof NoVSLevelDuck) return;

        // This only happens when overworld gets loaded on startup, we have a mixin in MixinMinecraftServer for this specific case
        if (getShipObjectWorld() != null) {
            DimensionParametersResolver.Parameters params = DimensionParametersResolver.INSTANCE.getDimensionMap().get(
                VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this)
            );
            if (params != null) {
                getShipObjectWorld().addDimension(
                    VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this),
                    VSGameUtilsKt.getYRange((ServerLevel) (Object) this),
                    params.getGravity(),
                    params.getSeaLevel(),
                    params.getMaxY()
                );
                return;
            }
            getShipObjectWorld().addDimension(
                VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this),
                VSGameUtilsKt.getYRange((ServerLevel) (Object) this),
                McMathUtilKt.getDEFAULT_WORLD_GRAVITY(),
                AerodynamicUtils.DEFAULT_SEA_LEVEL,
                AerodynamicUtils.DEFAULT_MAX
            );
        }
    }

    @Inject(method = "getPoiManager", at = @At("HEAD"))
    void onGetPoiManager(CallbackInfoReturnable<PoiManager> cir) {
        if (chunkSource.getPoiManager() instanceof final OfLevel levelProvider) {
            levelProvider.setLevel((ServerLevel) (Object) this);
        }
    }

    @Inject(method = "isCloseToVillage", at = @At("HEAD"), cancellable = true)
    void preIsCloseToVillage(BlockPos blockPos, int i, CallbackInfoReturnable<Boolean> cir) {
        if (i <= 6) {
            final boolean[] found = {false};
            VSGameUtilsKt.transformToNearbyShipsAndWorld(ServerLevel.class.cast(this), blockPos.getX(), blockPos.getY(), blockPos.getZ(), i * 100.0, (double x, double y, double z) -> {
                found[0] = found[0] || this.sectionsToVillage(SectionPos.of(BlockPos.containing(x, y, z))) <= i;
            });
            if (found[0]) {
                cir.setReturnValue(true);
            }
        }
    }

    /**
     * Include ships in particle distance check. Seems to only be used by /particle
     */
    @WrapOperation(
        method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean includeShipsInParticleDistanceCheck(
        final BlockPos player, final Position particle, final double distance,
        final Operation<Boolean> closerToCenterThan) {

        final ServerLevel self = ServerLevel.class.cast(this);
        final LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(
            self, (int) particle.x() >> 4, (int) particle.z() >> 4);

        if (ship == null) {
            // vanilla behaviour
            return closerToCenterThan.call(player, particle, distance);
        }

        // in-world position
        final Vector3d posInWorld = ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(particle));

        return posInWorld.distanceSquared(player.getX(), player.getY(), player.getZ()) < distance * distance;
    }

    @Unique
    private void vs$loadChunk(@NotNull final ChunkAccess worldChunk, final List<VsiTerrainUpdate> voxelShapeUpdates) {
        // Remove the chunk pos from vs$chunksToUnload if its present
        final long chunkPosLong = worldChunk.getPos().toLong();
        vs$chunksToUnload.remove(chunkPosLong);
        if (!vs$knownChunks.containsKey(chunkPosLong)) {
            // FULL-only shipyard chunks never reach BLOCK_TICKING status in vanilla,
            // so two critical callbacks are missed:
            // 1. registerTickContainerInLevel() — adds tick containers to LevelTicks
            // 2. startTickingChunk() → unpackTicks() — moves saved ticks from pendingTicks
            //    to the active tickQueue so they actually fire
            // Without both, scheduled ticks (repeaters, torches, observers) freeze on reload.
            if (worldChunk instanceof LevelChunk levelChunk) {
                final int cx = worldChunk.getPos().x;
                final int cz = worldChunk.getPos().z;
                if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) {
                    final ServerLevel self = ServerLevel.class.cast(this);
                    levelChunk.registerTickContainerInLevel(self);
                    self.startTickingChunk(levelChunk);
                }
            }

            final List<Vector3ic> voxelChunkPositions = new ArrayList<>();

            final int chunkX = worldChunk.getPos().x;
            final int chunkZ = worldChunk.getPos().z;

            final LevelChunkSection[] chunkSections = worldChunk.getSections();

            for (int sectionY = 0; sectionY < chunkSections.length; sectionY++) {
                final LevelChunkSection chunkSection = chunkSections[sectionY];
                final Vector3ic chunkPos =
                    new Vector3i(chunkX, worldChunk.getSectionYFromSectionIndex(sectionY), chunkZ);
                voxelChunkPositions.add(chunkPos);

                if (chunkSection != null && !chunkSection.hasOnlyAir()) {
                    // Add this chunk to the ground rigid body
                    final VsiTerrainUpdate voxelShapeUpdate =
                        VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, chunkPos);
                    voxelShapeUpdates.add(voxelShapeUpdate);

                    // region Detect wings
                    final ServerLevel thisAsLevel = ServerLevel.class.cast(this);
                    final LoadedServerShip
                        ship = VSGameUtilsKt.getLoadedShipManagingPos(thisAsLevel, chunkX, chunkZ);
                    if (ship != null) {
                        // Sussy cast, but I don't want to expose this directly through the vs-core api
                        final WingManager shipAsWingManager = ship.getWingManager();
                        final MutableBlockPos mutableBlockPos = new MutableBlockPos();
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    final BlockState blockState = chunkSection.getBlockState(x, y, z);
                                    final int posX = (chunkX << 4) + x;
                                    final int posY = worldChunk.getMinBuildHeight() + (sectionY << 4) + y;
                                    final int posZ = (chunkZ << 4) + z;
                                    if (blockState.getBlock() instanceof WingBlock) {
                                        mutableBlockPos.set(posX, posY, posZ);
                                        final Wing wing =
                                            ((WingBlock) blockState.getBlock()).getWing(thisAsLevel,
                                                mutableBlockPos, blockState);
                                        if (wing != null) {
                                            shipAsWingManager.setWing(shipAsWingManager.getFirstWingGroupId(),
                                                posX, posY, posZ, wing);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // endregion
                } else {
                    final VsiTerrainUpdate emptyVoxelShapeUpdate = getVsCore()
                        .newEmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), true);
                    voxelShapeUpdates.add(emptyVoxelShapeUpdate);
                }
            }
            vs$knownChunks.put(chunkPosLong, voxelChunkPositions);
        }
    }

    @Unique
    private Optional<LevelChunk> vs$getLoadedChunkFromHolder(final ChunkHolder chunkHolder) {
        Optional<LevelChunk> worldChunkOptional =
            chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left();
        // FULL-only shipyard chunks don't complete tickingChunkFuture,
        // so tickingChunkFuture is never completed. For these chunks, get the chunk
        // directly from the chunk cache instead of relying on futures.
        if (worldChunkOptional.isEmpty()) {
            final ChunkPos cp = chunkHolder.getPos();
            if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cp.x, cp.z)) {
                final LevelChunk cachedChunk = chunkSource.getChunkNow(cp.x, cp.z);
                if (cachedChunk != null) {
                    worldChunkOptional = Optional.of(cachedChunk);
                }
            }
        }
        return worldChunkOptional;
    }

    /**
     * Prevent vanilla from unloading shipyard chunks whose ship is still alive.
     *
     * Active ship chunks are intentionally held with vanilla forced tickets on 1.20.1
     * so their entities and blocks keep ticking. If another unload path reaches here
     * while the ship still exists, keep the chunk resident and preserve scheduled ticks.
     */
    @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
    private void vs$keepActiveShipChunksLoaded(final LevelChunk chunk, final CallbackInfo ci) {
        final ChunkPos pos = chunk.getPos();
        if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) return;
        final ServerLevel self = ServerLevel.class.cast(this);
        if (VSGameUtilsKt.getShipManagingPos(self, pos.x, pos.z) != null) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        final ServerLevel self = ServerLevel.class.cast(this);
        final VsiServerShipWorld shipObjectWorld = VSGameUtilsKt.getShipObjectWorld(self);
        // Find newly loaded chunks
        final ChunkMapAccessor chunkMapAccessor = (ChunkMapAccessor) chunkSource.chunkMap;

        // Create DenseVoxelShapeUpdate for new loaded chunks
        // Also mark the chunks as loaded in the ship objects
        final List<VsiTerrainUpdate> voxelShapeUpdates = new ArrayList<>();
        final DistanceManagerAccessor distanceManagerAccessor = (DistanceManagerAccessor) chunkSource.chunkMap.getDistanceManager();
        final int maxTerrainChunkLoads =
            Math.max(1, Math.min(4096, VSGameConfig.SERVER.getPerformance().getShipTerrainChunkLoadsPerTick()));
        final int maxTerrainChunkUnloads =
            Math.max(1, Math.min(4096, VSGameConfig.SERVER.getPerformance().getShipTerrainChunkUnloadsPerTick()));

        int loadedChunksThisTick = 0;
        final LongIterator pendingForcedChunkIterator = vs$pendingForcedChunks.iterator();
        while (pendingForcedChunkIterator.hasNext() && loadedChunksThisTick < maxTerrainChunkLoads) {
            final long chunkPosLong = pendingForcedChunkIterator.nextLong();
            if (vs$knownChunks.containsKey(chunkPosLong)) {
                pendingForcedChunkIterator.remove();
                continue;
            }
            if (!distanceManagerAccessor.getTickets().containsKey(chunkPosLong)) {
                pendingForcedChunkIterator.remove();
                continue;
            }
            final ChunkHolder chunkHolder = chunkMapAccessor.callGetVisibleChunkIfPresent(chunkPosLong);
            if (chunkHolder == null) {
                continue;
            }
            final Optional<LevelChunk> worldChunkOptional = vs$getLoadedChunkFromHolder(chunkHolder);
            if (worldChunkOptional.isPresent()) {
                vs$loadChunk(worldChunkOptional.get(), voxelShapeUpdates);
                pendingForcedChunkIterator.remove();
                loadedChunksThisTick++;
            }
        }

        for (final ChunkHolder chunkHolder : chunkMapAccessor.callGetChunks()) {
            if (loadedChunksThisTick >= maxTerrainChunkLoads) {
                break;
            }
            // Only load chunks that haven't been loaded before, and have a ticket
            final long chunkPosLong = chunkHolder.getPos().toLong();
            if (!vs$knownChunks.containsKey(chunkPosLong) && distanceManagerAccessor.getTickets().containsKey(chunkPosLong)) {
                Optional<LevelChunk> worldChunkOptional = vs$getLoadedChunkFromHolder(chunkHolder);
                if (worldChunkOptional.isPresent()) {
                    final LevelChunk worldChunk = worldChunkOptional.get();
                    vs$loadChunk(worldChunk, voxelShapeUpdates);
                    vs$pendingForcedChunks.remove(chunkPosLong);
                    loadedChunksThisTick++;
                }
            }
        }

        int unloadedChunksThisTick = 0;
        final Iterator<Long2ObjectMap.Entry<List<Vector3ic>>> knownChunkPosIterator =
            vs$knownChunks.long2ObjectEntrySet().fastIterator();
        while (knownChunkPosIterator.hasNext()) {
            final Long2ObjectMap.Entry<List<Vector3ic>> knownChunkPosEntry = knownChunkPosIterator.next();
            final long chunkPos = knownChunkPosEntry.getLongKey();
            // Unload chunks if they don't have tickets or if they're not in the visible chunks
            if ((!distanceManagerAccessor.getTickets().containsKey(chunkPos) || chunkMapAccessor.callGetVisibleChunkIfPresent(chunkPos) == null)) {
                final long ticksWaitingToUnload = vs$chunksToUnload.getOrDefault(chunkPos, 0L);
                if (ticksWaitingToUnload > VS$CHUNK_UNLOAD_THRESHOLD) {
                    if (unloadedChunksThisTick >= maxTerrainChunkUnloads) {
                        break;
                    }
                    // Unload this chunk
                    for (final Vector3ic unloadedChunk : knownChunkPosEntry.getValue()) {
                        final VsiTerrainUpdate deleteVoxelShapeUpdate =
                            getVsCore().newDeleteTerrainUpdate(unloadedChunk.x(), unloadedChunk.y(), unloadedChunk.z());
                        voxelShapeUpdates.add(deleteVoxelShapeUpdate);
                    }
                    knownChunkPosIterator.remove();
                    vs$chunksToUnload.remove(chunkPos);
                    unloadedChunksThisTick++;
                } else {
                    vs$chunksToUnload.put(chunkPos, ticksWaitingToUnload + 1);
                }
            }
        }

        // Send new loaded chunks updates to the ship world
        if (!voxelShapeUpdates.isEmpty()) {
            shipObjectWorld.addTerrainUpdates(
                VSGameUtilsKt.getDimensionId(self),
                voxelShapeUpdates
            );
        }

        if (VSCoreConfig.SERVER.getSp().getEnableSplitting()) {
            ValkyrienSkiesMod.splitHandler.tick(ServerLevel.class.cast(this));
        }

        DragInfoReporter.INSTANCE.tick((ServerLevel) (Object) this);

    }

    @Override
    public void removeChunk(final int chunkX, final int chunkZ) {
        final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        vs$knownChunks.remove(chunkPos);
    }

    @Unique
    private final LongOpenHashSet vs$pendingForcedChunks = new LongOpenHashSet();

    @Override
    public void addPendingForcedChunk(final int chunkX, final int chunkZ) {
        vs$pendingForcedChunks.add(ChunkPos.asLong(chunkX, chunkZ));
    }

    @NotNull
    @Override
    public LongOpenHashSet getPendingForcedChunks() {
        return vs$pendingForcedChunks;
    }
}
