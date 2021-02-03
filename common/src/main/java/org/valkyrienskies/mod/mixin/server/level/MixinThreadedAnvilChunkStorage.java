package org.valkyrienskies.mod.mixin.server.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BuiltinBiomes;
import net.minecraft.world.biome.source.BiomeArray;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.IShipObjectWorldProvider;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinThreadedAnvilChunkStorage {

    private static final Biome[] BIOMES;

    static {
        // Make all ship chunks have the plains biome
        BIOMES = Util.make(new Biome[BiomeArray.DEFAULT_LENGTH], (biomes) -> Arrays.fill(biomes, BuiltinBiomes.PLAINS));
    }

    @Shadow @Final
    private ServerWorld world;

    @Shadow @Final
    private Supplier<PersistentStateManager> persistentStateManagerFactory;

    private final ThreadedAnvilChunkStorage thisAsChunkMap = ThreadedAnvilChunkStorage.class.cast(this);

    /**
     * Force the game to generate empty chunks in the shipyard.
     *
     * If a chunk already exists do nothing. If it doesn't yet exist but its in the ship yard, then pretend that chunk
     * already existed and return a new chunk.
     *
     * @reason An injector would be safer to use, but it doesn't seem to work properly unless I use an @Overwrite.
     * @author Tri0de
     */
    @Overwrite
    @Nullable
    private CompoundTag getUpdatedChunkTag(ChunkPos chunkPos) throws IOException {
        CompoundTag compoundTag = thisAsChunkMap.getNbt(chunkPos);
        final CompoundTag originalToReturn = compoundTag == null ? null :
            thisAsChunkMap.updateChunkTag(this.world.getRegistryKey(), this.persistentStateManagerFactory, compoundTag);

        if (originalToReturn == null) {
            final IShipObjectWorldProvider shipObjectWorldProvider = (IShipObjectWorldProvider) world;
            if (shipObjectWorldProvider.getShipObjectWorld().getChunkAllocator().isChunkInShipyard(chunkPos.x, chunkPos.z)) {
                // The chunk doesn't yet exist and is in the shipyard. Make a new empty chunk
                // Generate the chunk to be nothing
                final WorldChunk generatedChunk = new WorldChunk(world, chunkPos, new BiomeArray(world.getRegistryManager().get(Registry.BIOME_KEY), BIOMES));
                // Its wasteful to serialize just for this to be deserialized, but it will work for now.
                return ChunkSerializer.serialize(world, generatedChunk);
            }
        }

        return originalToReturn;
    }
}
