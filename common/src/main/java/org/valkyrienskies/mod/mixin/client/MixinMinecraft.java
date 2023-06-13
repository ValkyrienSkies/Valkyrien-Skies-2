package org.valkyrienskies.mod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.apigame.world.ClientShipWorldCore;
import org.valkyrienskies.core.apigame.world.VSPipeline;
import org.valkyrienskies.mod.common.IShipObjectWorldClientCreator;
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.world.DummyShipWorldClient;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft
    implements MinecraftDuck, IShipObjectWorldClientProvider, IShipObjectWorldClientCreator {

    @Unique
    private static final Logger log = LogManager.getLogger("VS2 MixinMinecraft");

    @Shadow
    private boolean pause;

    @Shadow
    @Nullable
    public abstract IntegratedServer getSingleplayerServer();

    @Shadow
    public ClientLevel level;

    @Unique
    private HitResult originalCrosshairTarget;

    @Override
    public void vs$setOriginalCrosshairTarget(final HitResult originalCrosshairTarget) {
        this.originalCrosshairTarget = originalCrosshairTarget;
    }

    @Override
    public HitResult vs$getOriginalCrosshairTarget() {
        return originalCrosshairTarget;
    }

    @Unique
    private ClientShipWorldCore shipObjectWorld = null;

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
        ),
        method = "startUseItem"
    )
    private InteractionResult useOriginalCrosshairForBlockPlacement(final MultiPlayerGameMode instance,
        final LocalPlayer localPlayer, final InteractionHand interactionHand,
        final BlockHitResult blockHitResult, final Operation<InteractionResult> useItemOn) {

        return useItemOn.call(instance, localPlayer, interactionHand,
            this.originalCrosshairTarget);
    }

    @NotNull
    @Override
    public ClientShipWorldCore getShipObjectWorld() {
        final ClientShipWorldCore shipObjectWorldCopy = shipObjectWorld;

        if (shipObjectWorldCopy == null) {
            log.warn("Requested getShipObjectWorld() when shipObjectWorld was null!");
            return DummyShipWorldClient.INSTANCE;
        }
        return shipObjectWorldCopy;
    }

    @Shadow
    public abstract ClientPacketListener getConnection();

    @Inject(
        method = "tick",
        at = @At("TAIL")
    )
    public void postTick(final CallbackInfo ci) {
        // Tick the ship world and then drag entities
        if (!pause && shipObjectWorld != null && level != null && getConnection() != null) {
            shipObjectWorld.tickNetworking(getConnection().getConnection().getRemoteAddress());
            shipObjectWorld.postTick();
            EntityDragger.INSTANCE.dragEntitiesWithShips(level.entitiesForRendering());
        }
    }

    @Inject(
        method = "runTick",
        at = @At(value = "TAIL")
    )
    public void setGamePause(final boolean pauseOnly, final CallbackInfo ci) {
        final IShipObjectWorldServerProvider provider = (IShipObjectWorldServerProvider) getSingleplayerServer();
        if (provider != null) {
            final VSPipeline pipeline = provider.getVsPipeline();
            if (pipeline != null) {
                pipeline.setArePhysicsRunning(!this.pause);
            }
        }
    }

    @Inject(
        method = "setCurrentServer",
        at = @At("HEAD")
    )
    public void preSetCurrentServer(final ServerData serverData, final CallbackInfo ci) {
        ValkyrienSkiesMod.getVsCore().setClientUsesUDP(false);
    }

    @Override
    public void createShipObjectWorldClient() {
        if (shipObjectWorld != null) {
            throw new IllegalStateException("shipObjectWorld was not null when it should be null?");
        }
        shipObjectWorld = ValkyrienSkiesMod
            .getVsCoreClient()
            .newShipWorldClient();
    }

    @Override
    public void deleteShipObjectWorldClient() {
        final ClientShipWorldCore shipObjectWorldCopy = shipObjectWorld;
        if (shipObjectWorldCopy == null) {
            throw new IllegalStateException("shipObjectWorld was null when it should be not null?");
        }
        shipObjectWorldCopy.destroyWorld();
        shipObjectWorld = null;
    }

    @Inject(
        method = "clearLevel",
        at = @At("TAIL")
    )
    private void postClearLevel(final CallbackInfo ci) {
        deleteShipObjectWorldClient();
    }
}
