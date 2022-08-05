package org.valkyrienskies.mod.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.DaggerShipObjectClientWorld_Factory;
import org.valkyrienskies.core.game.ships.ShipObjectClientWorld;
import org.valkyrienskies.core.networking.VSNetworking;
import org.valkyrienskies.core.pipelines.VSPipeline;
import org.valkyrienskies.mod.common.IShipObjectWorldClientCreator;
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft
    implements MinecraftDuck, IShipObjectWorldClientProvider, IShipObjectWorldClientCreator {

    @Shadow
    private boolean pause;

    @Shadow
    @Nullable
    public abstract IntegratedServer getSingleplayerServer();

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
    private ShipObjectClientWorld shipObjectWorld = null;

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
        ),
        method = "startUseItem"
    )
    private InteractionResult useOriginalCrosshairForBlockPlacement(final MultiPlayerGameMode instance,
        final LocalPlayer localPlayer, final ClientLevel clientLevel, final InteractionHand interactionHand,
        final BlockHitResult blockHitResult) {

        return instance.useItemOn(localPlayer, clientLevel, interactionHand,
            (BlockHitResult) this.originalCrosshairTarget);
    }

    @NotNull
    @Override
    public ShipObjectClientWorld getShipObjectWorld() {
        final ShipObjectClientWorld shipObjectWorldCopy = shipObjectWorld;
        if (shipObjectWorldCopy == null) {
            throw new IllegalStateException("Requested getShipObjectWorld() when shipObjectWorld was null!");
        }
        return shipObjectWorldCopy;
    }

    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    public void preTick(final CallbackInfo ci) {
        // Tick the ship world
        if (shipObjectWorld != null) {
            shipObjectWorld.preTick();
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
        VSNetworking.INSTANCE.setClientUsesUDP(false);
    }

    @Override
    public void createShipObjectWorldClient() {
        if (shipObjectWorld != null) {
            throw new IllegalStateException("shipObjectWorld was not null when it should be null?");
        }
        shipObjectWorld = DaggerShipObjectClientWorld_Factory.create().make();
    }

    @Override
    public void deleteShipObjectWorldClient() {
        final ShipObjectClientWorld shipObjectWorldCopy = shipObjectWorld;
        if (shipObjectWorldCopy == null) {
            throw new IllegalStateException("shipObjectWorld was null when it should be not null?");
        }
        shipObjectWorldCopy.destroyWorld();
        shipObjectWorld = null;
    }
}
