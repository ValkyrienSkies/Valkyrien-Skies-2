package org.valkyrienskies.mod.fabric.common

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.apigame.hooks.CoreHooksIn
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.util.MinecraftPlayer

/**
 * Registers VS with the Fabric networking API.
 */
class VSFabricNetworking(
    private val isClient: Boolean
) {
    private val VS_PACKET_ID = ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet")

    private fun registerClientNetworking(hooks: CoreHooksIn) {
        ClientPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { _, _, buf, _ ->
            hooks.onReceiveClient(buf)
        }
    }

    fun register(hooks: CoreHooksIn) {
        if (isClient) {
            registerClientNetworking(hooks)
        }

        ServerPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { _, player, _, buf, _ ->
            hooks.onReceiveServer(buf, player.playerWrapper)
        }
    }

    fun sendToClient(data: ByteBuf, player: IPlayer) {
        val serverPlayer = (player as MinecraftPlayer).player as ServerPlayer
        ServerPlayNetworking.send(serverPlayer, VS_PACKET_ID, FriendlyByteBuf(data))
    }

    fun sendToServer(data: ByteBuf) {
        ClientPlayNetworking.send(VS_PACKET_ID, FriendlyByteBuf(data))
    }
}
