package org.valkyrienskies.mod.common.util

import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ClientShip
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

@OptIn(VsBeta::class)
val LoadedServerShip.settings: ShipSettings
    get() = getAttachment(ShipSettings::class.java) ?: ShipSettings().also { setAttachment(it) }

data class ClientShipSettings(
    /**
     * If null it will use the default
     */
    var renderer: ShipRenderer? = null
)

val ClientShip.settings: ClientShipSettings
    get() = ClientShipSettings() //TODO have a way to store/pull from server a per ship client preference
