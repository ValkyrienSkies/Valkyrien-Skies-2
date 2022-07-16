package org.valkyrienskies.mod.api

import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.core.api.ShipUser

interface ShipBlockEntity : ShipUser {
    /**
     * Ship for the block entity, it will be set on creation
     */
    override var ship: Ship?
}
