package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.client.PlayerShipPushClient;
import org.valkyrienskies.mod.common.util.ShipPushing;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientShipPush {
    @Inject(method = "performUseItemOn", at = @At("HEAD"), cancellable = true)
    private void vs$startPush(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                              CallbackInfoReturnable<InteractionResult> cir) {
        if (ShipPushing.tryStart(player, hand, hit)) {
            PlayerShipPushClient.start(player);
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }
}
