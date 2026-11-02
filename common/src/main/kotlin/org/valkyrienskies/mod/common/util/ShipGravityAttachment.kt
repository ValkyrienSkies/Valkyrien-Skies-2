package org.valkyrienskies.mod.common.util

import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.ships.ShipPhysicsListener
import org.valkyrienskies.core.api.world.PhysLevel
import org.jetbrains.annotations.ApiStatus.*

/**
 * This attachment is a unified way for addons to set the gravity per-ship.
 * If you want to set the gravity for a dimension, just should use a datapack.
 *
 * [gravityOverride] represents the acceleration applied to the ship each physics tick
 * (_after_ normal gravity has already been negated).
 *
 * For example a value of `-5` would make the ship fall at 0.5x gravity
 * (assuming the dimension gravity was -10m/s/s)
 *
 * If [gravityOverride] is `null`, this attachment will not apply any force.
 * The ship will have normal gravity for whatever dimension it's in.
 *
 * This class is experimental! It will be removed from the API *entirely*
 * once there is a viable per-ship/body gravity property in core. 
 * It only exists as a _temporary_ solution for addons that need this unified system.
 */
@Experimental
class ShipGravityAttachment(@Volatile var gravityOverride: Double? = null): ShipPhysicsListener {
    override fun physTick(
        physShip: PhysShip, physLevel: PhysLevel
    ) {
        gravityOverride ?: return

        val gravity = physLevel.aerodynamicUtils.getAtmosphereForDimension(physLevel.dimension).third

        // We check that gravityOverride is not null again because you can't trust concurrency
        val newForce = (gravity) + (gravityOverride ?: return)

        physShip.applyWorldForceToModelPos(Vector3d(0.0, newForce*physShip.mass, 0.0))
    }
}
