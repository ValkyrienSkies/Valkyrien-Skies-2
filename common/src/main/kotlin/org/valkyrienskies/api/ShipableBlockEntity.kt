package org.valkyrienskies.api

import org.valkyrienskies.core.game.ships.ShipObject

interface ShipableBlockEntity {

    fun addedToShip(ship: ShipObject)
    fun removedFromShip()
}
