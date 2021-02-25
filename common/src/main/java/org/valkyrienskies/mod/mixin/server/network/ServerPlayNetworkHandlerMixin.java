package org.valkyrienskies.mod.mixin.server.network;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtils;

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
    public double includeShipsInDistanceCheck(ServerPlayerEntity receiver, double x, double y, double z) {
        Vector3d inWorld = VSGameUtils.getWorldCoordinates(this.player.getServerWorld(), new Vector3d(x, y, z));
        return inWorld.distanceSquared(receiver.getX(), receiver.getY(), receiver.getZ());
    }

}
