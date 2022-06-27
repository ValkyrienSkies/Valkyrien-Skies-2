package org.valkyrienskies.mod.mixin.server;

import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.IPlayer;
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld;
import org.valkyrienskies.core.pipelines.VSPipeline;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.ShipSavedData;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.event.RegistryEvents;
import org.valkyrienskies.mod.mixinducks.server.IPlayerProvider;
import org.valkyrienskies.physics_api_krunch.KrunchBootstrap;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements IShipObjectWorldServerProvider, IPlayerProvider {
    @Shadow
    private PlayerList playerList;

    @Shadow
    public abstract ServerLevel overworld();

    @Unique
    private ShipObjectServerWorld shipWorld;

    @Unique
    private VSPipeline vsPipeline;

    @Unique
    private final Map<UUID, MinecraftPlayer> vsPlayerWrappers = new HashMap<>();

    @Inject(
        at = @At("HEAD"),
        method = "tickServer"
    )
    public void onTick(final BooleanSupplier booleanSupplier, final CallbackInfo ci) {
        updateVSPlayerWrappers();
        shipWorld.setPlayers(ImmutableSet.copyOf(vsPlayerWrappers.values()));
    }

    @Unique
    private void updateVSPlayerWrappers() {
        final List<ServerPlayer> players = playerList.getPlayers();
        // First add new player objects
        players.forEach(player -> {
            final UUID playerID = player.getUUID();
            if (!vsPlayerWrappers.containsKey(playerID)) {
                final MinecraftPlayer playerWrapper = new MinecraftPlayer(player, playerID);
                vsPlayerWrappers.put(playerID, playerWrapper);
            }
        });

        // Then remove removed player objects
        // First make a set of all current player IDs, so we can check if a player is online in O(1) time.
        final Set<UUID> currentPlayerIDs = new HashSet<>();
        players.forEach(player -> currentPlayerIDs.add(player.getUUID()));

        // Then remove any old player wrappers whose players are no longer here.
        vsPlayerWrappers.entrySet().removeIf(entry -> !currentPlayerIDs.contains(entry.getKey()));
    }

    @Override
    public IPlayer getPlayer(final UUID uuid) {
        return vsPlayerWrappers.get(uuid);
    }

    @NotNull
    @Override
    public ShipObjectServerWorld getShipObjectWorld() {
        return shipWorld;
    }

    @NotNull
    @Override
    public VSPipeline getVsPipeline() {
        return vsPipeline;
    }

    @Inject(
        method = "runServer",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/server/MinecraftServer;initServer()Z"
        )
    )
    private void preRunServer(final CallbackInfo ci) {
        KrunchBootstrap.INSTANCE.loadNativeBinaries();

        // Load ship data from the world storage
        final ShipSavedData shipSavedData = overworld().getDataStorage()
            .computeIfAbsent(ShipSavedData.Companion::createEmpty, ShipSavedData.SAVED_DATA_ID);

        // Create ship world and VS Pipeline
        shipWorld = new ShipObjectServerWorld(shipSavedData.getQueryableShipData(), shipSavedData.getChunkAllocator());
        vsPipeline = new VSPipeline(shipWorld);

        RegistryEvents.registriesAreComplete();
    }

    @Inject(
        method = "tickServer",
        at = @At("HEAD")
    )
    private void preTick(final CallbackInfo ci) {
        vsPipeline.preTickGame();
    }

    @Inject(
        method = "tickServer",
        at = @At("TAIL")
    )
    private void postTick(final CallbackInfo ci) {
        vsPipeline.postTickGame();
    }

    @Inject(
        method = "onServerExit",
        at = @At("HEAD")
    )
    private void preShutdown(final CallbackInfo ci) {
        vsPipeline.setDeleteResources(true);
    }
}
