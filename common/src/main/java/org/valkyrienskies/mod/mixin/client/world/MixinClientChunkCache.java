package org.valkyrienskies.mod.mixin.client.world;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
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
    volatile ClientChunkCache.Storage storage;
    @Shadow
    @Final
    ClientLevel level;

    public LongObjectMap<LevelChunk> vs$getShipChunks() {
        return vs$shipChunks;
    }

    @Unique
    private final LongObjectMap<LevelChunk> vs$shipChunks = new LongObjectHashMap<>();

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
    private void preLoadChunkFromPacket(final int x, final int z,
        final FriendlyByteBuf buf,
        final CompoundTag tag,
        final Consumer<BlockEntityTagOutput> consumer, final CallbackInfoReturnable<LevelChunk> cir) {
        final ClientChunkCacheStorageAccessor clientChunkMapAccessor =
            ClientChunkCacheStorageAccessor.class.cast(storage);
        if (!clientChunkMapAccessor.callInRange(x, z)) {
            if (VSGameUtilsKt.isChunkInShipyard(level, x, z)) {
                final long chunkPosLong = ChunkPos.asLong(x, z);

                final LevelChunk oldChunk = vs$shipChunks.get(chunkPosLong);
                final LevelChunk worldChunk;
                if (oldChunk != null) {
                    worldChunk = oldChunk;
                    oldChunk.replaceWithPacketData(buf, tag, consumer);
                } else {
                    worldChunk = new LevelChunk(this.level, new ChunkPos(x, z));
                    worldChunk.replaceWithPacketData(buf, tag, consumer);
                    vs$shipChunks.put(chunkPosLong, worldChunk);
                }

                this.level.onChunkLoaded(new ChunkPos(x, z));
                SodiumCompat.onChunkAdded(this.level, x, z);
                cir.setReturnValue(worldChunk);
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    public void preUnload(final int chunkX, final int chunkZ, final CallbackInfo ci) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) {
            vs$shipChunks.remove(ChunkPos.asLong(chunkX, chunkZ));
            if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() != VSRenderer.SODIUM) {
                ((IVSViewAreaMethods) ((LevelRendererAccessor) ((ClientLevelAccessor) level).getLevelRenderer()).getViewArea())
                    .unloadChunk(chunkX, chunkZ);
            }
            SodiumCompat.onChunkRemoved(this.level, chunkX, chunkZ);
            ci.cancel();
        }
    }

    @Inject(
        method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
        at = @At("HEAD"), cancellable = true)
    public void preGetChunk(final int chunkX, final int chunkZ, final ChunkStatus chunkStatus, final boolean bl,
        final CallbackInfoReturnable<LevelChunk> cir) {
        final LevelChunk shipChunk = vs$shipChunks.get(ChunkPos.asLong(chunkX, chunkZ));
        if (shipChunk != null) {
            cir.setReturnValue(shipChunk);
        }
    }
}
