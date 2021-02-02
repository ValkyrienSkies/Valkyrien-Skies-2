package net.examplemod.networking

/**
 * Sends packets to clients or the server.
 * @param P: The player object class
 */
interface IVSPacketSender<P> {
    fun sendToServer(vsPacket: IVSPacket) {
        throw UnsupportedOperationException()
    }

    fun sendToClient(vsPacket: IVSPacket, player: P) {
        throw UnsupportedOperationException()
    }
}