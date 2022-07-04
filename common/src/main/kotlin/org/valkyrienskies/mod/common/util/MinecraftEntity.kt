package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.game.IEntity
import org.valkyrienskies.core.game.ships.ShipId

/**
 * There should only be exactly one instance of [MinecraftEntity] for a given [Entity].
 * Do not create more than 1 of these objects for an [Entity]!
 */
class MinecraftEntity(private val entity: Entity) : IEntity {
    override var addedMovementLastTick: Vector3dc = Vector3d()
    override var addedYawRotLastTick: Double = 0.0
    override var lastShipStoodOn: ShipId? = null
        set(value) {
            ticksSinceStoodOnShip = 0
            field = value
        }
    override var ticksSinceStoodOnShip: Int = 0

    // Used by the client rendering code
    var cachedLastPosition: Vector3dc? = null
    var restoreCachedLastPosition = false

    override fun applyAddedMovement() {
        // TODO: Delete this
    }
}
