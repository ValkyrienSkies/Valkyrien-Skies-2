package org.valkyrienskies.mod.api

import net.minecraft.core.Direction
import org.valkyrienskies.mod.common.entity.ShipMountingEntity

/**
 * A utility class that is attached to ships and gives the inputs from a player
 * mounted to a [ShipMountingEntity]
 */
@Deprecated("This API will be removed at a later date. " +
    "Addons should make their own seat entities and handle their own inputs. ")
class SeatedControllingPlayer(val seatInDirection: Direction) {
    var forwardImpulse: Float = 0.0f
    var leftImpulse: Float = 0.0f
    var upImpulse: Float = 0.0f
    var sprintOn: Boolean = false
    var cruise: Boolean = false
}
