package org.valkyrienskies.mod.fabric.common

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerLoginPacketListenerImpl
import org.valkyrienskies.core.internal.hooks.VsiCoreHooksIn
import org.valkyrienskies.core.internal.network.ServerLoginHandshake
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.networking.VsNetworkPlatform
import org.valkyrienskies.mod.common.networking.VsNetworkStatistics
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.server.network.ServerLoginPacketListenerImplAccessor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers VS with the Fabric networking API: one play channel carrying VS session payloads (fragmentation is
 * handled by the VS session), and the login-phase handshake as a login query.
 */
class VSFabricNetworking(
    private val isClient: Boolean
) {
    private val VS_PACKET_ID = ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet")

    private val pendingLogins = ConcurrentHashMap<ServerLoginPacketListenerImpl, ServerLoginHandshake>()

    private fun registerClientNetworking(hooks: VsiCoreHooksIn) {
        ClientPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { _, _, buf, _ ->
            VsNetworkStatistics.record("recv", "s2c", "vs_packet", buf.readableBytes())
            hooks.onReceiveClient(buf)
        }
        ClientLoginNetworking.registerGlobalReceiver(VsNetworkPlatform.HANDSHAKE_ID) { _, _, buf, _ ->
            val reply = ValkyrienSkiesMod.vsCore.networkHost.clientHandleLoginHello(buf)
            CompletableFuture.completedFuture(PacketByteBufs.copy(Unpooled.wrappedBuffer(reply)))
        }
    }

    fun register(hooks: VsiCoreHooksIn) {
        if (isClient) {
            registerClientNetworking(hooks)
        }

        ServerPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { _, player, _, buf, _ ->
            VsNetworkStatistics.record("recv", "c2s", "vs_packet", buf.readableBytes())
            hooks.onReceiveServer(buf, player.playerWrapper)
        }

        // Login-phase VS handshake: protocol/channel compatibility and the message id table.
        ServerLoginConnectionEvents.QUERY_START.register { handler, _, sender, _ ->
            val handshake = VsNetworkPlatform.startLogin()
            pendingLogins[handler] = handshake
            sender.sendPacket(VsNetworkPlatform.HANDSHAKE_ID, PacketByteBufs.copy(Unpooled.wrappedBuffer(handshake.hello)))
        }
        ServerLoginConnectionEvents.DISCONNECT.register { handler, _ -> pendingLogins.remove(handler) }
        ServerLoginNetworking.registerGlobalReceiver(VsNetworkPlatform.HANDSHAKE_ID) { _, handler, understood, buf, _, _ ->
            val handshake = pendingLogins.remove(handler) ?: return@registerGlobalReceiver
            if (!understood) {
                handler.disconnect(Component.literal(handshake.outdatedClientReason))
                return@registerGlobalReceiver
            }
            val connection = (handler as ServerLoginPacketListenerImplAccessor).`valkyrienskies$getConnection`()
            VsNetworkPlatform.finishLogin(connection, handshake, buf)
                ?.let { reason -> handler.disconnect(Component.literal(reason)) }
        }
    }

    fun sendToClient(data: ByteBuf, player: VsiPlayer) {
        val serverPlayer = (player as MinecraftPlayer).player as ServerPlayer
        VsNetworkStatistics.record("send", "s2c", "vs_packet", data.readableBytes())
        ServerPlayNetworking.send(serverPlayer, VS_PACKET_ID, FriendlyByteBuf(data))
    }

    fun sendToServer(data: ByteBuf) {
        VsNetworkStatistics.record("send", "c2s", "vs_packet", data.readableBytes())
        ClientPlayNetworking.send(VS_PACKET_ID, FriendlyByteBuf(data))
    }
}
