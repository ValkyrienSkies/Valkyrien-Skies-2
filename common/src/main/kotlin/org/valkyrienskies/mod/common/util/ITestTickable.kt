package org.valkyrienskies.mod.common.util

import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.api.world.properties.DimensionId

@OptIn(VsBeta::class)
interface ITestTickable {

    fun matchesDimension(dimensionId: DimensionId): Boolean
    fun physTick(physLevel: PhysLevel, delta: Double)
}
