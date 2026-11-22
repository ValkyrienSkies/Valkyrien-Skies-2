package org.valkyrienskies.mod.common.util

import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos

object ShipAwareHit {

    /**
     * `a - b` where one side may be in the shipyard and the other in world space (Create value-box hit tests:
     * hit location minus block corner). The world-space side is moved into the ship first, using the render
     * transform on the client so it matches what the player clicked.
     */
    @JvmStatic
    fun subtract(level: Level?, a: Vec3, b: Vec3): Vec3 {
        if (level == null) return a.subtract(b)
        val shipA = level.getShipManagingPos(a.x, a.y, a.z)
        val shipB = level.getShipManagingPos(b.x, b.y, b.z)
        return when {
            shipA != null && shipB == null -> a.subtract(toShip(shipA, b))
            shipA == null && shipB != null -> toShip(shipB, a).subtract(b)
            else -> a.subtract(b)
        }
    }

    private fun toShip(ship: Ship, pos: Vec3): Vec3 {
        val transform = if (ship is ClientShip) ship.renderTransform else ship.transform
        return transform.toModel.transformPosition(pos.toJOML()).toMinecraft()
    }
}
