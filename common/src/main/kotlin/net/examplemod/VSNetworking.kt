package net.examplemod

import net.examplemod.networking.IConnection
import net.examplemod.networking.VSPacketRegistry
import net.examplemod.networking.impl.VSPacketShipDataClientHandler
import net.examplemod.networking.impl.VSPacketShipDataList
import net.minecraft.server.level.ServerPlayer

/**
 * Registers the [net.examplemod.networking.IVSPacket]s, and has [net.examplemod.networking.IConnection]s used to send
 * packets between clients and server.
 */
object VSNetworking {

    private val vsPacketRegistry = VSPacketRegistry<ServerPlayer>()
    lateinit var shipDataToClientConnection: IConnection<ServerPlayer>

    internal fun registerVSPackets() {
        vsPacketRegistry.registerVSPacket(
            VSPacketShipDataList::class.java,
            { VSPacketShipDataList.createEmptyVSPacketShipDataList() },
            VSPacketShipDataClientHandler,
            null
        )
    }
}