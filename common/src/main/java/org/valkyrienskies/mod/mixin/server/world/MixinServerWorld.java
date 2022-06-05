package org.valkyrienskies.mod.mixin.server.world;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import kotlin.Pair;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Position;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.chunk_tracking.ChunkUnwatchTask;
import org.valkyrienskies.core.chunk_tracking.ChunkWatchTask;
import org.valkyrienskies.core.game.IPlayer;
import org.valkyrienskies.core.game.ships.ShipData;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld;
import org.valkyrienskies.core.networking.IVSPacket;
import org.valkyrienskies.core.networking.impl.VSPacketShipDataList;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.ShipSavedData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.VSNetworking;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.server.world.ThreadedAnvilChunkStorageAccessor;
import org.valkyrienskies.physics_api.voxel_updates.DenseVoxelShapeUpdate;
import org.valkyrienskies.physics_api.voxel_updates.EmptyVoxelShapeUpdate;
import org.valkyrienskies.physics_api.voxel_updates.IVoxelShapeUpdate;

@Mixin(ServerWorld.class)
public abstract class MixinServerWorld implements IShipObjectWorldServerProvider {

    @Shadow
    public abstract PersistentStateManager getPersistentStateManager();

    @Shadow
    @Final
    private List<ServerPlayerEntity> players;

    @Shadow
    @Final
    private ServerChunkManager serverChunkManager;

    private ShipObjectServerWorld shipObjectWorld = null;
    private ShipSavedData shipSavedData = null;
    private final Map<UUID, MinecraftPlayer> vsPlayerWrappers = new HashMap<>();

    private final HashSet<Vector3ic> knownChunkRegions = new HashSet<>();

    @Unique
    private int getDimensionId() {
        final World thisAsWorld = World.class.cast(this);
        return thisAsWorld.getDimension().hashCode(); // TODO: This isn't ideal, but it should work for now
    }

    @Inject(
        at = @At("TAIL"),
        method = "<init>"
    )
    private void postConstructor(final CallbackInfo info) {
        // Load ship data from the world storage
        shipSavedData = getPersistentStateManager()
            .getOrCreate(ShipSavedData.Companion::createEmpty, ShipSavedData.SAVED_DATA_ID);
        // Make a ship world using the loaded ship data
        shipObjectWorld =
            new ShipObjectServerWorld(shipSavedData.getQueryableShipData(), shipSavedData.getChunkAllocator(),
                getDimensionId());
    }

    @NotNull
    @Override
    public ShipObjectServerWorld getShipObjectWorld() {
        return shipObjectWorld;
    }

    /**
     * Include ships in particle distance check. Seems to only be used by /particle
     */
    @Redirect(
        method = "sendToPlayerIfNearby",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/math/BlockPos;isWithinDistance(Lnet/minecraft/util/math/Position;D)Z"
        )
    )
    private boolean includeShipsInParticleDistanceCheck(
        final BlockPos player, final Position particle, final double distance) {

        final ServerWorld self = ServerWorld.class.cast(this);
        final ShipObject ship = VSGameUtilsKt.getShipObjectManagingPos(
            self, (int) particle.getX() >> 4, (int) particle.getZ() >> 4);

        if (ship == null) {
            // vanilla behaviour
            return player.isWithinDistance(particle, distance);
        }

        // in-world position
        final Vector3d posInWorld = ship.getShipData().getShipTransform().getShipToWorldMatrix()
            .transformPosition(VectorConversionsMCKt.toJOML(particle));

        return posInWorld.distanceSquared(player.getX(), player.getY(), player.getZ()) < distance * distance;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        // First update the IPlayer wrapper list
        updateVSPlayerWrappers();

        // Find newly loaded chunks
        final List<ChunkHolder> loadedChunksList = Lists.newArrayList(
            ((ThreadedAnvilChunkStorageAccessor) serverChunkManager.threadedAnvilChunkStorage).callEntryIterator());

        // Create DenseVoxelShapeUpdate for new loaded chunks
        // Also mark the chunks as loaded in the ship objects
        final List<IVoxelShapeUpdate> newLoadedChunks = new ArrayList<>();

        for (final ChunkHolder chunkHolder : loadedChunksList) {
            final Optional<WorldChunk> worldChunkOptional =
                chunkHolder.getTickingFuture().getNow(ChunkHolder.UNLOADED_WORLD_CHUNK).left();
            if (worldChunkOptional.isPresent()) {
                final WorldChunk worldChunk = worldChunkOptional.get();
                final int chunkX = worldChunk.getPos().x;
                final int chunkZ = worldChunk.getPos().z;

                final ChunkSection[] chunkSections = worldChunk.getSectionArray();

                final ShipData shipData = shipObjectWorld.getQueryableShipData().getShipDataFromChunkPos(chunkX, chunkZ);
                if (shipData != null) {
                    // Tell the ship data that the chunk has been loaded
                    shipData.onLoadChunk(chunkX, chunkZ);
                }

                // For now just assume chunkY goes from 0 to 16
                for (int chunkY = 0; chunkY < 16; chunkY++) {
                    final ChunkSection chunkSection = chunkSections[chunkY];
                    final Vector3ic chunkPos = new Vector3i(chunkX, chunkY, chunkZ);

                    if (!knownChunkRegions.contains(chunkPos)) {
                        if (chunkSection != null && !chunkSection.isEmpty()) {
                            // Add this chunk to the ground rigid body
                            final DenseVoxelShapeUpdate voxelShapeUpdate =
                                VSGameUtilsKt.toDenseVoxelUpdate(chunkSection, chunkPos);
                            newLoadedChunks.add(voxelShapeUpdate);
                        } else {
                            final EmptyVoxelShapeUpdate emptyVoxelShapeUpdate =
                                new EmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), false, true);
                            newLoadedChunks.add(emptyVoxelShapeUpdate);
                        }

                        knownChunkRegions.add(chunkPos);
                    }
                }
            }
        }

        // Then tick the ship world
        shipObjectWorld.tickShips(newLoadedChunks);

        // Send ships to clients
        final IVSPacket shipDataPacket = VSPacketShipDataList.Companion
            .create(shipObjectWorld.getQueryableShipData().iterator());

        for (final ServerPlayerEntity playerEntity : players) {
            VSNetworking.shipDataPacketToClientSender.sendToClient(shipDataPacket, playerEntity);
        }

        // Then determine the chunk watch/unwatch tasks, and then execute them
        final ImmutableList<IPlayer> playersToTick = ImmutableList.copyOf(vsPlayerWrappers.values());
        final Pair<Spliterator<ChunkWatchTask>, Spliterator<ChunkUnwatchTask>> chunkWatchAndUnwatchTasksPair =
            shipObjectWorld.tickShipChunkLoading(playersToTick);

        // Use Spliterator instead of iterators so that we can multi thread the execution of these tasks
        final Spliterator<ChunkWatchTask> chunkWatchTasks = chunkWatchAndUnwatchTasksPair.getFirst();
        final Spliterator<ChunkUnwatchTask> chunkUnwatchTasks = chunkWatchAndUnwatchTasksPair.getSecond();

        // But for now just do it single threaded
        chunkWatchTasks.forEachRemaining(chunkWatchTask -> {
            System.out.println("Watch task for " + chunkWatchTask.getChunkX() + " : " + chunkWatchTask.getChunkZ());
            final Packet<?>[] chunkPacketBuffer = new Packet[2];
            final ChunkPos chunkPos = new ChunkPos(chunkWatchTask.getChunkX(), chunkWatchTask.getChunkZ());

            // TODO: Move this somewhere else
            serverChunkManager.setChunkForced(chunkPos, true);

            for (final IPlayer player : chunkWatchTask.getPlayersNeedWatching()) {
                final MinecraftPlayer minecraftPlayer = (MinecraftPlayer) player;
                final ServerPlayerEntity serverPlayerEntity =
                    (ServerPlayerEntity) minecraftPlayer.getPlayerEntityReference().get();
                if (serverPlayerEntity != null) {
                    ((ThreadedAnvilChunkStorageAccessor) serverChunkManager.threadedAnvilChunkStorage)
                        .callSendWatchPackets(serverPlayerEntity, chunkPos, chunkPacketBuffer, false, true);
                }
            }
            chunkWatchTask.onExecuteChunkWatchTask();
        });

        chunkUnwatchTasks.forEachRemaining(chunkUnwatchTask -> {
            System.out
                .println("Unwatch task for " + chunkUnwatchTask.getChunkX() + " : " + chunkUnwatchTask.getChunkZ());
            chunkUnwatchTask.onExecuteChunkUnwatchTask();
        });
    }

    @Unique
    private void updateVSPlayerWrappers() {
        // First add new player objects
        players.forEach(player -> {
            final UUID playerID = player.getUuid();
            if (!vsPlayerWrappers.containsKey(playerID)) {
                final MinecraftPlayer playerWrapper = new MinecraftPlayer(player, playerID);
                vsPlayerWrappers.put(playerID, playerWrapper);
            }
        });

        // Then remove removed player objects
        // First make a set of all current player IDs, so we can check if a player is online in O(1) time.
        final Set<UUID> currentPlayerIDs = new HashSet<>();
        players.forEach(player -> currentPlayerIDs.add(player.getUuid()));

        // Then remove any old player wrappers whose players are no longer here.
        vsPlayerWrappers.entrySet().removeIf(entry -> !currentPlayerIDs.contains(entry.getKey()));
    }

}
