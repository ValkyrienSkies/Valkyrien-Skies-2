package org.valkyrienskies.mod.mixin.server.world;

import com.google.common.collect.ImmutableList;
import kotlin.Pair;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentStateManager;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.chunk_tracking.ChunkUnwatchTask;
import org.valkyrienskies.core.chunk_tracking.ChunkWatchTask;
import org.valkyrienskies.core.game.IPlayer;
import org.valkyrienskies.core.game.ShipObjectWorld;
import org.valkyrienskies.core.networking.IVSPacket;
import org.valkyrienskies.core.networking.impl.VSPacketShipDataList;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;
import org.valkyrienskies.mod.common.ShipSavedData;
import org.valkyrienskies.mod.common.VSNetworking;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;

import java.util.*;
import java.util.function.BooleanSupplier;

@Mixin(ServerWorld.class)
public abstract class MixinServerWorld implements IShipObjectWorldProvider {

    @Shadow
    public abstract PersistentStateManager getPersistentStateManager();

    @Shadow
    @Final
    private List<ServerPlayerEntity> players;

    @Shadow
    @Final
    private ServerChunkManager serverChunkManager;

    private ShipObjectWorld shipObjectWorld = null;
    private ShipSavedData shipSavedData = null;
    private final Map<UUID, MinecraftPlayer> vsPlayerWrappers = new HashMap<>();

    @Inject(
            at = @At("TAIL"),
            method = "<init>"
    )
    private void postConstructor(CallbackInfo info) {
        // Load ship data from the world storage
        shipSavedData = getPersistentStateManager()
                .getOrCreate(ShipSavedData.Companion::createEmpty, ShipSavedData.SAVED_DATA_ID);
        // Make a ship world using the loaded ship data
        shipObjectWorld = new ShipObjectWorld(shipSavedData.getQueryableShipData(), shipSavedData.getChunkAllocator());
    }

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // First update the IPlayer wrapper list
        updateVSPlayerWrappers();

        // Then tick the ship world
        shipObjectWorld.tickShips();

        // Send ships to clients
        final IVSPacket shipDataPacket = VSPacketShipDataList.Companion.create(shipObjectWorld.getQueryableShipData().iterator());
        for (ServerPlayerEntity playerEntity : players) {
            VSNetworking.shipDataPacketToClientSender.sendToClient(shipDataPacket, playerEntity);
        }

        // Then determine the chunk watch/unwatch tasks, and then execute them
        final ImmutableList<IPlayer> playersToTick = ImmutableList.copyOf(vsPlayerWrappers.values());
        final Pair<Spliterator<ChunkWatchTask>, Spliterator<ChunkUnwatchTask>> chunkWatchAndUnwatchTasksPair = shipObjectWorld.tickShipChunkLoading(playersToTick);

        // Use Spliterator instead of iterators so that we can multi thread the execution of these tasks
        final Spliterator<ChunkWatchTask> chunkWatchTasks = chunkWatchAndUnwatchTasksPair.getFirst();
        final Spliterator<ChunkUnwatchTask> chunkUnwatchTasks = chunkWatchAndUnwatchTasksPair.getSecond();

        final ThreadedAnvilChunkStorageAccessor accessor = (ThreadedAnvilChunkStorageAccessor) serverChunkManager.threadedAnvilChunkStorage;

        // But for now just do it single threaded
        chunkWatchTasks.forEachRemaining(chunkWatchTask -> {
            System.out.println("Watch task for " + chunkWatchTask.getChunkX() + " : " + chunkWatchTask.getChunkZ());
            final Packet<?>[] chunkPacketBuffer = new Packet[2];
            final ChunkPos chunkPos = new ChunkPos(chunkWatchTask.getChunkX(), chunkWatchTask.getChunkZ());

            // TODO: Move this somewhere else
            serverChunkManager.setChunkForced(chunkPos, true);

            for (final IPlayer player : chunkWatchTask.getPlayersNeedWatching()) {
                final MinecraftPlayer minecraftPlayer = (MinecraftPlayer) player;
                final ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) minecraftPlayer.getPlayerEntityReference().get();
                if (serverPlayerEntity != null) {
                    accessor.callSendWatchPackets(serverPlayerEntity, chunkPos, chunkPacketBuffer, false, true);
                }
            }
            chunkWatchTask.onExecuteChunkWatchTask();
        });

        chunkUnwatchTasks.forEachRemaining(chunkUnwatchTask -> {
            System.out.println("Unwatch task for " + chunkUnwatchTask.getChunkX() + " : " + chunkUnwatchTask.getChunkZ());
            chunkUnwatchTask.onExecuteChunkUnwatchTask();
        });
    }

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
