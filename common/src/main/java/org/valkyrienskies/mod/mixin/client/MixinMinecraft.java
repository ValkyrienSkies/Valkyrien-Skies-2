package org.valkyrienskies.mod.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(Minecraft.class)
public class MixinMinecraft implements MinecraftDuck {

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

    @Redirect(
        method = "startUseItem",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
        )
    )
    private InteractionResult wrapInteractBlock(final MultiPlayerGameMode receiver,
        final LocalPlayer player, final ClientLevel world, final InteractionHand hand, final BlockHitResult hitResult) {

        return PlayerUtil.INSTANCE.transformPlayerTemporarily(player,
            VSGameUtilsKt.getShipObjectManagingPos(world, hitResult.getBlockPos()),
            () -> receiver.useItemOn(player, world, hand, (BlockHitResult) originalCrosshairTarget));
    }
}
