package org.valkyrienskies.mod.common.util

import org.valkyrienskies.core.apigame.world.properties.DimensionId

/**
 * Interface used to get the [DimensionId] from Minecraft [Level] objects
 */
interface DimensionIdProvider {
    val dimensionId: DimensionId
}
