package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.mod.common.toWorldCoordinates

// Vanilla shooters compute projectile trajectories by reading target.getX/getY/getZ (or getY(D)/getEyeY) and subtracting the shooter's world position. For shipyard-positioned targets the raw getters return shipyard coords (~10⁷-block magnitudes); without projection the resulting direction vector fires the projectile into the void. Off-ship entities pass through unchanged via Level.toWorldCoordinates' null-ship fallback.
object ShipyardEntityProjection {

    @JvmStatic
    fun worldPosition(entity: Entity): Vec3 = entity.level().toWorldCoordinates(entity.position())

    @JvmStatic
    fun worldX(entity: Entity): Double = worldPosition(entity).x

    @JvmStatic
    fun worldY(entity: Entity): Double = worldPosition(entity).y

    @JvmStatic
    fun worldZ(entity: Entity): Double = worldPosition(entity).z

    /** Vanilla `Entity.getY(yFraction)` returns `entity.y + bbHeight * yFraction`; project that body-fraction Y to world. */
    @JvmStatic
    fun worldYAt(entity: Entity, yFraction: Double): Double =
        entity.level().toWorldCoordinates(Vec3(entity.x, entity.getY(yFraction), entity.z)).y

    /** Eye-height world Y — projects `(x, getEyeY(), z)`. */
    @JvmStatic
    fun worldEyeY(entity: Entity): Double =
        entity.level().toWorldCoordinates(Vec3(entity.x, entity.eyeY, entity.z)).y
}
