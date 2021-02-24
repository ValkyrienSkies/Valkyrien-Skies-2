package org.valkyrienskies.mod.networking.impl

import net.minecraft.client.MinecraftClient
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketClientHandler
import org.valkyrienskies.core.networking.impl.VSPacketShipDataList
import org.valkyrienskies.mod.VSGameUtils

object VSPacketShipDataClientHandler : IVSPacketClientHandler {
    override fun handlePacket(vsPacket: IVSPacket) {
        vsPacket as VSPacketShipDataList
        println("client got the following packet $vsPacket")

        val gameWorld = MinecraftClient.getInstance().world
        val shipWorld = VSGameUtils.getShipObjectWorldFromWorld(gameWorld!!)

        vsPacket.shipDataList.forEach {
            if (shipWorld.queryableShipData.getShipDataFromUUID(it.shipUUID) == null) {
                shipWorld.queryableShipData.addShipData(it)
            }
        }
    }
}