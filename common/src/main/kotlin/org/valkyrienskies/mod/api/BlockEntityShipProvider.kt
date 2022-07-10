package org.valkyrienskies.mod.api

import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.core.api.ShipProvider

interface BlockEntityShipProvider : ShipProvider {
    /**
     * Ship for the block entity, it will be set on creation
     */
    override var ship: Ship?
}
