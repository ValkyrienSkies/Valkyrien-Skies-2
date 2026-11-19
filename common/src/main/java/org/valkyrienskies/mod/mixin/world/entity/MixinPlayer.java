package org.valkyrienskies.mod.mixin.world.entity;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(Player.class)
public class MixinPlayer implements PlayerDuck {

    @Unique
    private volatile MinecraftPlayer vsPlayer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializePlayerWrapper(final CallbackInfo ci) {
        // Mixin field initializers run before Player assigns its profile UUID.
        vsPlayer = new MinecraftPlayer(Player.class.cast(this));
    }

    @Override
    public MinecraftPlayer vs_getPlayer() {
        return vsPlayer;
    }

    @Override
    public void vs_setPlayer(final MinecraftPlayer player) {
        vsPlayer = player;
    }

}
