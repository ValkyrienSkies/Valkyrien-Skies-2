package org.valkyrienskies.mod.common

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.common.util.transformPosition

object CompatUtil {
    // Same method is repeated several times with different argument types, as nearly all of our compat code is in Java
    // which lacks type conversion sugar of Kotlin. This makes for cleaner code in mixins themselves because we avoid
    // many usages of VectorConversionUtilsKt just to deal with JOML types used in VS.

    /**
     * Transform an arbitrary position to shipspace of a set ship.
     * This method handles coordinates in worldspace, shipspace of other ships and shipspace of the same ship.
     */
    @JvmOverloads
    fun toSameSpaceAs(level: Level?, position: Vector3dc, targetShip: Ship?, sourceShip: Ship? = null): Vector3d {
        val result = Vector3d(position)
        val ship = sourceShip ?: level?.getShipManagingPos(result)
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
    @JvmOverloads
    fun toSameSpaceAs(level: Level?, position: Vec3, targetShip: Ship?, sourceShip: Ship? = null): Vec3 {
        return toSameSpaceAs(level, position.toJOML(), targetShip, sourceShip).toMinecraft()
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

    /**
     * For [pos] on a ship, combine on-ship light value with light at world space location of that block.
     * Not a replacement for ray casting occlusion check since no other ships are accounted for.
     *
     * Block lighting is added (4 in shipyard + 7 in world = result of 11) while sky lighting is occluded (15 in shipyard + 0 in world = result of 0)
     *
     * If [pos] is not managed by a ship, returns vanilla brightness value at that position.
     */
    @JvmOverloads
    fun getCompoundBrightness(level: Level, lightType: LightLayer, pos: BlockPos, ship: Ship? = null): Int {
        val ship = ship ?: level.getShipManagingPos(pos)
        val listener = level.lightEngine.getLayerListener(lightType)
        if (ship == null) return listener.getLightValue(pos)

        val worldPos = BlockPos.containing(ship.shipToWorld.transformPosition(pos.center))
        return when (lightType) {
            LightLayer.BLOCK -> {
                // Block lighting: combine on-ship light with in-world.
                (listener.getLightValue(pos) + listener.getLightValue(worldPos))
                    .coerceAtMost(15)
            }
            LightLayer.SKY -> {
                // Sky lighting: lower lighting indicates sky occlusion. Choose that one.
                minOf(
                    listener.getLightValue(pos),
                    listener.getLightValue(worldPos)
                )
            }
        }
    }


}
