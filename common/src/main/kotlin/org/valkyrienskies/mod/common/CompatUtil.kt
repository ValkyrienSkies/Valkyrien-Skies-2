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
    // For now this class contains a single yet very useful function of transforming one position
    // to the basis of another one. In the future we should identify more boilerplate compat code and wrap it into
    // similar functions.

    // Same method is repeated several times with different argument types, as nearly all of our compat code is in Java
    // which lacks type conversion sugar of Kotlin. This makes for cleaner code in mixins themselves because we avoid
    // many usages of VectorConversionUtilsKt just to deal with JOML types used in VS.

    /**
     * Transform an arbitrary position to shipspace of a set ship.
     * This method handles coordinates in worldspace, shipspace of other ships and shipspace of the same ship.
     */
    fun toSameSpaceAs(level: Level?, position: Vector3dc, targetShip: Ship?): Vector3d {
        val result = Vector3d(position)
        val ship = level?.getShipManagingPos(result)
        if (ship != targetShip) {
            ship?.shipToWorld?.transformPosition(result)
            targetShip?.worldToShip?.transformPosition(result)
        }
        return result
    }

    /**
     * Transform [position] to shipspace of a set ship.
     *
     * If [targetShip] is null, position is transformed to worldspace.
     */
    fun toSameSpaceAs(level: Level?, position: Vec3, targetShip: Ship?): Vec3 {
        return toSameSpaceAs(level, position.toJOML(), targetShip).toMinecraft()
    }

    /**
     * Transform [position] to the basis of [target].
     *
     * If [target] is managed by a ship, [position] is transformed to shipspace of that ship; otherwise, transform to
     * worldspace.
     */
    fun toSameSpaceAs(level: Level?, position: Vector3dc, target: Vector3dc): Vector3d {
        return toSameSpaceAs(level, position, level.getShipManagingPos(target))
    }

    /**
     * Transform [position] to the basis of [target].
     *
     * If [target] is managed by a ship, [position] is transformed to shipspace of that ship; otherwise, transform to
     * worldspace.
     */
    fun toSameSpaceAs(level: Level?, position: Vec3, target: Vec3): Vec3 {
        return toSameSpaceAs(level, position.toJOML(), level.getShipManagingPos(target))
            .toMinecraft()
    }

    /**
     * Transform [position] to the basis of [target].
     *
     * If [target] is managed by a ship, [position] is transformed to shipspace of that ship; otherwise, transform to
     * worldspace.
     */
    fun toSameSpaceAs(level: Level?, position: Vec3, target: BlockPos): Vec3 {
        return toSameSpaceAs(level, position.toJOML(), level.getShipManagingPos(target))
            .toMinecraft()
    }

    /**
     * Transform a position ([px], [py], [pz]) to the basis of [target].
     *
     * If [target] is managed by a ship, position is transformed to shipspace of that ship; otherwise, transform to
     * worldspace.
     */
    fun toSameSpaceAs(level: Level?, px: Double, py: Double, pz: Double, target: Vec3): Vec3 {
        return toSameSpaceAs(level, Vector3d(px, py, pz), target.toJOML())
            .toMinecraft()
    }

    /**
     * Transform a position ([px], [py], [pz]) to the basis of [target].
     *
     * If [target] is managed by a ship, position is transformed to shipspace of that ship; otherwise, transform to
     * worldspace.
     */
    fun toSameSpaceAs(level: Level?, px: Double, py: Double, pz: Double, target: BlockPos): Vec3 {
        return toSameSpaceAs(level, Vector3d(px, py, pz), level.getShipManagingPos(target))
            .toMinecraft()
    }
}
