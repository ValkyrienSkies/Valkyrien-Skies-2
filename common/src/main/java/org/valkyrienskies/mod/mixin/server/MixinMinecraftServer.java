package org.valkyrienskies.mod.mixin.server;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.IPlayer;
import org.valkyrienskies.core.game.ships.SerializedShipDataModule;
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld;
import org.valkyrienskies.core.pipelines.VSPipeline;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.ShipSavedData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.world.ChunkManagement;
import org.valkyrienskies.mod.event.RegistryEvents;
import org.valkyrienskies.physics_api_krunch.KrunchBootstrap;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements IShipObjectWorldServerProvider {
    @Shadow
    private PlayerList playerList;

    @Shadow
    public abstract ServerLevel overworld();

    @Shadow
    public abstract Iterable<ServerLevel> getAllLevels();

    @Unique
    private ShipObjectServerWorld shipWorld;

    @Unique
    private VSPipeline vsPipeline;

    private Set<String> loadedLevels = new HashSet<>();

    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initServer()Z"),
        method = "runServer"
    )
    private void beforeInitServer(final CallbackInfo info) {
        ValkyrienSkiesMod.setCurrentServer(MinecraftServer.class.cast(this));
    }

    @Inject(at = @At("TAIL"), method = "stopServer")
    private void afterStopServer(final CallbackInfo ci) {
        ValkyrienSkiesMod.setCurrentServer(null);
    }

    @Inject(
        at = @At("HEAD"),
        method = "tickServer"
    )
    public void onTick(final BooleanSupplier booleanSupplier, final CallbackInfo ci) {
        final Set<IPlayer> vsPlayers = playerList.getPlayers().stream()
            .map(VSGameUtilsKt::getPlayerWrapper).collect(Collectors.toSet());

        shipWorld.setPlayers(vsPlayers);
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

    /**
     * Create the ship world immediately after the levels are created, so that nothing can try to access the ship world
     * before it has been initialized.
     */
    @Inject(
        method = "createLevels",
        at = @At("TAIL")
    )
    private void postCreateLevels(final CallbackInfo ci) {
        KrunchBootstrap.INSTANCE.loadNativeBinaries();

        // Load ship data from the world storage
        final ShipSavedData shipSavedData = overworld().getDataStorage()
            .computeIfAbsent(ShipSavedData.Companion::createEmpty, ShipSavedData.SAVED_DATA_ID);

        // Create ship world and VS Pipeline

        vsPipeline = ValkyrienSkiesMod.getVsCore().getPipelineComponentFactory()
            .newPipelineComponent(new SerializedShipDataModule(
                shipSavedData.getQueryableShipData(), shipSavedData.getChunkAllocator()))
            .newPipeline();

        shipWorld = vsPipeline.getShipWorld();

        RegistryEvents.registriesAreComplete();
    }

    @Inject(
        method = "tickServer",
        at = @At("HEAD")
    )
    private void preTick(final CallbackInfo ci) {
        vsPipeline.preTickGame();

        // region Tell the VS world to load new levels, and unload deleted ones
        final Set<String> newLoadedLevels = new HashSet<>();
        for (final ServerLevel level : getAllLevels()) {
            newLoadedLevels.add(VSGameUtilsKt.getDimensionId(level));
        }
        for (final String loadedLevelId : newLoadedLevels) {
            if (!loadedLevels.contains(loadedLevelId)) {
                shipWorld.addDimension(loadedLevelId);
            }
        }
        for (final String oldLoadedLevelId : loadedLevels) {
            if (!newLoadedLevels.contains(oldLoadedLevelId)) {
                shipWorld.removeDimension(oldLoadedLevelId);
            }
        }
        loadedLevels = newLoadedLevels;
        // endregion
    }

    /**
     * Tick the [shipWorld], then send voxel terrain updates for each level
     */
    @Inject(
        method = "tickChildren",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerConnectionListener;tick()V"
        )
    )
    private void preConnectionTick(final CallbackInfo ci) {
        ChunkManagement.tickChunkLoading(shipWorld, MinecraftServer.class.cast(this));
    }

    @Inject(
        method = "tickServer",
        at = @At("TAIL")
    )
    private void postTick(final CallbackInfo ci) {
        vsPipeline.postTickGame();
        // Only drag entities after we have updated the ship positions
        for (final ServerLevel level : getAllLevels()) {
            EntityDragger.INSTANCE.dragEntitiesWithShips(level.getAllEntities());
        }
    }

    @Inject(
        method = "stopServer",
        at = @At("HEAD")
    )
    private void preStopServer(final CallbackInfo ci) {
        vsPipeline.setDeleteResources(true);
    }
}
