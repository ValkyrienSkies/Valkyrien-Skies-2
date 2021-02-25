package org.valkyrienskies.mod.forge.common

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraftforge.fml.network.PacketDistributor
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketToClientSender

object VSForgeServerToClientPacketSender : IVSPacketToClientSender<ServerPlayerEntity> {
    override fun sendToClient(vsPacket: IVSPacket, player: ServerPlayerEntity) {
        VSForgeNetworking.vsForgeChannel.send(PacketDistributor.PLAYER.with { player }, MessageVSPacket(vsPacket))
    }
}