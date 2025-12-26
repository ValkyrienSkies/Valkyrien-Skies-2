package org.valkyrienskies.mod.api

import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.api.world.properties.DimensionId

/**
 * An interface for block entities that want to receive updates on the Physics Thread. Receives a PhysLevel, and a PhysShip if the block entity is on a ship.
 *
 * Do not access non-volatile/atomic Block Entity variables from physTick, as that isn't thread-safe! Make sure anything accessed between ticks is thread-safe, either by using atomic variables or concurrent queues.
 */
@PhysTickOnly
interface BlockEntityPhysicsListener {

    /**
     * Called every physics tick.
     *
     * @param physShip The ship in the physics pipeline stage if this block is on a ship.
     * @param physLevel The physics world, containing information about the current state of the physics simulation. IE. Ships, Constraints, etc.
     *
     * @see [PhysShip]
     * @see [PhysLevel]
     */
    fun physTick(physShip: PhysShip?, physLevel: PhysLevel)

    var dimension: DimensionId
}
