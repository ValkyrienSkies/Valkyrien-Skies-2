package org.valkyrienskies.mod.common

import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.apigame.world.ClientShipWorldCore
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.apigame.world.VSPipeline

interface IShipObjectWorldProvider {
    val shipObjectWorld: ShipWorld?
}

interface IShipObjectWorldServerProvider : IShipObjectWorldProvider {
    override val shipObjectWorld: ServerShipWorldCore?
    val vsPipeline: VSPipeline?
}

interface IShipObjectWorldClientProvider : IShipObjectWorldProvider {
    override val shipObjectWorld: ClientShipWorldCore?
}

interface IShipObjectWorldClientCreator {
    fun createShipObjectWorldClient()
    fun deleteShipObjectWorldClient()
}
