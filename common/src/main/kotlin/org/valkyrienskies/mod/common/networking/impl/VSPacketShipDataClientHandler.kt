package org.valkyrienskies.mod.common.networking.impl

import net.minecraft.client.Minecraft
import org.valkyrienskies.core.networking.IVSPacket
import org.valkyrienskies.core.networking.IVSPacketClientHandler
import org.valkyrienskies.core.networking.impl.VSPacketShipDataList
import org.valkyrienskies.mod.common.shipObjectWorld

object VSPacketShipDataClientHandler : IVSPacketClientHandler {
    override fun handlePacket(vsPacket: IVSPacket) {
        vsPacket as VSPacketShipDataList

        val gameLevel = Minecraft.getInstance().level
        val shipWorld = gameLevel?.shipObjectWorld ?: return

        vsPacket.shipDataList.forEach {
            if (shipWorld.queryableShipData.getById(it.id) == null) {
                // Convert [ShipDataCommon] to [ShipDataClient]
                shipWorld.queryableShipData.addShipData(it)
            } else {
                // Update the next ship transform
                shipWorld.shipObjects[it.id]?.updateNextShipTransform(it.shipTransform)
                // Update the ship physics bounding box
                shipWorld.shipObjects[it.id]?.shipData?.shipVoxelAABB = it.shipVoxelAABB
            }
        }
    }
}
