package org.valkyrienskies.mod.mixin.world.entity;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(ServerPlayer.class)
public class MixinServerPlayer {

    /**
     * {@code PlayerList.respawn} replaces the player with a new entity on the same connection, assigning the connection
     * before calling this. Keep the player's wrapper (and with it vs-core's ship tracking and network session) on the
     * connection rather than on the entity.
     */
    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void vs$keepPlayerWrapper(final ServerPlayer previous, final boolean keepEverything, final CallbackInfo ci) {
        final ServerPlayer self = ServerPlayer.class.cast(this);
        if (self.connection != null && self.connection == previous.connection) {
            ((PlayerDuck) self).vs_takeOverPlayer(previous);
        }
    }

}
