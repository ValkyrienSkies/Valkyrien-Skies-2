package org.valkyrienskies.mod.networking.impl

import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketClientHandler
import org.valkyrienskies.core.networking.impl.VSPacketShipDataList

object VSPacketShipDataClientHandler: IVSPacketClientHandler {
    override fun handlePacket(vsPacket: IVSPacket) {
        vsPacket as VSPacketShipDataList
        println("client got the following packet $vsPacket")
        vsPacket.shipDataList.forEach { println("Received ShipData $it") }
    }
}