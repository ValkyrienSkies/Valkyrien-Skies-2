package net.examplemod

import io.netty.buffer.ByteBuf
import net.examplemod.networking.impl.VSPacketShipDataClientHandler
import net.examplemod.networking.impl.VSPacketShipDataList
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketSender
import org.valkyrienskies.core.networking.VSPacketRegistry

/**
 * Registers the [org.valkyrienskies.core.networking.IVSPacket]s, and has [org.valkyrienskies.core.networking.IVSPacketSender]s used to send
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