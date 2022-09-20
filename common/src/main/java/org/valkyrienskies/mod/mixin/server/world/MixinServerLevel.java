package org.valkyrienskies.mod.mixin.server.world;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.core.game.ships.ShipData;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.server.world.ChunkMapAccessor;
import org.valkyrienskies.physics_api.voxel_updates.DeleteVoxelShapeUpdate;
import org.valkyrienskies.physics_api.voxel_updates.DenseVoxelShapeUpdate;
import org.valkyrienskies.physics_api.voxel_updates.EmptyVoxelShapeUpdate;
import org.valkyrienskies.physics_api.voxel_updates.IVoxelShapeUpdate;

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
    public ShipObjectServerWorld getShipObjectWorld() {
        return ((IShipObjectWorldServerProvider) getServer()).getShipObjectWorld();
    }

    /**
     * Include ships in particle distance check. Seems to only be used by /particle
     */
    @Redirect(
        method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean includeShipsInParticleDistanceCheck(
        final BlockPos player, final Position particle, final double distance) {

        final ServerLevel self = ServerLevel.class.cast(this);
        final ShipObject ship = VSGameUtilsKt.getShipObjectManagingPos(
            self, (int) particle.x() >> 4, (int) particle.z() >> 4);

        if (ship == null) {
            // vanilla behaviour
            return player.closerThan(particle, distance);
        }

        // in-world position
        final Vector3d posInWorld = ship.getShipData().getShipTransform().getShipToWorldMatrix()
            .transformPosition(VectorConversionsMCKt.toJOML(particle));

        return posInWorld.distanceSquared(player.getX(), player.getY(), player.getZ()) < distance * distance;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        final ServerLevel self = ServerLevel.class.cast(this);
        final ShipObjectServerWorld shipObjectWorld = VSGameUtilsKt.getShipObjectWorld(self);
        // Find newly loaded chunks
        final List<ChunkHolder> loadedChunksList = Lists.newArrayList(
            ((ChunkMapAccessor) chunkSource.chunkMap).callGetChunks());

        // Create DenseVoxelShapeUpdate for new loaded chunks
        // Also mark the chunks as loaded in the ship objects
        final List<IVoxelShapeUpdate> voxelShapeUpdates = new ArrayList<>();
        final Set<Vector3ic> currentTickChunkRegions = new HashSet<>();

        for (final ChunkHolder chunkHolder : loadedChunksList) {
            final Optional<LevelChunk> worldChunkOptional =
                chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left();
            if (worldChunkOptional.isPresent()) {
                final LevelChunk worldChunk = worldChunkOptional.get();
                final int chunkX = worldChunk.getPos().x;
                final int chunkZ = worldChunk.getPos().z;

                final LevelChunkSection[] chunkSections = worldChunk.getSections();

                final ShipData shipData =
                    shipObjectWorld.getQueryableShipData()
                        .getShipDataFromChunkPos(chunkX, chunkZ, VSGameUtilsKt.getDimensionId(self));

                if (shipData != null) {
                    // Tell the ship data that the chunk has been loaded
                    shipData.onLoadChunk(chunkX, chunkZ);
                }

                // For now just assume chunkY goes from 0 to 16
                for (int chunkY = 0; chunkY < 16; chunkY++) {
                    final LevelChunkSection chunkSection = chunkSections[chunkY];
                    final Vector3ic chunkPos = new Vector3i(chunkX, chunkY, chunkZ);
                    currentTickChunkRegions.add(chunkPos);

                    if (!knownChunkRegions.contains(chunkPos)) {
                        if (chunkSection != null && !chunkSection.isEmpty()) {
                            // Add this chunk to the ground rigid body
                            final DenseVoxelShapeUpdate voxelShapeUpdate =
                                VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, chunkPos);
                            voxelShapeUpdates.add(voxelShapeUpdate);

                            if (shipData != null) {
                                VSGameUtilsKt.addChunkBlocksToShipVoxelAABB(chunkSection, chunkPos, shipData);
                            }
                        } else {
                            final EmptyVoxelShapeUpdate emptyVoxelShapeUpdate =
                                new EmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), false, true);
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
                final DeleteVoxelShapeUpdate deleteVoxelShapeUpdate =
                    new DeleteVoxelShapeUpdate(knownChunkPos.x(), knownChunkPos.y(), knownChunkPos.z(), false);
                voxelShapeUpdates.add(deleteVoxelShapeUpdate);
                knownChunkPosIterator.remove();
            }
        }

        // Send new loaded chunks updates to the ship world
        shipObjectWorld.addVoxelShapeUpdates(
            VSGameUtilsKt.getDimensionId(self),
            voxelShapeUpdates
        );
    }

    /**
     * @author ewoudje
     * @reason send updates to relevant VSEntityHandler
     */
    @Inject(method = "addEntity", at = @At("TAIL"))
    private void updateHandler(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        final Vector3d pos = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
        final Ship ship = VSGameUtilsKt.getShipObjectManagingPos(entity.level, pos);
        if (ship != null) {
            VSEntityManager.INSTANCE.getHandler(entity.getType())
                .freshEntityInShipyard(entity, ship, pos);
        }
    }
}
