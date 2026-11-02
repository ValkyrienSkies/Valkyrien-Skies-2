package org.valkyrienskies.mod.mixin.feature.air_pockets.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;

@Mixin(value = MinecraftServer.class, priority = 900)
public abstract class MixinMinecraftServer {

    @Shadow
    public abstract Iterable<ServerLevel> getAllLevels();

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void valkyrienair$tickShipWaterPockets(final CallbackInfo ci) {
        for (final ServerLevel level : getAllLevels()) {
            ShipWaterPocketManager.tickServerLevel(level);
        }
    }
}

