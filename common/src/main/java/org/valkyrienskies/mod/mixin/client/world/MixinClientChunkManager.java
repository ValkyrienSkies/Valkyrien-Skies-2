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
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.mod.mixin.accessors.client.world.ClientChunkManagerClientChunkMapAccessor;

/**
 * The purpose of this mixin is to allow {@link ClientChunkManager} to store ship chunks.
 */
@Mixin(ClientChunkManager.class)
public abstract class MixinClientChunkManager {

    @Shadow
    private volatile ClientChunkManager.ClientChunkMap chunks;
    @Shadow
    @Final
    private ClientWorld world;

    private final LongObjectMap<WorldChunk> shipChunks = new LongObjectHashMap<>();

    @Inject(method = "loadChunkFromPacket", at = @At("HEAD"), cancellable = true)
    private void preLoadChunkFromPacket(final int x, final int z, final BiomeArray biomes, final PacketByteBuf buf,
        final CompoundTag tag,
        final int verticalStripBitmask, final boolean complete, final CallbackInfoReturnable<WorldChunk> cir) {
        final ClientChunkManagerClientChunkMapAccessor clientChunkMapAccessor =
            ClientChunkManagerClientChunkMapAccessor.class.cast(chunks);
        if (!clientChunkMapAccessor.callIsInRadius(x, z)) {
            if (ChunkAllocator.isChunkInShipyard(x, z)) {
                final long chunkPosLong = ChunkPos.toLong(x, z);

                final WorldChunk worldChunk = new WorldChunk(this.world, new ChunkPos(x, z), biomes);
                worldChunk.loadFromPacket(biomes, buf, tag, verticalStripBitmask);
                shipChunks.put(chunkPosLong, worldChunk);

                final ChunkSection[] chunkSections = worldChunk.getSectionArray();
                final LightingProvider lightingProvider = this.getLightingProvider();
                lightingProvider.setColumnEnabled(new ChunkPos(x, z), true);

                for (int j = 0; j < chunkSections.length; ++j) {
                    final ChunkSection chunkSection = chunkSections[j];
                    lightingProvider
                        .setSectionStatus(ChunkSectionPos.from(x, j, z), ChunkSection.isEmpty(chunkSection));
                }

                this.world.resetChunkColor(x, z);
                cir.setReturnValue(worldChunk);
            }
        }
    }

    @Inject(method = "unload", at = @At("HEAD"), cancellable = true)
    public void preUnload(final int chunkX, final int chunkZ, final CallbackInfo ci) {
        shipChunks.remove(ChunkPos.toLong(chunkX, chunkZ));
        ci.cancel();
    }

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    public void preGetChunk(final int chunkX, final int chunkZ, final ChunkStatus chunkStatus, final boolean bl,
        final CallbackInfoReturnable<WorldChunk> cir) {
        final WorldChunk shipChunk = shipChunks.get(ChunkPos.toLong(chunkX, chunkZ));
        if (shipChunk != null) {
            cir.setReturnValue(shipChunk);
        }
    }

    @Shadow
    public abstract LightingProvider getLightingProvider();
}
