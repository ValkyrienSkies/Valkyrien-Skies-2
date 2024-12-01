package org.valkyrienskies.mod.mixin.server.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.apigame.world.IPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipyardPosSavable;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;


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

    /**
     * Save mob's shipyard position when it gets unloaded
     *
     * @author G_Mungus
     */

    @Inject(method = "removeEntity", at = @At("HEAD"))
    protected void unloadEntityMixin(Entity entity, CallbackInfo info) {
        if (entity instanceof Mob mob) {
            Vector3d shipyardPos = valkyrienskies$getPosInShipyard(mob);
            if (shipyardPos != null &&
                VSGameUtilsKt.getShipManagingPos(this.level, shipyardPos) != null &&
                ((ShipyardPosSavable)mob).valkyrienskies$getUnloadedShipyardPos() == null) {

                ((ShipyardPosSavable)mob).valkyrienskies$setUnloadedShipyardPos(shipyardPos);
            }
        }
    }

    /**
     * Teleport mob to correct position on ship when loaded back in
     *
     * @author G_Mungus
     */

    @Inject(method = "addEntity", at = @At("RETURN"))
    protected void loadEntityMixin(Entity entity, CallbackInfo info) {
        if (entity instanceof Mob mob) {
            Vector3d shipyardPos = ((ShipyardPosSavable)mob).valkyrienskies$getUnloadedShipyardPos();
            if(shipyardPos != null) {
                mob.teleportTo(shipyardPos.x,
                    shipyardPos.y,
                    shipyardPos.z);

                ((ShipyardPosSavable) mob).valkyrienskies$setUnloadedShipyardPos(null);
            }
        }
    }


    /**
     * Helper method to get shipyard pos of mob on a ship
     * <p>
     * Implementation might look wierd but the way I originally had it didn't work while in the middle of chunk unloading
     *
     * @author G_Mungus
     */
    @Unique
    private Vector3d valkyrienskies$getPosInShipyard(final Entity entity) {
        List<Vector3d> vectors = VSGameUtilsKt.transformToNearbyShipsAndWorld(entity.level(), entity.getX(), entity.getY(), entity.getZ(), 1.0);

        for (Vector3d vec : vectors) {
            if (VSGameUtilsKt.isBlockInShipyard(entity.level(), VectorConversionsMCKt.toMinecraft(vec))) {
                return vec;
            }
        }
        return null;
    }

}
