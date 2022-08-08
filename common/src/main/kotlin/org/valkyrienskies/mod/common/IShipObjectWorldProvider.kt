package org.valkyrienskies.mod.common

import org.valkyrienskies.core.game.ships.VSWorld
import org.valkyrienskies.core.game.ships.VSWorldClient
import org.valkyrienskies.core.game.ships.VSWorldServer
import org.valkyrienskies.core.pipelines.VSPipeline

interface IShipObjectWorldProvider {
    val VSWorld: VSWorld<*>
}

interface IShipObjectWorldServerProvider : IShipObjectWorldProvider {
    override val VSWorld: VSWorldServer
    val vsPipeline: VSPipeline
}

interface IShipObjectWorldClientProvider : IShipObjectWorldProvider {
    override val VSWorld: VSWorldClient
}

interface IShipObjectWorldClientCreator {
    fun createShipObjectWorldClient()
    fun deleteShipObjectWorldClient()
}
