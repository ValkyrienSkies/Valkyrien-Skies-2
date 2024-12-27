package org.valkyrienskies.mod.common.util

import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip

/**
 * A attachment that stores ship specific settings.
 */
data class ShipSettings(
    /**
     * Should the ship chunks try to generate? If true it will generate chunks in the shipyard.
     * You normally don't want this EVER
     */
    var shouldGenerateChunks: Boolean = false,

    /**
     * If true this ship will change dimensions when it touches a portal
     */
    var changeDimensionOnTouchPortals: Boolean = true
)

val LoadedServerShip.settings: ShipSettings
    get() = getAttachment<ShipSettings>() ?: ShipSettings().also { setAttachment(it) }
