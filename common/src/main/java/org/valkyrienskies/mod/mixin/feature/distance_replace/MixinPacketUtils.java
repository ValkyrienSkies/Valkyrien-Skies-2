package org.valkyrienskies.mod.mixin.feature.distance_replace;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareDistance;

// Game packets are handled outside any level tick; give distance checks the sending player's level.
@Mixin(PacketUtils.class)
public class MixinPacketUtils {

    // The main-thread lambda in ensureRunningOnSameThread
    @WrapOperation(
        method = "method_11072",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"
        )
    )
    private static void vs$setDistanceContextLevel(final Packet<?> packet, final PacketListener listener,
        final Operation<Void> original) {
        if (listener instanceof final ServerGamePacketListenerImpl game) {
            ShipAwareDistance.withLevel(game.player.serverLevel(), () -> original.call(packet, listener));
        } else {
            original.call(packet, listener);
        }
    }
}
