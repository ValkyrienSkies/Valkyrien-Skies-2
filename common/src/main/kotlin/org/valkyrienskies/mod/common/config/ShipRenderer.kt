package org.valkyrienskies.mod.common.config

import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.util.settings

enum class ShipRenderer {
    VANILLA,
    FLYWHEEL
}

val ClientShip.shipRenderer: ShipRenderer
    get() = settings.renderer ?: VSGameConfig.CLIENT.defaultRenderer
