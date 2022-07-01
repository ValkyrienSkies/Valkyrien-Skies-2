package org.valkyrienskies.mod.common.networking.impl

import net.minecraft.client.Minecraft
import org.valkyrienskies.core.game.ships.ShipDataCommon
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
                // Convert [ShipData] to [ShipDataCommon], to prevent us from accidentally running server code
                val shipDataCommon = ShipDataCommon(
                    it.id, it.name, it.chunkClaim, it.chunkClaimDimension, it.physicsData, it.shipTransform,
                    it.prevTickShipTransform, it.shipAABB, it.shipVoxelAABB, it.shipActiveChunksSet
                )
                shipWorld.queryableShipData.addShipData(shipDataCommon)
            } else {
                // Update the next ship transform
                shipWorld.shipObjects[it.id]?.updateNextShipTransform(it.shipTransform)
                // Update the ship physics bounding box
                shipWorld.shipObjects[it.id]?.shipData?.shipVoxelAABB = it.shipVoxelAABB
            }
        }
    }
}
