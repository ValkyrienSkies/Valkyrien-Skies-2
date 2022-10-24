package org.valkyrienskies.mod.mixin.client;

import net.minecraft.client.Game;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.IShipObjectWorldClientCreator;

@Mixin(Game.class)
public class MixinGame {
    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * @reason Create a new [ShipObjectClientWorld] when we start a new game session.
     */
    @Inject(method = "onStartGameSession", at = @At("HEAD"))
    private void preOnStartGameSession(final CallbackInfo ci) {
        ((IShipObjectWorldClientCreator) minecraft).createShipObjectWorldClient();
    }

    /**
     * @reason Destroy the [ShipObjectClientWorld] when we leave a game session.
     */
    @Inject(method = "onLeaveGameSession", at = @At("HEAD"))
    private void preOnLeaveGameSession(final CallbackInfo ci) {
        ((IShipObjectWorldClientCreator) minecraft).deleteShipObjectWorldClient();
    }
}
