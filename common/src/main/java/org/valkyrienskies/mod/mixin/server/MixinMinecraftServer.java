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
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld;
import org.valkyrienskies.core.pipelines.VSPipeline;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.ShipSavedData;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.event.RegistryEvents;
import org.valkyrienskies.physics_api_krunch.KrunchBootstrap;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements IShipObjectWorldServerProvider {
    @Shadow
    private PlayerManager playerManager;

    @Shadow
    public abstract ServerWorld getOverworld();

    @Unique
    private ShipObjectServerWorld shipWorld;

    @Unique
    private VSPipeline vsPipeline;

    @Unique
    private final Map<UUID, MinecraftPlayer> vsPlayerWrappers = new HashMap<>();

    @Inject(
        at = @At("HEAD"),
        method = "tick"
    )
    public void onTick(final BooleanSupplier booleanSupplier, final CallbackInfo ci) {
        updateVSPlayerWrappers();
        shipWorld.setPlayers(ImmutableSet.copyOf(vsPlayerWrappers.values()));
    }

    @Unique
    private void updateVSPlayerWrappers() {
        final List<ServerPlayerEntity> players = playerManager.getPlayerList();
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
            target = "Lnet/minecraft/server/MinecraftServer;setupServer()Z"
        )
    )
    private void preRunServer(final CallbackInfo ci) {
        KrunchBootstrap.INSTANCE.loadNativeBinaries();

        // Load ship data from the world storage
        final ShipSavedData shipSavedData = getOverworld().getPersistentStateManager()
            .getOrCreate(ShipSavedData.Companion::createEmpty, ShipSavedData.SAVED_DATA_ID);

        // Create ship world and VS Pipeline
        shipWorld = new ShipObjectServerWorld(shipSavedData.getQueryableShipData(), shipSavedData.getChunkAllocator());
        vsPipeline = new VSPipeline(shipWorld);

        RegistryEvents.registriesAreComplete();
    }

    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void preTick(final CallbackInfo ci) {
        vsPipeline.preTickGame();
    }

    @Inject(
        method = "tick",
        at = @At("TAIL")
    )
    private void postTick(final CallbackInfo ci) {
        vsPipeline.postTickGame();
    }

    @Inject(
        method = "shutdown",
        at = @At("HEAD")
    )
    private void preShutdown(final CallbackInfo ci) {
        vsPipeline.setDeleteResources(true);
    }
}
