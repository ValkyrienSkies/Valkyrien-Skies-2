package org.valkyrienskies.mod.fabric.common

import org.valkyrienskies.mod.common.VSNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.PacketByteBuf

object VSClientPlayChannelHandler : ClientPlayNetworking.PlayChannelHandler {
    override fun receive(
        client: MinecraftClient,
        handler: ClientPlayNetworkHandler,
        buf: PacketByteBuf,
        responseSender: PacketSender?
    ) {
        VSNetworking.handleVSPacketClient(buf)
    }
}