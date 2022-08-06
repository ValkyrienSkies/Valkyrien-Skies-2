package org.valkyrienskies.mod.fabric.common

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.networking.NetworkChannel
import org.valkyrienskies.core.networking.VSNetworkingConfigurator
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixinducks.server.IPlayerProvider

/**
 * Registers VS with the Fabric networking API.
 */
class VSFabricNetworking(
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

        ServerPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { server, player, _, buf, _ ->
            channel.onReceiveServer(buf, (server as IPlayerProvider).getOrCreatePlayer(player))
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
