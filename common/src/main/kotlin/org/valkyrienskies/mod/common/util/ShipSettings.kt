package org.valkyrienskies.mod.common.util

import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.core.api.ships.saveAttachment

data class ShipSettings(
    // Should the ship chunks try to generate? If true it will generate chunks in the shipyard.
    // You normally don't want this EVER
    val shouldGenerateChunks: Boolean = false
)

val ServerShip.settings: ShipSettings
    get() = getAttachment<ShipSettings>() ?: ShipSettings().also { saveAttachment(it) }
