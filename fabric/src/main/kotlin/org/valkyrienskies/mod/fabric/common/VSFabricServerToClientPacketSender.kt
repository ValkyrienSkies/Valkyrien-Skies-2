package org.valkyrienskies.mod.fabric.common

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketToClientSender
import org.valkyrienskies.mod.common.VSNetworking

object VSFabricServerToClientPacketSender : IVSPacketToClientSender<ServerPlayerEntity> {
	override fun sendToClient(vsPacket: IVSPacket, player: ServerPlayerEntity) {
		val byteBuf = PacketByteBufs.create()
		VSNetworking.writeVSPacket(vsPacket, byteBuf)
		ServerPlayNetworking.send(player, VSFabricNetworking.VS_PACKET_ID, byteBuf)
	}
}
