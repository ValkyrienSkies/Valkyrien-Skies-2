package org.valkyrienskies.mod.mixin.server.world;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.biome.Biomes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.IPlayer;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    private static final Biome[] BIOMES;

    static {
        // Make all ship chunks have the plains biome
        BIOMES = Util.make(new Biome[ChunkBiomeContainer.BIOMES_SIZE], (biomes) -> Arrays.fill(biomes, Biomes.PLAINS));
    }

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Supplier<DimensionDataStorage> overworldDataStorage;

    /**
     * Force the game to generate empty chunks in the shipyard.
     *
     * <p>If a chunk already exists do nothing. If it doesn't yet exist but its in the ship yard, then pretend that
     * chunk already existed and return a new chunk.
     *
     * @reason An injector would be safer to use, but it doesn't seem to work properly unless I use an @Overwrite.
     * @author Tri0de
     */
    @Overwrite
    @Nullable
    private CompoundTag readChunk(final ChunkPos chunkPos) throws IOException {
        final ChunkMap self = ChunkMap.class.cast(this);
        final CompoundTag compoundTag = self.read(chunkPos);
        final CompoundTag originalToReturn = compoundTag == null ? null :
            self.upgradeChunkTag(this.level.dimension(), this.overworldDataStorage, compoundTag);

        if (originalToReturn == null) {
            final IShipObjectWorldProvider shipObjectWorldProvider = (IShipObjectWorldProvider) level;
            if (ChunkAllocator.isChunkInShipyard(chunkPos.x, chunkPos.z)) {
                // The chunk doesn't yet exist and is in the shipyard. Make a new empty chunk
                // Generate the chunk to be nothing
                final LevelChunk generatedChunk = new LevelChunk(level, chunkPos,
                    new ChunkBiomeContainer(level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), BIOMES));
                // Its wasteful to serialize just for this to be deserialized, but it will work for now.
                return ChunkSerializer.write(level, generatedChunk);
            }
        }

        return originalToReturn;
    }

    /**
     * Force the game send chunk update packets to players watching ship chunks.
     *
     * @author Tri0de
     */
    @Inject(method = "getPlayers", at = @At("TAIL"), cancellable = true)
    private void postGetPlayersWatchingChunk(final ChunkPos chunkPos, final boolean onlyOnWatchDistanceEdge,
        final CallbackInfoReturnable<Stream<ServerPlayer>> cir) {

        final Iterator<IPlayer> playersWatchingShipChunk =
            VSGameUtilsKt.getShipObjectWorld(level)
                .getIPlayersWatchingShipChunk(chunkPos.x, chunkPos.z, VSGameUtilsKt.getDimensionId(level));

        if (!playersWatchingShipChunk.hasNext()) {
            // No players watching this ship chunk, so we don't need to modify anything
            return;
        }

        final Stream<ServerPlayer> oldReturnValue = cir.getReturnValue();
        final Set<ServerPlayer> watchingPlayers = new HashSet<>();
        oldReturnValue.forEach(watchingPlayers::add);

        playersWatchingShipChunk.forEachRemaining(
            iPlayer -> {
                final MinecraftPlayer minecraftPlayer = (MinecraftPlayer) iPlayer;
                final ServerPlayer playerEntity =
                    (ServerPlayer) minecraftPlayer.getPlayerEntityReference().get();
                if (playerEntity != null) {
                    watchingPlayers.add(playerEntity);
                }
            }
        );

        final Stream<ServerPlayer> newReturnValue = watchingPlayers.stream();
        cir.setReturnValue(newReturnValue);
    }
}
