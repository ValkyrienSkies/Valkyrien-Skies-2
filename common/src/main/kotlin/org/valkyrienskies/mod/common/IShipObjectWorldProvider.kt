package org.valkyrienskies.mod.common

import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.VsiPipeline
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources

interface IShipObjectWorldProvider {
    val shipObjectWorld: ShipWorld?
}

interface IShipObjectWorldServerProvider : IShipObjectWorldProvider {
    override val shipObjectWorld: VsiServerShipWorld?
    val vsPipeline: VsiPipeline?
    val fractionalFluidSources: FractionalFluidSources?
}

interface IShipObjectWorldClientProvider : IShipObjectWorldProvider {
    override val shipObjectWorld: VsiClientShipWorld?
}

interface IShipObjectWorldClientCreator {
    fun createShipObjectWorldClient()
    fun deleteShipObjectWorldClient()
}
