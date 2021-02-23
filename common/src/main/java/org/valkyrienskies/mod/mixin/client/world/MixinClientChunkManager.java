package org.valkyrienskies.mod.mixin.client.world;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.biome.source.BiomeArray;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.VSGameUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The purpose of this mixin is to allow {@link ClientChunkManager} to store ship chunks.
 */
@Mixin(ClientChunkManager.class)
public abstract class MixinClientChunkManager {

    private final ConcurrentMap<Long, WorldChunk> shipChunksMap = new ConcurrentHashMap<>();

    @Shadow
    private volatile ClientChunkManager.ClientChunkMap chunks;
    @Shadow
    @Final
    private ClientWorld world;

    private final LongObjectMap<Chunk> shipChunks = new LongObjectHashMap<>();

    @Inject(method = "loadChunkFromPacket", at = @At("HEAD"), cancellable = true)
    private void preLoadChunkFromPacket(int x, int z, BiomeArray biomes, PacketByteBuf buf, CompoundTag tag, int verticalStripBitmask, boolean complete, CallbackInfoReturnable<WorldChunk> cir) {
        if (!this.chunks.isInRadius(x, z)) {
            if (VSGameUtils.INSTANCE.getShipObjectWorldFromWorld(world).getChunkAllocator().isChunkInShipyard(x, z)) {
                final long chunkPosLong = ChunkPos.toLong(x, z);

                WorldChunk worldChunk = new WorldChunk(this.world, new ChunkPos(x, z), biomes);
                worldChunk.loadFromPacket(biomes, buf, tag, verticalStripBitmask);
                shipChunks.put(chunkPosLong, worldChunk);

                ChunkSection[] chunkSections = worldChunk.getSectionArray();
                LightingProvider lightingProvider = this.getLightingProvider();
                lightingProvider.setColumnEnabled(new ChunkPos(x, z), true);

                for (int j = 0; j < chunkSections.length; ++j) {
                    ChunkSection chunkSection = chunkSections[j];
                    lightingProvider.setSectionStatus(ChunkSectionPos.from(x, j, z), ChunkSection.isEmpty(chunkSection));
                }

                this.world.resetChunkColor(x, z);
                cir.setReturnValue(worldChunk);
            }
        }
    }

    @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
    public void preUnload(int chunkX, int chunkZ, CallbackInfo ci) {
        shipChunksMap.remove(ChunkPos.toLong(chunkX, chunkZ));
        ci.cancel();
    }

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    public void preGetChunk(int chunkX, int chunkZ, ChunkStatus chunkStatus, boolean bl, CallbackInfoReturnable<WorldChunk> cir) {
        final WorldChunk shipChunk = shipChunksMap.get(ChunkPos.toLong(chunkX, chunkZ));
        if (shipChunk != null) cir.setReturnValue(shipChunk);
    }

    @Shadow
    public abstract LightingProvider getLightingProvider();
}
