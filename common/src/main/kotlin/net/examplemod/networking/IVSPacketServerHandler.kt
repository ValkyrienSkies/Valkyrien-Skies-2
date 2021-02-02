package net.examplemod.networking

import net.minecraft.server.level.ServerPlayer

interface IVSPacketServerHandler {
    fun handlePacket(vsPacket: IVSPacket, sender: ServerPlayer)
}