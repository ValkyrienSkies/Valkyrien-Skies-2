package org.valkyrienskies.mod.quilt.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.quiltmc.qsl.networking.api.ServerPlayNetworking
import org.quiltmc.qsl.networking.api.client.ClientPlayNetworking
import org.valkyrienskies.core.networking.NetworkChannel
import org.valkyrienskies.core.networking.VSNetworkingConfigurator
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.util.MinecraftPlayer

/**
 * Registers VS with the Quilt networking API.
 */
class VSQuiltNetworking(
    private val isClient: Boolean
) : VSNetworkingConfigurator {
    private val VS_PACKET_ID = ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet")

    fun registerClientNetworking(channel: NetworkChannel) {
        ClientPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { _, _, buf, _ ->
            channel.onReceiveClient(buf)
        }
    }

    override fun configure(channel: NetworkChannel) {
        if (isClient) {
            registerClientNetworking(channel)
        }

        ServerPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { _, player, _, buf, _ ->
            channel.onReceiveServer(buf, player.playerWrapper)
        }

        channel.rawSendToClient = { data, player ->
            val serverPlayer = (player as MinecraftPlayer).player as ServerPlayer
            ServerPlayNetworking.send(serverPlayer, VS_PACKET_ID, FriendlyByteBuf(data))
        }
        channel.rawSendToServer = { data ->
            ClientPlayNetworking.send(VS_PACKET_ID, FriendlyByteBuf(data))
        }
    }
}
