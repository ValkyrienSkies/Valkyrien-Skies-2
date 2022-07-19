package org.valkyrienskies.mod.api

import org.valkyrienskies.core.api.Ticked

class SeatedControllingPlayer : Ticked {
    var forwardImpulse: Float = 0.0f
    var leftImpulse: Float = 0.0f
    var upImpulse: Float = 0.0f

    override fun tick() {
        println("F: $forwardImpulse, L: $leftImpulse, U: $upImpulse")
    }
}
