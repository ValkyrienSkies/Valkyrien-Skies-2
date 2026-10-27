package org.valkyrienskies.mod.common.util

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3dc
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore

object DragInfoReporter {
    val shipDragValues = Long2ObjectOpenHashMap<Vector3dc>()
    val shipLiftValues = Long2ObjectOpenHashMap<Vector3dc>()

    fun tick(level: ServerLevel) {
        // We don't sync this data on servers, so don't bother calculating it
        if (!vsCore.hooks.isPhysicalClient) return

        // We use another class to guard the loading of Minecraft to singleplayer only
        if (!DragInfoReporterClient.isHitboxesEnabled()) {
            if (!shipDragValues.isEmpty()) {
                shipDragValues.clear()
                shipLiftValues.clear()
            }
            return
        }
        if (level !== level.server.overworld()) {
            return
        }
        shipDragValues.clear()
        shipLiftValues.clear()
        level.shipObjectWorld.loadedShips.forEach { ship ->
            shipDragValues.put(ship.id, ship.dragController!!.getDragForce())
            shipLiftValues.put(ship.id, ship.dragController!!.getLiftForce())
        }
    }
}
