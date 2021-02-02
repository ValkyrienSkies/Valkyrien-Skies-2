package net.examplemod.fabric

import net.examplemod.VSNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketToClientSender

object VSFabricServerToClientPacketSender : IVSPacketToClientSender<ServerPlayer> {
    override fun sendToClient(vsPacket: IVSPacket, player: ServerPlayer) {
        val byteBuf = PacketByteBufs.create()
        VSNetworking.writeVSPacket(vsPacket, byteBuf)
        ServerPlayNetworking.send(player, VSFabricNetworking.VS_PACKET_ID, byteBuf)
    }
}