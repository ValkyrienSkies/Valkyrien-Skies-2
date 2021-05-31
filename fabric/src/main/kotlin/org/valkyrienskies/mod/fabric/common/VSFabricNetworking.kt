package org.valkyrienskies.mod.fabric.common

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.util.MinecraftPlayer

/**
 * Registers VS with the Fabric networking API.
 */
object VSFabricNetworking {
    private val VS_PACKET_ID = Identifier(ValkyrienSkiesMod.MOD_ID, "vs_packet")

    internal fun registerNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { _, _, buf, _ ->
            VSNetworking.TCP.onReceiveClient(buf.copy())
        }
        ServerPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { _, player, _, buf, _ ->
            VSNetworking.TCP.onReceiveServer(buf.copy(), MinecraftPlayer.wrap(player))
        }
        VSNetworking.TCP.rawSendToServer = { buf ->
            ClientPlayNetworking.send(VS_PACKET_ID, PacketByteBuf(buf))
        }
        VSNetworking.TCP.rawSendToClient = { buf, player ->
            ServerPlayNetworking.send(player.mcPlayer as ServerPlayerEntity, VS_PACKET_ID, PacketByteBuf(buf))
        }
    }
}
