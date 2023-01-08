package org.valkyrienskies.mod.mixin.server.world;

import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.ServerLevelData;
import org.jetbrains.annotations.NotNull;
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
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore;
import org.valkyrienskies.core.apigame.world.chunks.TerrainUpdate;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.server.world.ChunkMapAccessor;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements IShipObjectWorldServerProvider {

    @Shadow
    @Final
    private ServerChunkCache chunkSource;

    @Shadow
    @NotNull
    public abstract MinecraftServer getServer();

    private final Set<Vector3ic> knownChunkRegions = new HashSet<>();

    @NotNull
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
        final List<ChunkHolder> loadedChunksList = Lists.newArrayList(
            ((ChunkMapAccessor) chunkSource.chunkMap).callGetChunks());

        // Create DenseVoxelShapeUpdate for new loaded chunks
        // Also mark the chunks as loaded in the ship objects
        final List<TerrainUpdate> voxelShapeUpdates = new ArrayList<>();
        final Set<Vector3ic> currentTickChunkRegions = new HashSet<>();

        for (final ChunkHolder chunkHolder : loadedChunksList) {
            final Optional<LevelChunk> worldChunkOptional =
                chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left();
            if (worldChunkOptional.isPresent()) {
                final LevelChunk worldChunk = worldChunkOptional.get();
                final int chunkX = worldChunk.getPos().x;
                final int chunkZ = worldChunk.getPos().z;

                final LevelChunkSection[] chunkSections = worldChunk.getSections();

                for (int sectionY = 0; sectionY < chunkSections.length; sectionY++) {
                    final LevelChunkSection chunkSection = chunkSections[sectionY];
                    final Vector3ic chunkPos =
                        new Vector3i(chunkX, worldChunk.getSectionYFromSectionIndex(sectionY), chunkZ);
                    currentTickChunkRegions.add(chunkPos);

                    if (!knownChunkRegions.contains(chunkPos)) {
                        if (chunkSection != null && !chunkSection.hasOnlyAir()) {
                            // Add this chunk to the ground rigid body
                            final TerrainUpdate voxelShapeUpdate =
                                VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, chunkPos);
                            voxelShapeUpdates.add(voxelShapeUpdate);
                        } else {
                            final TerrainUpdate emptyVoxelShapeUpdate = getVsCore()
                                .newEmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), true);
                            voxelShapeUpdates.add(emptyVoxelShapeUpdate);
                        }

                        knownChunkRegions.add(chunkPos);
                    }
                }
            }
        }

        final Iterator<Vector3ic> knownChunkPosIterator = knownChunkRegions.iterator();
        while (knownChunkPosIterator.hasNext()) {
            final Vector3ic knownChunkPos = knownChunkPosIterator.next();
            if (!currentTickChunkRegions.contains(knownChunkPos)) {
                // Delete this chunk
                final TerrainUpdate deleteVoxelShapeUpdate =
                    getVsCore().newDeleteTerrainUpdate(knownChunkPos.x(), knownChunkPos.y(), knownChunkPos.z());
                voxelShapeUpdates.add(deleteVoxelShapeUpdate);
                knownChunkPosIterator.remove();
            }
        }

        // Send new loaded chunks updates to the ship world
        shipObjectWorld.addTerrainUpdates(
            VSGameUtilsKt.getDimensionId(self),
            voxelShapeUpdates
        );
    }
    
}
