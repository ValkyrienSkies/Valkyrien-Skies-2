package org.valkyrienskies.mod.common

import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft

object CompatUtil {
    fun toSameSpaceAs(level: Level, position: Vector3d, target: Vector3d): Vector3d {
        var ship: Ship?
        if (level.isBlockInShipyard(position.x, position.y, position.z) && !level.isBlockInShipyard(
                target.x,
                target.y,
                target.z
            )
        ) {
            ship = level.getShipManagingPos(position)
            ship!!.shipToWorld.transformPosition(position)
        }
        if (!level.isBlockInShipyard(position.x, position.y, position.z) && level.isBlockInShipyard(
                target.x,
                target.y,
                target.z
            )
        ) {
            ship = level.getShipManagingPos(target)
            ship!!.worldToShip.transformPosition(position)
        }
        return position
    }

    fun toSameSpaceAs(level: Level, position: Vec3, target: Vec3): Vec3 {
        return toSameSpaceAs(level, position.toJOML(), target.toJOML()).toMinecraft()
    }

    fun toSameSpaceAs(level: Level, px: Double, py: Double, pz: Double, target: Vec3): Vec3 {
        return toSameSpaceAs(level, Vector3d(px, py, pz), target.toJOML()).toMinecraft()
    }
}
