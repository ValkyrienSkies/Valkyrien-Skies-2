package net.examplemod.networking

import io.netty.buffer.ByteBuf

/**
 * Custom networking code for VS to allow for compatibility with Fabric and Forge.
 * Handlers registering packets and their handlers, as well as writing packets to bytes and processing packets when they arrive.
 *
 * @param P: The player object class
 */
class VSPacketRegistry<P> {

    // The ID that will be given to the next packet registered by [registerVSPacket]
    private var nextRegistryId: Int = 0

    // Maps [IVSPacket] class types to their packet id
    private val classToIdMap = HashMap<Class<*>, Int>()

    // Maps packet ids to the supplier that creates a new empty version of that packet
    private val idToSupplierMap = HashMap<Int, () -> IVSPacket>()

    // Maps packet ids to the handler that runs them on the client
    private val classToClientHandlerMap = HashMap<Class<*>, IVSPacketClientHandler>()

    // Maps packet ids to the handler that runs them on the server
    private val classToServerHandlerMap = HashMap<Class<*>, IVSPacketServerHandler<P>>()

    /**
     * Registers a new packet type
     * @param clazz: The class of the packet type
     * @param supplier: A supplier that creates a new empty instance of the packet type. Used when converting bytes to packets.
     * @param clientHandler: An object that runs the code when this packet is received by the client
     * @param serverHandler: An object that runs the code when this packet is received by the server
     */
    fun <T : IVSPacket> registerVSPacket(
        clazz: Class<T>,
        supplier: () -> T,
        clientHandler: IVSPacketClientHandler?,
        serverHandler: IVSPacketServerHandler<P>?
    ) {
        if (classToIdMap[clazz] != null) {
            throw IllegalArgumentException("Already registered packet handlers for $clazz")
        }
        val classId = nextRegistryId++
        classToIdMap[clazz] = classId
        idToSupplierMap[classId] = supplier
        if (clientHandler != null) classToClientHandlerMap[clazz] = clientHandler
        if (serverHandler != null) classToServerHandlerMap[clazz] = serverHandler
    }

    fun writeVSPacket(vsPacket: IVSPacket, byteBuf: ByteBuf) {
        if (!classToIdMap.containsKey(vsPacket::class.java)) {
            throw IllegalArgumentException("No packetId found for $vsPacket")
        }
        val packetId: Int = classToIdMap[vsPacket::class.java]!!
        byteBuf.writeInt(packetId)
        vsPacket.write(byteBuf)
    }

    private fun readVSPacket(byteBuf: ByteBuf): IVSPacket {
        val packetId: Int = byteBuf.readInt()
        if (!idToSupplierMap.containsKey(packetId)) {
            throw IllegalArgumentException("No supplier found for packetId $packetId")
        }
        val vsPacket: IVSPacket = idToSupplierMap[packetId]!!.invoke()
        vsPacket.read(byteBuf)
        return vsPacket
    }

    fun handleVSPacketClient(byteBuf: ByteBuf) {
        val vsPacket = readVSPacket(byteBuf)
        if (!classToClientHandlerMap.containsKey(vsPacket::class.java)) {
            throw IllegalArgumentException("No client handler found for $vsPacket")
        }
        classToClientHandlerMap[vsPacket::class.java]!!.handlePacket(vsPacket)
    }

    fun handleVSPacketServer(byteBuf: ByteBuf, sender: P) {
        val vsPacket = readVSPacket(byteBuf)
        if (!classToServerHandlerMap.containsKey(vsPacket::class.java)) {
            throw IllegalArgumentException("No server handler found for $vsPacket")
        }
        classToServerHandlerMap[vsPacket::class.java]!!.handlePacket(vsPacket, sender)
    }

}