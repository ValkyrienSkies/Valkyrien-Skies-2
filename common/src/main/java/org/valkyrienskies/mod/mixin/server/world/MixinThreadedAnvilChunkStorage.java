package org.valkyrienskies.mod.mixin.server.world;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.ServerPlayerEntity;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.game.IPlayer;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;

@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class MixinThreadedAnvilChunkStorage {

    private static final Biome[] BIOMES;

    static {
        // Make all ship chunks have the plains biome
        BIOMES = Util.make(new Biome[BiomeArray.DEFAULT_LENGTH], (biomes) -> Arrays.fill(biomes, BuiltinBiomes.PLAINS));
    }

    @Shadow
    @Final
    private ServerWorld world;

    @Shadow
    @Final
    private Supplier<PersistentStateManager> persistentStateManagerFactory;


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
    private CompoundTag getUpdatedChunkTag(final ChunkPos chunkPos) throws IOException {
        final ThreadedAnvilChunkStorage self = ThreadedAnvilChunkStorage.class.cast(this);
        final CompoundTag compoundTag = self.getNbt(chunkPos);
        final CompoundTag originalToReturn = compoundTag == null ? null :
            self.updateChunkTag(this.world.getRegistryKey(), this.persistentStateManagerFactory, compoundTag);

        if (originalToReturn == null) {
            final IShipObjectWorldProvider shipObjectWorldProvider = (IShipObjectWorldProvider) world;
            if (shipObjectWorldProvider.getShipObjectWorld().getChunkAllocator()
                .isChunkInShipyard(chunkPos.x, chunkPos.z)) {
                // The chunk doesn't yet exist and is in the shipyard. Make a new empty chunk
                // Generate the chunk to be nothing
                final WorldChunk generatedChunk = new WorldChunk(world, chunkPos,
                    new BiomeArray(world.getRegistryManager().get(Registry.BIOME_KEY), BIOMES));
                // Its wasteful to serialize just for this to be deserialized, but it will work for now.
                return ChunkSerializer.serialize(world, generatedChunk);
            }
        }

        return originalToReturn;
    }

    /**
     * Force the game send chunk update packets to players watching ship chunks.
     *
     * @author Tri0de
     */
    @Inject(method = "getPlayersWatchingChunk", at = @At("TAIL"), cancellable = true)
    private void postGetPlayersWatchingChunk(final ChunkPos chunkPos, final boolean onlyOnWatchDistanceEdge,
        final CallbackInfoReturnable<Stream<ServerPlayerEntity>> cir) {

        final Iterator<IPlayer> playersWatchingShipChunk =
            VSGameUtilsKt.getShipObjectWorld(world).getIPlayersWatchingShipChunk(chunkPos.x, chunkPos.z);

        if (!playersWatchingShipChunk.hasNext()) {
            // No players watching this ship chunk, so we don't need to modify anything
            return;
        }

        final Stream<ServerPlayerEntity> oldReturnValue = cir.getReturnValue();
        final Set<ServerPlayerEntity> watchingPlayers = new HashSet<>();
        oldReturnValue.forEach(watchingPlayers::add);

        playersWatchingShipChunk.forEachRemaining(
            iPlayer -> {
                final MinecraftPlayer minecraftPlayer = (MinecraftPlayer) iPlayer;
                final ServerPlayerEntity playerEntity =
                    (ServerPlayerEntity) minecraftPlayer.getPlayerEntityReference().get();
                if (playerEntity != null) {
                    watchingPlayers.add(playerEntity);
                }
            }
        );

        final Stream<ServerPlayerEntity> newReturnValue = watchingPlayers.stream();
        cir.setReturnValue(newReturnValue);
    }
}
