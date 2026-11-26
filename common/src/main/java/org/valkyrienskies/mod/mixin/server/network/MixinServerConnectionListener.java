package org.valkyrienskies.mod.mixin.server.network;

import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import java.net.InetAddress;
import net.minecraft.server.network.ServerConnectionListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells VS networking where the Minecraft server listens, each time it starts listening: a dedicated server at
 * startup (server-ip and server-port) or a world opened to LAN. The QUIC lane uses the same address and, unless
 * udpPort is set, the same port.
 */
@Mixin(ServerConnectionListener.class)
public class MixinServerConnectionListener {
    @Inject(method = "startTcpServerListener", at = @At("TAIL"))
    private void vs$listening(@Nullable final InetAddress address, final int port, final CallbackInfo ci) {
        getVsCore().getNetworkHost().serverListening(address, port);
    }
}
