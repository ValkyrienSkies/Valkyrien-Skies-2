package org.valkyrienskies.mod.mixin.server.world;

import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.block.WingBlock;
import org.valkyrienskies.mod.common.config.DimensionParametersResolver;
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


    // Map from ChunkPos to the list of voxel chunks that chunk owns
    @Unique
    private final Map<ChunkPos, List<Vector3ic>> vs$knownChunks = new HashMap<>();

    // Maps chunk pos to number of ticks we have considered unloading the chunk
    @Unique
    private final Long2LongOpenHashMap vs$chunksToUnload = new Long2LongOpenHashMap();

    // How many ticks we wait before unloading a chunk
    @Unique
    private static final long VS$CHUNK_UNLOAD_THRESHOLD = 100;

    // Set of chunk positions (as longs) that were recently force-loaded and need to be checked
    @Unique
    private final LongOpenHashSet vs$pendingForcedChunks = new LongOpenHashSet();

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
        vs$chunksToUnload.remove(worldChunk.getPos().toLong());
        if (!vs$knownChunks.containsKey(worldChunk.getPos())) {
            final List<Vector3ic> voxelChunkPositions = new ArrayList<>();

            final int chunkX = worldChunk.getPos().x;
            final int chunkZ = worldChunk.getPos().z;

            final LevelChunkSection[] chunkSections = worldChunk.getSections();

            // Cache ship lookup per-chunk instead of per-section
            final ServerLevel thisAsLevel = ServerLevel.class.cast(this);
            final LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(thisAsLevel, chunkX, chunkZ);
            final WingManager shipWingManager = ship != null ? ship.getWingManager() : null;

            for (int sectionY = 0; sectionY < chunkSections.length; sectionY++) {
                final LevelChunkSection chunkSection = chunkSections[sectionY];
                final Vector3ic chunkPos =
                    new Vector3i(chunkX, worldChunk.getSectionYFromSectionIndex(sectionY), chunkZ);

                if (chunkSection != null && !chunkSection.hasOnlyAir()) {
                    // Add this chunk to the ground rigid body
                    final VsiTerrainUpdate voxelShapeUpdate =
                        VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, chunkPos);
                    voxelShapeUpdates.add(voxelShapeUpdate);

                    // Detect wings — only scan blocks if this chunk belongs to a ship
                    if (shipWingManager != null) {
                        final MutableBlockPos mutableBlockPos = new MutableBlockPos();
                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    final BlockState blockState = chunkSection.getBlockState(x, y, z);
                                    if (blockState.getBlock() instanceof WingBlock) {
                                        final int posX = (chunkX << 4) + x;
                                        final int posY = worldChunk.getMinBuildHeight() + (sectionY << 4) + y;
                                        final int posZ = (chunkZ << 4) + z;
                                        mutableBlockPos.set(posX, posY, posZ);
                                        final Wing wing =
                                            ((WingBlock) blockState.getBlock()).getWing(thisAsLevel,
                                                mutableBlockPos, blockState);
                                        if (wing != null) {
                                            shipWingManager.setWing(shipWingManager.getFirstWingGroupId(),
                                                posX, posY, posZ, wing);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Send empty update so vs-core knows this section is loaded (air), not unloaded
                    final VsiTerrainUpdate emptyVoxelShapeUpdate = getVsCore()
                        .newEmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), true);
                    voxelShapeUpdates.add(emptyVoxelShapeUpdate);
                }
                voxelChunkPositions.add(chunkPos);
            }
            vs$knownChunks.put(worldChunk.getPos(), voxelChunkPositions);
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

        // Fast path: check ship chunks that were recently force-loaded via ChunkManagement.
        // Uses direct O(1) lookups instead of iterating ALL chunk holders.
        if (!vs$pendingForcedChunks.isEmpty()) {
            final var pendingIterator = vs$pendingForcedChunks.iterator();
            while (pendingIterator.hasNext()) {
                final long chunkPosLong = pendingIterator.nextLong();
                final ChunkPos pos = new ChunkPos(chunkPosLong);
                if (vs$knownChunks.containsKey(pos)) {
                    pendingIterator.remove();
                    continue;
                }
                final ChunkHolder chunkHolder = chunkMapAccessor.callGetVisibleChunkIfPresent(chunkPosLong);
                if (chunkHolder != null) {
                    // Ship chunks use a lightweight ticket (level 32 = ticking)
                    final Optional<LevelChunk> worldChunkOptional =
                        chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left();
                    if (worldChunkOptional.isPresent()) {
                        vs$loadChunk(worldChunkOptional.get(), voxelShapeUpdates);
                        pendingIterator.remove();
                    }
                }
            }
        }

        // Slow path: scan chunk holders for non-ship chunks (world terrain near players).
        // Skip chunks already known to VS to reduce work.
        for (final ChunkHolder chunkHolder : chunkMapAccessor.callGetChunks()) {
            final ChunkPos pos = chunkHolder.getPos();
            if (vs$knownChunks.containsKey(pos)) continue;
            final long posLong = pos.toLong();
            if (vs$pendingForcedChunks.contains(posLong)) continue;
            if (!distanceManagerAccessor.getTickets().containsKey(posLong)) continue;
            final Optional<LevelChunk> worldChunkOptional =
                chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left();
            if (worldChunkOptional.isPresent()) {
                vs$loadChunk(worldChunkOptional.get(), voxelShapeUpdates);
            }
        }

        final Iterator<Entry<ChunkPos, List<Vector3ic>>> knownChunkPosIterator = vs$knownChunks.entrySet().iterator();
        while (knownChunkPosIterator.hasNext()) {
            final Entry<ChunkPos, List<Vector3ic>> knownChunkPosEntry = knownChunkPosIterator.next();
            final long chunkPos = knownChunkPosEntry.getKey().toLong();
            // Unload chunks if they don't have tickets or if they're not in the visible chunks
            if ((!distanceManagerAccessor.getTickets().containsKey(chunkPos) || chunkMapAccessor.callGetVisibleChunkIfPresent(chunkPos) == null)) {
                final long ticksWaitingToUnload = vs$chunksToUnload.getOrDefault(chunkPos, 0L);
                if (ticksWaitingToUnload > VS$CHUNK_UNLOAD_THRESHOLD) {
                    // Unload this chunk
                    for (final Vector3ic unloadedChunk : knownChunkPosEntry.getValue()) {
                        final VsiTerrainUpdate deleteVoxelShapeUpdate =
                            getVsCore().newDeleteTerrainUpdate(unloadedChunk.x(), unloadedChunk.y(), unloadedChunk.z());
                        voxelShapeUpdates.add(deleteVoxelShapeUpdate);
                    }
                    knownChunkPosIterator.remove();
                    vs$chunksToUnload.remove(chunkPos);
                } else {
                    vs$chunksToUnload.put(chunkPos, ticksWaitingToUnload + 1);
                }
            }
        }

        // Send new loaded chunks updates to the ship world
        shipObjectWorld.addTerrainUpdates(
            VSGameUtilsKt.getDimensionId(self),
            voxelShapeUpdates
        );

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

    @Override
    public void addPendingForcedChunk(final int chunkX, final int chunkZ) {
        vs$pendingForcedChunks.add(ChunkPos.asLong(chunkX, chunkZ));
    }
}
