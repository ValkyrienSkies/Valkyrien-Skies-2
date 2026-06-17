package org.valkyrienskies.mod.common.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.joml.primitives.AABBd
import org.valkyrienskies.mod.common.getShipsIntersecting

object NearbyShipsForPathfinding {

    private val EMPTY: Array<NearbyShip> = emptyArray()

    @JvmStatic
    fun collect(level: Level, min: BlockPos, max: BlockPos): Array<NearbyShip> {
        val aabb = AABBd(
            min.x.toDouble(), min.y.toDouble(), min.z.toDouble(),
            max.x + 1.0, max.y + 1.0, max.z + 1.0
        )
        val list = ArrayList<NearbyShip>()
        for (ship in level.getShipsIntersecting(aabb)) {
            list.add(NearbyShip(ship, ship.worldAABB, ship.transform.worldToShip))
        }
        return if (list.isEmpty()) EMPTY else list.toTypedArray()
    }

    @JvmStatic
    fun empty(): Array<NearbyShip> = EMPTY
}
