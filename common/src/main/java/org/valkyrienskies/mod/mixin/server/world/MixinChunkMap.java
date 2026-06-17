package org.valkyrienskies.mod.mixin.server.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkMap.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.internal.world.VsiPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;

//This should trump Very Many Players, which is set to 1050
@Mixin(value = ChunkMap.class, priority = 1100)
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
    /*
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
            // If its in a ship and it shouldn't generate chunks OR if there is no ship but its happening in the shipyard
            if ((ship == null && VSGameUtilsKt.isChunkInShipyard(level, chunkPos.x, chunkPos.z)) ||
                (ship != null && !ShipSettingsKt.getSettings(ship).getShouldGenerateChunks())) {
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
     */

    /**
     * Force the game send chunk update packets to players watching ship chunks.
     *
     * @author Tri0de
     */
    @Inject(method = "getPlayers", at = @At("RETURN"), cancellable = true)
    private void postGetPlayersWatchingChunk(final ChunkPos chunkPos, final boolean onlyOnWatchDistanceEdge,
        final CallbackInfoReturnable<List<ServerPlayer>> cir) {

        final Iterator<VsiPlayer> playersWatchingShipChunk =
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
                if (!(iPlayer instanceof MinecraftPlayer minecraftPlayer)) return;
                final ServerPlayer playerEntity =
                    (ServerPlayer) minecraftPlayer.getPlayerEntityReference().get();
                if (playerEntity != null) {
                    watchingPlayers.add(playerEntity);
                }
            }
        );

        cir.setReturnValue(new ArrayList<>(watchingPlayers));
    }

    @WrapOperation(method = "anyPlayerCloseEnoughForSpawning", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;hasPlayersNearby(J)Z"))
    private boolean onHasPlayersNearby(
        DistanceManager instance, long l, Operation<Boolean> original, @Local(argsOnly = true) ChunkPos arg) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, arg);
        if (ship == null) {
            return original.call(instance, l);
        }

        return original.call(instance, new ChunkPos(BlockPos.containing(
            VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.toWorldCoordinates(ship, arg.getMiddleBlockPosition(63)))
        )).toLong());
    }

    @WrapOperation(method = "playerIsCloseEnoughForSpawning", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;euclideanDistanceSquared(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/entity/Entity;)D"))
    private double onEuclideanDistanceSquared(ChunkPos d0, Entity d1, Operation<Double> original) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, d0);
        if (ship == null) {
            return original.call(d0, d1);
        }

        return original.call(new ChunkPos(BlockPos.containing(
            VectorConversionsMCKt.toMinecraft(VSGameUtilsKt.toWorldCoordinates(ship, d0.getMiddleBlockPosition(63)))
        )), d1);
    }

    /**
     * Only save ship chunks that actually have something in them to avoid massive lag when closing/saving the game.
     *
     * <p>Previously this gated on {@code ship.getActiveChunksSet().contains(pos)}
     * but that set is populated asynchronously by the connectivity-update
     * executeIf in {@code ShipAssembler.batchAssembleToShips} phase 4. Closing
     * the world immediately after a spawn (before that executeIf fires)
     * would hit preSave with an empty activeChunksSet, bail, and lose the
     * ship's blocks on reload. Switch to a direct "has any non-air section"
     * check so freshly-spawned ships still save correctly.
     *
     * <p>Important: we MUST call {@code chunkAccess.setUnsaved(false)} before
     * cancelling the save. Vanilla's {@code ChunkMap.save} marks the chunk
     * clean as its very first side-effect (after the {@code isUnsaved()}
     * check). If we short-circuit at HEAD without doing the same, the chunk
     * stays dirty, so {@code ChunkHolder.isReadyForSaving()} keeps returning
     * false, and during {@code stopServer()}'s chunk-unload loop
     * ({@code ChunkMap.processUnloads} → {@code scheduleUnload}) the chunk
     * keeps being requeued — the server thread spins at 100% CPU in
     * {@code thenRunAsync → scheduleUnload} and never exits runServer().
     * Observed as a 200s+ server-shutdown hang blocking {@code halt(true)}.
     */
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void preSave(ChunkAccess chunkAccess, CallbackInfoReturnable<Boolean> cir) {
        final ChunkPos pos = chunkAccess.getPos();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) return;

        for (LevelChunkSection section : chunkAccess.getSections()) {
            if (section != null && !section.hasOnlyAir()) {
                return; // chunk has content — let vanilla save run
            }
        }
        chunkAccess.setUnsaved(false);
        cir.setReturnValue(false);
    }
}
