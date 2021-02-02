package net.examplemod

import io.netty.buffer.ByteBuf
import net.examplemod.networking.IVSPacket
import net.examplemod.networking.IVSPacketSender
import net.examplemod.networking.VSPacketRegistry
import net.examplemod.networking.impl.VSPacketShipDataClientHandler
import net.examplemod.networking.impl.VSPacketShipDataList
import net.minecraft.server.level.ServerPlayer

/**
 * Registers the [net.examplemod.networking.IVSPacket]s, and has [net.examplemod.networking.IVSPacketSender]s used to send
 * packets between clients and server.
 */
object VSNetworking {

    private val vsPacketRegistry = VSPacketRegistry<ServerPlayer>()
    lateinit var shipDataToClientPacketSender: IVSPacketSender<ServerPlayer>

    internal fun registerVSPackets() {
        vsPacketRegistry.registerVSPacket(
            VSPacketShipDataList::class.java,
            { VSPacketShipDataList.createEmptyVSPacketShipDataList() },
            VSPacketShipDataClientHandler,
            null
        )
    }

    fun handleVSPacketClient(byteBuf: ByteBuf) {
        vsPacketRegistry.handleVSPacketClient(byteBuf)
    }

    fun handleVSPacketServer(byteBuf: ByteBuf, sender: ServerPlayer) {
        vsPacketRegistry.handleVSPacketServer(byteBuf, sender)
    }

    fun writeVSPacket(vsPacket: IVSPacket, byteBuf: ByteBuf) {
        vsPacketRegistry.writeVSPacket(vsPacket, byteBuf)
    }
}