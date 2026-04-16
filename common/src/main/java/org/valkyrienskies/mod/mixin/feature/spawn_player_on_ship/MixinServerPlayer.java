package org.valkyrienskies.mod.mixin.feature.spawn_player_on_ship;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck;

@Mixin(ServerPlayer.class)
public class MixinServerPlayer {

    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void copyFrom(ServerPlayer serverPlayer, boolean bl, CallbackInfo ci) {
        ((PlayerKnownShipsDuck) this).vs_setKnownShips(((PlayerKnownShipsDuck) serverPlayer).vs_getKnownShips());
    }

}
