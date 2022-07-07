package org.valkyrienskies.mod.common.config

object VSGameConfig {
    /**
     * Vanilla Behaviour: TRUE
     *
     * By default, the vanilla server prevents block interacts past a certain distance to prevent cheat clients from
     * breaking blocks halfway across the map. This approach breaks down in the face of extremely large ships,
     * where the distance from the block origin to the nearest face is greater than the interact distance check allows.
     */
    @JvmStatic
    var enableInteractDistanceChecks = true
}
