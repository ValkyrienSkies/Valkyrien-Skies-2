package org.valkyrienskies.mod

import io.netty.buffer.ByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketToClientSender
import org.valkyrienskies.core.networking.VSPacketRegistry
import org.valkyrienskies.core.networking.impl.VSPacketShipDataList
import org.valkyrienskies.mod.networking.impl.VSPacketShipDataClientHandler

/**
 * Registers the [IVSPacket]s, and stores [IVSPacketToClientSender]
 * and [IVSPacketToServerSender] packet senders.
 */
object VSNetworking {

    private val vsPacketRegistry = VSPacketRegistry<ServerPlayerEntity>()
    lateinit var shipDataPacketToClientSender: IVSPacketToClientSender<ServerPlayerEntity>

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

    fun handleVSPacketServer(byteBuf: ByteBuf, sender: ServerPlayerEntity) {
        vsPacketRegistry.handleVSPacketServer(byteBuf, sender)
    }

    fun writeVSPacket(vsPacket: IVSPacket, byteBuf: ByteBuf) {
        vsPacketRegistry.writeVSPacket(vsPacket, byteBuf)
    }
}