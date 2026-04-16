package org.valkyrienskies.mod.api

import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.api.world.properties.DimensionId

/**
 * An interface for entities that want to receive updates on the Physics Thread. Receives a PhysLevel.
 *
 * Do not access non-volatile/atomic Entity variables from physTick, as that isn't thread-safe! Make sure anything accessed between ticks is thread-safe, either by using atomic variables or concurrent queues.
 */
interface EntityPhysicsListener {
    /**
     * Called every physics tick.
     *
     * @param physLevel The physics world, containing information about the current state of the physics simulation. IE. Ships, Constraints, etc.
     *
     * @see [PhysLevel]
     */
    fun physTick(physLevel: PhysLevel)

    var dimension: DimensionId
}
