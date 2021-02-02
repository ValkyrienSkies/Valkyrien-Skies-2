package net.examplemod.networking

interface IVSPacketClientHandler {
    fun handlePacket(vsPacket: IVSPacket)
}