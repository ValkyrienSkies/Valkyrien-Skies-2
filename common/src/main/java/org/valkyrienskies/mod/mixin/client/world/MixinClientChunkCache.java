package org.valkyrienskies.mod.mixin.client.world;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.mod.compat.SodiumCompat;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.multiplayer.ClientLevelAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.LevelRendererAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.world.ClientChunkCacheStorageAccessor;
import org.valkyrienskies.mod.mixinducks.client.render.IVSViewAreaMethods;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;

/**
 * The purpose of this mixin is to allow {@link ClientChunkCache} to store ship chunks.
 */
@Mixin(ClientChunkCache.class)
public abstract class MixinClientChunkCache implements ClientChunkCacheDuck {

    @Shadow
    private volatile ClientChunkCache.Storage storage;
    @Shadow
    @Final
    private ClientLevel level;

    public LongObjectMap<LevelChunk> vs_getShipChunks() {
        return shipChunks;
    }

    private final LongObjectMap<LevelChunk> shipChunks = new LongObjectHashMap<>();

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void preLoadChunkFromPacket(final int x, final int z,
        final FriendlyByteBuf buf,
        final CompoundTag tag,
        final Consumer<BlockEntityTagOutput> consumer, final CallbackInfoReturnable<LevelChunk> cir) {
        final ClientChunkCacheStorageAccessor clientChunkMapAccessor =
            ClientChunkCacheStorageAccessor.class.cast(storage);
        if (!clientChunkMapAccessor.callInRange(x, z)) {
            if (ChunkAllocator.isChunkInShipyard(x, z)) {
                final long chunkPosLong = ChunkPos.asLong(x, z);

                final LevelChunk worldChunk = new LevelChunk(this.level, new ChunkPos(x, z));
                worldChunk.replaceWithPacketData(buf, tag, consumer);
                shipChunks.put(chunkPosLong, worldChunk);

                final LevelChunkSection[] chunkSections = worldChunk.getSections();
                final LevelLightEngine lightingProvider = this.getLightEngine();
                lightingProvider.enableLightSources(new ChunkPos(x, z), true);

                for (int j = 0; j < chunkSections.length; ++j) {
                    final LevelChunkSection chunkSection = chunkSections[j];
                    lightingProvider
                        .updateSectionStatus(SectionPos.of(x, j, z), chunkSection.hasOnlyAir());
                }

                this.level.onChunkLoaded(new ChunkPos(x, z));

                SodiumCompat.onChunkAdded(x, z);

                cir.setReturnValue(worldChunk);
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    public void preUnload(final int chunkX, final int chunkZ, final CallbackInfo ci) {
        shipChunks.remove(ChunkPos.asLong(chunkX, chunkZ));
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() != VSRenderer.SODIUM) {
            ((IVSViewAreaMethods) ((LevelRendererAccessor) ((ClientLevelAccessor) level).getLevelRenderer()).getViewArea())
                .unloadChunk(chunkX, chunkZ);
        }
        SodiumCompat.onChunkRemoved(chunkX, chunkZ);
        ci.cancel();
    }

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    public void preGetChunk(final int chunkX, final int chunkZ, final ChunkStatus chunkStatus, final boolean bl,
        final CallbackInfoReturnable<LevelChunk> cir) {
        final LevelChunk shipChunk = shipChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (shipChunk != null) {
            cir.setReturnValue(shipChunk);
        }
    }

    @Shadow
    public abstract LevelLightEngine getLightEngine();
}
