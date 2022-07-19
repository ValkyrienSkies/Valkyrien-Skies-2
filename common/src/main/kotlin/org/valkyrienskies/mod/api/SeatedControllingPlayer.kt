package org.valkyrienskies.mod.api

import net.minecraft.core.Direction

class SeatedControllingPlayer(val seatInDirection: Direction) {
    var forwardImpulse: Float = 0.0f
    var leftImpulse: Float = 0.0f
    var upImpulse: Float = 0.0f
}
