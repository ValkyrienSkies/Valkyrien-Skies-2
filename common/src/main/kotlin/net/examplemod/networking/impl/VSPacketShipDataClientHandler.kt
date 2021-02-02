package net.examplemod.networking.impl

import net.examplemod.networking.IVSPacket
import net.examplemod.networking.IVSPacketClientHandler

object VSPacketShipDataClientHandler: IVSPacketClientHandler {
    override fun handlePacket(vsPacket: IVSPacket) {
        vsPacket as VSPacketShipDataList
        println("client got the following packet $vsPacket")
        vsPacket.getShipDataList().forEach { println("Received ShipData $it") }
    }
}