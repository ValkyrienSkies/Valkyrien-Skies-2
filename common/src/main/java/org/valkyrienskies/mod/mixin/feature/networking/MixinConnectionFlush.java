package org.valkyrienskies.mod.mixin.feature.networking;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.networking.VsNetworkPlatform;

/**
 * VS networking: flushes batched VS messages before any vanilla packet goes out on a play connection, so VS
 * messages keep their order relative to vanilla packets while consecutive VS messages share one payload.
 *
 * Only the two-argument send is targeted (the one-argument overload delegates to it). Guards:
 * - only play connections (game packet listeners), never login/status;
 * - custom payload packets (including VS's own) don't trigger a flush;
 * - re-entrant calls are ignored;
 * - the session itself ignores flushes from threads other than the connection's game thread.
 */
@Mixin(Connection.class)
public abstract class MixinConnectionFlush {
    @Unique
    private boolean vs$flushing;

    @Shadow
    public abstract PacketListener getPacketListener();

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at = @At("HEAD")
    )
    private void vs$flushBeforeVanilla(final Packet<?> packet, final PacketSendListener listener, final CallbackInfo ci) {
        if (vs$flushing || packet instanceof ClientboundCustomPayloadPacket
            || packet instanceof ServerboundCustomPayloadPacket) {
            return;
        }
        final PacketListener packetListener = getPacketListener();
        vs$flushing = true;
        try {
            if (packetListener instanceof final ServerGamePacketListenerImpl serverListener) {
                if (serverListener.player != null) {
                    VsNetworkPlatform.flushServer(serverListener.player);
                }
            } else if (packetListener instanceof ClientGamePacketListener) {
                VsNetworkPlatform.flushClient();
            }
        } finally {
            vs$flushing = false;
        }
    }
}
