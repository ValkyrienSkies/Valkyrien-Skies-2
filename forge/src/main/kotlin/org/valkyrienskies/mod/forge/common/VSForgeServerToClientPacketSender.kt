package org.valkyrienskies.mod.forge.common

import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.fml.network.PacketDistributor
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketToClientSender

object VSForgeServerToClientPacketSender : IVSPacketToClientSender<ServerPlayer> {
    override fun sendToClient(vsPacket: IVSPacket, player: ServerPlayer) {
        VSForgeNetworking.vsForgeChannel.send(PacketDistributor.PLAYER.with { player }, MessageVSPacket(vsPacket))
    }
}
