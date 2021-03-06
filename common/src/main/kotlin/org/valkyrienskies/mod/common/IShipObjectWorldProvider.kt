package org.valkyrienskies.mod.common

import org.valkyrienskies.core.game.ships.ShipObjectClientWorld
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld
import org.valkyrienskies.core.game.ships.ShipObjectWorld

interface IShipObjectWorldProvider {
    val shipObjectWorld: ShipObjectWorld
}

interface IShipObjectWorldServerProvider : IShipObjectWorldProvider {
    override val shipObjectWorld: ShipObjectServerWorld
}

interface IShipObjectWorldClientProvider : IShipObjectWorldProvider {
    override val shipObjectWorld: ShipObjectClientWorld
}
