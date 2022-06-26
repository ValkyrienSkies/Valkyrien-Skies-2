package org.valkyrienskies.mod.fabric.common

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.util.MinecraftPlayer

/**
 * Registers VS with the Fabric networking API.
 */
object VSFabricNetworking {
    internal val VS_PACKET_ID = ResourceLocation(ValkyrienSkiesMod.MOD_ID, "vs_packet")

    /**
     * Only run on client
     */
    internal fun registerClientPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(VS_PACKET_ID) { mc, listener, buf, sender ->
            VSNetworking.TCP.onReceiveClient(buf)
        }
    }

    internal fun injectFabricPacketSenders() {
        VSNetworking.TCP.rawSendToClient = { data, player ->
            val serverPlayer = (player as MinecraftPlayer).playerEntityReference.get() as ServerPlayer
            ServerPlayNetworking.send(serverPlayer, VS_PACKET_ID, FriendlyByteBuf(data))
        }
    }
}
