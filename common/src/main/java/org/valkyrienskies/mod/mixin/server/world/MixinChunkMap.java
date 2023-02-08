package org.valkyrienskies.mod.mixin.server.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.apigame.world.IPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.common.util.ShipSettingsKt;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    @Final
    private Supplier<DimensionDataStorage> overworldDataStorage;

    /**
     * Force the game to generate empty chunks in the shipyard.
     *
     * <p>If a chunk already exists do nothing. If it doesn't yet exist, but it's in the shipyard, then pretend that
     * chunk already existed and return a new chunk.
     *
     * @author Tri0de
     */
    @Inject(method = "readChunk", at = @At("HEAD"), cancellable = true)
    private void preReadChunk(final ChunkPos chunkPos, final CallbackInfoReturnable<CompoundTag> cir)
        throws IOException {
        final ChunkMap self = ChunkMap.class.cast(this);
        final CompoundTag compoundTag = self.read(chunkPos);
        final CompoundTag originalToReturn = compoundTag == null ? null :
            self.upgradeChunkTag(this.level.dimension(), this.overworldDataStorage, compoundTag, Optional.empty());

        cir.setReturnValue(originalToReturn);
        if (originalToReturn == null) {
            final ServerShip ship = VSGameUtilsKt.getShipManagingPos(level, chunkPos.x, chunkPos.z);
            if (ship != null && !ShipSettingsKt.getSettings(ship).getShouldGenerateChunks()) {
                // The chunk doesn't yet exist and is in the shipyard. Make a new empty chunk
                // Generate the chunk to be nothing
                final LevelChunk generatedChunk = new LevelChunk(level,
                    new ProtoChunk(chunkPos, UpgradeData.EMPTY, level,
                        level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), null), null);
                // Its wasteful to serialize just for this to be deserialized, but it will work for now.
                cir.setReturnValue(ChunkSerializer.write(level, generatedChunk));
            }
        }
    }

    /**
     * Force the game send chunk update packets to players watching ship chunks.
     *
     * @author Tri0de
     */
    @Inject(method = "getPlayers", at = @At("TAIL"), cancellable = true)
    private void postGetPlayersWatchingChunk(final ChunkPos chunkPos, final boolean onlyOnWatchDistanceEdge,
        final CallbackInfoReturnable<List<ServerPlayer>> cir) {

        final Iterator<IPlayer> playersWatchingShipChunk =
            VSGameUtilsKt.getShipObjectWorld(level)
                .getIPlayersWatchingShipChunk(chunkPos.x, chunkPos.z, VSGameUtilsKt.getDimensionId(level));

        if (!playersWatchingShipChunk.hasNext()) {
            // No players watching this ship chunk, so we don't need to modify anything
            return;
        }

        final List<ServerPlayer> oldReturnValue = cir.getReturnValue();
        final Set<ServerPlayer> watchingPlayers = new HashSet<>(oldReturnValue);

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

        cir.setReturnValue(new ArrayList<>(watchingPlayers));
    }

}
