package net.examplemod.networking

/**
 * Handles [IVSPacket]s on the client side
 */
interface IVSPacketClientHandler {
    fun handlePacket(vsPacket: IVSPacket)
}