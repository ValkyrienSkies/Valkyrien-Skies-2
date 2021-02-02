package net.examplemod.mixin.server.level;

import net.examplemod.IShipObjectWorldProvider;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.biome.Biomes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    private static final Biome[] BIOMES;

    static {
        // Make all ship chunks have the plains biome
        BIOMES = Util.make(new Biome[ChunkBiomeContainer.BIOMES_SIZE], (biomes) -> Arrays.fill(biomes, Biomes.PLAINS));
    }

    @Shadow @Final
    private ServerLevel level;
    @Shadow @Final
    private Supplier<DimensionDataStorage> overworldDataStorage;

    private final ChunkMap thisAsChunkMap = ChunkMap.class.cast(this);

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
    private CompoundTag readChunk(ChunkPos chunkPos) throws IOException {
        CompoundTag compoundTag = thisAsChunkMap.read(chunkPos);
        final CompoundTag originalToReturn = compoundTag == null ? null : thisAsChunkMap.upgradeChunkTag(this.level.dimension(), this.overworldDataStorage, compoundTag);

        if (originalToReturn == null) {
            final IShipObjectWorldProvider shipObjectWorldProvider = (IShipObjectWorldProvider) level;
            if (shipObjectWorldProvider.getShipObjectWorld().getChunkAllocator().isChunkInShipyard(chunkPos.x, chunkPos.z)) {
                // The chunk doesn't yet exist and is in the shipyard. Make a new empty chunk
                // Generate the chunk to be nothing
                final LevelChunk generatedChunk = new LevelChunk(level, chunkPos, new ChunkBiomeContainer(level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), BIOMES));
                // Its wasteful to serialize just for this to be deserialized, but it will work for now.
                return ChunkSerializer.write(level, generatedChunk);
            }
        }

        return originalToReturn;
    }
}
