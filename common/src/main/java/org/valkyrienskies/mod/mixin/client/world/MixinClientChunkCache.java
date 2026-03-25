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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.ChunkPos;
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

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void preLoadChunkFromPacket(final int x, final int z,
        final FriendlyByteBuf buf,
        final CompoundTag tag,
        final Consumer<BlockEntityTagOutput> consumer,
        final CallbackInfoReturnable<LevelChunk> cir
    ) {
        if (VSGameUtilsKt.isChunkInShipyard(level, x, z)) {
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
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    public void preUnload(final int chunkX, final int chunkZ, final CallbackInfo ci) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) {
            LevelChunk worldChunk = vs$shipChunks.remove(ChunkPos.asLong(chunkX, chunkZ));
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
        final LevelChunk shipChunk = vs$shipChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (shipChunk != null) {
            cir.setReturnValue(shipChunk);
        }
    }
}
