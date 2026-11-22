package org.valkyrienskies.mod.mixin.feature.distance_replace;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareDistance;

// Players tick from the connection, outside ServerLevel.tick (menu stillValid checks run here).
@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerTick {

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;doTick()V")
    )
    private void vs$setDistanceContextLevel(final ServerPlayer player, final Operation<Void> original) {
        ShipAwareDistance.withLevel(player.serverLevel(), () -> original.call(player));
    }
}
