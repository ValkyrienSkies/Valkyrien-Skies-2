package org.valkyrienskies.mod

import io.netty.buffer.ByteBuf
import org.valkyrienskies.mod.networking.impl.VSPacketShipDataClientHandler
import org.valkyrienskies.mod.networking.impl.VSPacketShipDataList
import net.minecraft.server.level.ServerPlayer
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketToClientSender
import org.valkyrienskies.core.networking.VSPacketRegistry

/**
 * Registers the [org.valkyrienskies.core.networking.IVSPacket]s, and stores [org.valkyrienskies.core.networking.IVSPacketToClientSender]
 * and [org.valkyrienskies.core.networking.IVSPacketToServerSender] packet senders.
 */
object VSNetworking {

    private val vsPacketRegistry = VSPacketRegistry<ServerPlayer>()
    lateinit var shipDataPacketToClientSender: IVSPacketToClientSender<ServerPlayer>

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