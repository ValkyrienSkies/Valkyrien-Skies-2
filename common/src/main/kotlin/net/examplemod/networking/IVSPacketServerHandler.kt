package net.examplemod.networking

/**
 * Handles [IVSPacket]s on the server side
 * @param P: The player object class
 */
interface IVSPacketServerHandler<P> {
    fun handlePacket(vsPacket: IVSPacket, sender: P)
}