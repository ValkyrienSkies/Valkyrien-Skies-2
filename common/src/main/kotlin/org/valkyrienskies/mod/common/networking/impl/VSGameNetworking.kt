package org.valkyrienskies.mod.common.networking.impl

import net.minecraft.client.Minecraft
import org.valkyrienskies.core.networking.impl.PacketShipDataList
import org.valkyrienskies.core.networking.simple.registerClientHandler
import org.valkyrienskies.mod.common.shipObjectWorld

object VSGameNetworking {
    fun registerHandlers() {
        PacketShipDataList::class.registerClientHandler {
            val gameLevel = Minecraft.getInstance().level
            val shipWorld = gameLevel?.shipObjectWorld ?: return@registerClientHandler

            it.ships.forEach {
                if (shipWorld.queryableShipData.getById(it.id) == null) {
                    // Convert [ShipDataCommon] to [ShipDataClient]
                    shipWorld.queryableShipData.addShipData(it)
                } else {
                    // Update the next ship transform
                    shipWorld.shipObjects[it.id]?.updateNextShipTransform(it.shipTransform)
                }
            }
        }
    }
}
