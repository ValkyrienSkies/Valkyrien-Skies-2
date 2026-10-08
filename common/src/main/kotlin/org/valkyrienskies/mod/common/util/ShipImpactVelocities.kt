package org.valkyrienskies.mod.common.util

import org.joml.Vector3d
import org.valkyrienskies.core.api.events.CollisionEvent
import org.valkyrienskies.core.api.physics.ContactPoint
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.internal.world.VsiPhysLevel
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object ShipImpactVelocities {
    private data class Motion(val position: Vector3d, val velocity: Vector3d, val omega: Vector3d)
    private val frames = ConcurrentHashMap<String, Map<Long, Motion>>()

    /** Copy motion before the physics engine changes it during a collision. */
    @PhysTickOnly
    fun capture(world: PhysLevel) {
        val level = world as? VsiPhysLevel ?: return
        frames[world.dimension] = level.getAllPhysShips().associate { ship ->
            ship.id to Motion(Vector3d(ship.transform.position), Vector3d(ship.velocity), Vector3d(ship.omega))
        }
    }

    @PhysTickOnly
    fun closingSpeed(event: CollisionEvent, contact: ContactPoint): Double {
        val measured = ShipInteractionMath.closingSpeed(contact.velocity, contact.normal)
        val frame = frames[event.dimensionId] ?: return measured
        val a = frame[event.shipIdA]
        val b = frame[event.shipIdB]
        if (a == null && b == null) return measured
        fun velocity(motion: Motion?) = if (motion == null) Vector3d() else
            ShipInteractionMath.pointVelocity(motion.velocity, motion.omega, motion.position, contact.position)
        val before = velocity(a).sub(velocity(b))
        return max(measured, ShipInteractionMath.closingSpeed(before, contact.normal))
    }

    fun clear() = frames.clear()
}
