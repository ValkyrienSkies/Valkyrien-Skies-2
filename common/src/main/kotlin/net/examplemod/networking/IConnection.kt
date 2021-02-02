package net.examplemod.networking

/**
 * Sends packets to clients or the server.
 * @param P: The player object class
 */
interface IConnection<P> {
    fun sendToServer(vsPacket: IVSPacket)
    fun sendToClient(vsPacket: IVSPacket, player: P)
}