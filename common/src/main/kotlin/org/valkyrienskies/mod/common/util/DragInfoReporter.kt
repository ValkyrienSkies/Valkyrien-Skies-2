package org.valkyrienskies.mod.common.util

import net.minecraft.server.level.ServerLevel
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.mod.common.shipObjectWorld

object DragInfoReporter {
    val shipDragValues = HashMap<ShipId, Vector3dc>()
    val shipLiftValues = HashMap<ShipId, Vector3dc>()

    fun tick(level: ServerLevel) {
        level.shipObjectWorld.loadedShips.forEach { ship ->
            shipDragValues[ship.id] = ship.dragController!!.getDragForce()
            shipLiftValues[ship.id] = ship.dragController!!.getLiftForce()
        }
    }
}
