package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.PlayerShipPushClient;

@Mixin(LocalPlayer.class)
public abstract class MixinShipPushInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void vs$sendPushInput(CallbackInfo ci) {
        PlayerShipPushClient.tick((LocalPlayer) (Object) this);
    }
}
