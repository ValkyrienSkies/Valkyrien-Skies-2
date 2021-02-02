package net.examplemod.networking.impl

import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketClientHandler

object VSPacketShipDataClientHandler: IVSPacketClientHandler {
    override fun handlePacket(vsPacket: IVSPacket) {
        vsPacket as VSPacketShipDataList
        println("client got the following packet $vsPacket")
        vsPacket.getShipDataList().forEach { println("Received ShipData $it") }
    }
}