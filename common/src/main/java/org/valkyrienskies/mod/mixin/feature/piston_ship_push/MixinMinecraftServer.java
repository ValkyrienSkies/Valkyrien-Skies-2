package org.valkyrienskies.mod.mixin.feature.piston_ship_push;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.PistonShipPush;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void vs$flushPistonForces(final CallbackInfo ci) {
        PistonShipPush.flushPending();
    }
}
