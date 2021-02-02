package net.examplemod.networking

import io.netty.buffer.ByteBuf
import net.minecraft.server.level.ServerPlayer

/**
 * A custom networking api for VS.
 *
 * Not ideal but that's the reality of supporting both forge and fabric.
 */
object VSPacketRegistry {

    private var nextRegistryId: Int = 0
    private val classToIdMap = HashMap<Class<*>, Int>()
    private val idToSupplierMap = HashMap<Int, () -> IVSPacket>()
    private val classToClientHandlerMap = HashMap<Class<*>, IVSPacketClientHandler>()
    private val classToServerHandlerMap = HashMap<Class<*>, IVSPacketServerHandler>()

    fun <T: IVSPacket> registerVSPacketHandler(clazz: Class<*>, supplier: () -> T, clientHandler: IVSPacketClientHandler?, serverHandler: IVSPacketServerHandler?) {
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

    fun handleVSPacketServer(byteBuf: ByteBuf, sender: ServerPlayer) {
        val vsPacket = readVSPacket(byteBuf)
        if (!classToServerHandlerMap.containsKey(vsPacket::class.java)) {
            throw IllegalArgumentException("No server handler found for $vsPacket")
        }
        classToServerHandlerMap[vsPacket::class.java]!!.handlePacket(vsPacket, sender)
    }

}