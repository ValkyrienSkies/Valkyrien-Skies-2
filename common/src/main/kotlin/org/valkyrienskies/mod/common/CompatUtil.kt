package org.valkyrienskies.mod.common

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft

object CompatUtil {
    fun toSameSpaceAs(level: Level?, position: Vector3dc, targetShip: Ship?): Vector3d {
        val result = position as Vector3d
        val ship = level?.getShipObjectManagingPos(result)
        if (ship != targetShip) {
            ship?.shipToWorld?.transformPosition(result)
            targetShip?.worldToShip?.transformPosition(result)
        }
        return result
    }

    fun toSameSpaceAs(level: Level?, position: Vec3, targetShip: Ship?): Vec3 {
        return toSameSpaceAs(level, position.toJOML(), targetShip).toMinecraft()
    }

    fun toSameSpaceAs(level: Level?, position: Vector3dc, target: Vector3dc): Vector3d {
        return toSameSpaceAs(level, position, level.getShipManagingPos(target))
    }

    fun toSameSpaceAs(level: Level?, position: Vec3, target: Vec3): Vec3 {
        return toSameSpaceAs(level, position.toJOML(), level.getShipManagingPos(target))
            .toMinecraft()
    }

    fun toSameSpaceAs(level: Level?, position: Vec3, target: BlockPos): Vec3 {
        return toSameSpaceAs(level, position.toJOML(), level.getShipManagingPos(target))
            .toMinecraft()
    }

    fun toSameSpaceAs(level: Level?, px: Double, py: Double, pz: Double, target: Vec3): Vec3 {
        return toSameSpaceAs(level, Vector3d(px, py, pz), target.toJOML())
            .toMinecraft()
    }

    fun toSameSpaceAs(level: Level?, px: Double, py: Double, pz: Double, target: BlockPos): Vec3 {
        return toSameSpaceAs(level, Vector3d(px, py, pz), level.getShipManagingPos(target))
            .toMinecraft()
    }
}
