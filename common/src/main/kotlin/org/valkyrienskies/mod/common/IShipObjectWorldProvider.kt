package org.valkyrienskies.mod.common

import org.valkyrienskies.core.game.ships.ShipObjectClientWorld
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld
import org.valkyrienskies.core.game.ships.ShipObjectWorld
import org.valkyrienskies.core.pipelines.VSPipeline

interface IShipObjectWorldProvider {
    val shipObjectWorld: ShipObjectWorld<*>
}

interface IShipObjectWorldServerProvider : IShipObjectWorldProvider {
    override val shipObjectWorld: ShipObjectServerWorld
    val vsPipeline: VSPipeline
}

interface IShipObjectWorldClientProvider : IShipObjectWorldProvider {
    override val shipObjectWorld: ShipObjectClientWorld
}

interface IShipObjectWorldClientCreator {
    fun createShipObjectWorldClient()
    fun deleteShipObjectWorldClient()
}
