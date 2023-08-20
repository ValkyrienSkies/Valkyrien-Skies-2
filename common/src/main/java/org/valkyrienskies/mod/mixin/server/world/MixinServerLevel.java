package org.valkyrienskies.mod.mixin.server.world;

import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.ServerLevelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Wing;
import org.valkyrienskies.core.api.ships.WingManager;
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore;
import org.valkyrienskies.core.apigame.world.chunks.TerrainUpdate;
import org.valkyrienskies.core.impl.game.ships.ConnectivityForest;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.block.WingBlock;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.server.world.ChunkMapAccessor;
import org.valkyrienskies.mod.util.ShipSplitter;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements IShipObjectWorldServerProvider {

    @Shadow
    @Final
    private ServerChunkCache chunkSource;

    @Shadow
    @NotNull
    public abstract MinecraftServer getServer();

    // Map from ChunkPos to the list of voxel chunks that chunk owns
    private final Map<ChunkPos, List<Vector3ic>> knownChunks = new HashMap<>();

    @Nullable
    @Override
    public ServerShipWorldCore getShipObjectWorld() {
        return ((IShipObjectWorldServerProvider) getServer()).getShipObjectWorld();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    void onInit(final MinecraftServer minecraftServer, final Executor executor,
        final LevelStorageAccess levelStorageAccess,
        final ServerLevelData serverLevelData, final ResourceKey levelId, final Holder holder,
        final ChunkProgressListener chunkProgressListener, final ChunkGenerator chunkGenerator, final boolean bl,
        final long l, final List list,
        final boolean bl2, final CallbackInfo ci) {

        // This only happens when overworld gets loaded on startup, we have a mixin in MixinMinecraftServer for this specific case
        if (getShipObjectWorld() != null) {
            getShipObjectWorld().addDimension(VSGameUtilsKt.getDimensionId((ServerLevel) (Object) this),
                VSGameUtilsKt.getYRange((ServerLevel) (Object) this));
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
        final LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(
            self, (int) particle.x() >> 4, (int) particle.z() >> 4);

        if (ship == null) {
            // vanilla behaviour
            return closerToCenterThan.call(player, particle, distance);
        }

        // in-world position
        final Vector3d posInWorld = ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(particle));

        return posInWorld.distanceSquared(player.getX(), player.getY(), player.getZ()) < distance * distance;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        final ServerLevel self = ServerLevel.class.cast(this);
        final ServerShipWorldCore shipObjectWorld = VSGameUtilsKt.getShipObjectWorld(self);
        // Find newly loaded chunks
        final ChunkMapAccessor chunkMapAccessor = (ChunkMapAccessor) chunkSource.chunkMap;

        // Create DenseVoxelShapeUpdate for new loaded chunks
        // Also mark the chunks as loaded in the ship objects
        final List<TerrainUpdate> voxelShapeUpdates = new ArrayList<>();

        for (final ChunkHolder chunkHolder : chunkMapAccessor.callGetChunks()) {
            final Optional<LevelChunk> worldChunkOptional =
                chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left();
            if (worldChunkOptional.isPresent()) {
                final LevelChunk worldChunk = worldChunkOptional.get();
                if (!knownChunks.containsKey(worldChunk.getPos())) {
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
                            final TerrainUpdate voxelShapeUpdate =
                                VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, chunkPos);
                            voxelShapeUpdates.add(voxelShapeUpdate);

                            // region Detect wings
                            final ServerLevel thisAsLevel = ServerLevel.class.cast(this);
                            final LoadedServerShip
                                ship = VSGameUtilsKt.getShipObjectManagingPos(thisAsLevel, chunkX, chunkZ);
                            if (ship != null) {
                                // Sussy cast, but I don't want to expose this directly through the vs-core api
                                final WingManager shipAsWingManager = ship.getAttachment(WingManager.class);
                                final MutableBlockPos mutableBlockPos = new MutableBlockPos();
                                final ConnectivityForest shipAsConnectivityForest = ship.getAttachment(ConnectivityForest.class);
//                                final AirPocketForest shipAsAirPocketForest = ship.getAttachment(AirPocketForest.class);
                                for (int x = 0; x < 16; x++) {
                                    for (int y = 0; y < 16; y++) {
                                        for (int z = 0; z < 16; z++) {
                                            final BlockState blockState = chunkSection.getBlockState(x, y, z);
                                            final int posX = (chunkX << 4) + x;
                                            final int posY = chunkSection.bottomBlockY() + y;
                                            final int posZ = (chunkZ << 4) + z;
                                            if (!blockState.isAir()) {
                                                shipAsConnectivityForest.newVertex(posX, posY, posZ);
                                            } else {
//                                                if (ship.getShipAABB().containsPoint(posX, posY, posZ)) {
//                                                    shipAsAirPocketForest.newVertex(posX, posY, posZ, false);
//                                                }
                                            }
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
                                shipAsConnectivityForest.getGraph().optimize();
//                                shipAsAirPocketForest.setShouldUpdateOutsideAir(true);
//                                shipAsAirPocketForest.getGraph().optimize();
                                shipAsConnectivityForest.verifyIntactOnLoad();
                            }
                            // endregion
                        } else {
                            final TerrainUpdate emptyVoxelShapeUpdate = getVsCore()
                                .newEmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), true);
                            voxelShapeUpdates.add(emptyVoxelShapeUpdate);
                        }
                    }
                    knownChunks.put(worldChunk.getPos(), voxelChunkPositions);
                }
            }
        }

        final Iterator<Entry<ChunkPos, List<Vector3ic>>> knownChunkPosIterator = knownChunks.entrySet().iterator();
        while (knownChunkPosIterator.hasNext()) {
            final Entry<ChunkPos, List<Vector3ic>> knownChunkPosEntry = knownChunkPosIterator.next();
            if (chunkMapAccessor.callGetVisibleChunkIfPresent(knownChunkPosEntry.getKey().toLong()) == null) {
                // Delete this chunk
                for (final Vector3ic unloadedChunk : knownChunkPosEntry.getValue()) {
                    final TerrainUpdate deleteVoxelShapeUpdate =
                        getVsCore().newDeleteTerrainUpdate(unloadedChunk.x(), unloadedChunk.y(), unloadedChunk.z());
                    voxelShapeUpdates.add(deleteVoxelShapeUpdate);
                }
                knownChunkPosIterator.remove();
            }
        }

        ShipSplitter.INSTANCE.splitShips(shipObjectWorld, self);
        ShipSplitter.INSTANCE.airHandle(shipObjectWorld, self);

        // todo: Better check, I think this is pretty inefficient...


        // Send new loaded chunks updates to the ship world
        shipObjectWorld.addTerrainUpdates(
            VSGameUtilsKt.getDimensionId(self),
            voxelShapeUpdates
        );
    }

}
