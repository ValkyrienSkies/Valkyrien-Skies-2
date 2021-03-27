package org.valkyrienskies.mod.mixin.server.network;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSConfig;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Redirect(
        method = "onPlayerInteractBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;squaredDistanceTo(DDD)D"
        )
    )
    public double includeShipsInBlockInteractDistanceCheck(
        final ServerPlayerEntity receiver, final double x, final double y, final double z) {
        if (VSConfig.getEnableInteractDistanceChecks()) {
            return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
        } else {
            return 0;
        }
    }

}
