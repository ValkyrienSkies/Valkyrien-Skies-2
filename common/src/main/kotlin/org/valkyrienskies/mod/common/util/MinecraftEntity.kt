package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.valkyrienskies.core.game.IEntity
import org.valkyrienskies.core.game.ships.ShipId

/**
 * There should only be exactly one instance of [MinecraftEntity] for a given [Entity].
 * Do not create more than 1 of these objects for an [Entity]!
 */
class MinecraftEntity(private val entity: Entity) : IEntity {
    override var addedMovementLastTick: Vector3d = Vector3d()
        private set
    override var addedYawRotLastTick: Double = 0.0
        private set
    override var lastShipStoodOn: ShipId? = null
        set(value) {
            ticksSinceStoodOnShip = 0
            field = value
        }
    override var ticksSinceStoodOnShip: Int = 0
        private set

    // TODO: Idk yet, but TODO!
    override fun applyAddedMovement() {
        if (lastShipStoodOn == null) {
            // We are on the ground, reset added velocities
            addedMovementLastTick.zero()
            addedYawRotLastTick = 0.0
            return
        }
        if (ticksSinceStoodOnShip < 5) {
            // TODO: Recompute the velocity added by the ship
        } else {
            addedMovementLastTick.mul(.9)
            addedYawRotLastTick *= .9
        }
        entity.setPos(
            entity.x + addedMovementLastTick.x,
            entity.y + addedMovementLastTick.y,
            entity.z + addedMovementLastTick.z
        )
    }
}
