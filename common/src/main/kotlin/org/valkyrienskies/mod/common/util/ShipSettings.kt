package org.valkyrienskies.mod.common.util

import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.core.api.ships.saveAttachment
import org.valkyrienskies.mod.common.config.ShipRenderer

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

data class ClientShipSettings(
    /**
     * If null it will use the default
     */
    var renderer: ShipRenderer? = null
)

val ServerShip.settings: ShipSettings
    get() = getAttachment<ShipSettings>() ?: ShipSettings().also { saveAttachment(it) }

val ClientShip.settings: ClientShipSettings
    get() = ClientShipSettings() //TODO have a way to store/pull from server a per ship client preference
