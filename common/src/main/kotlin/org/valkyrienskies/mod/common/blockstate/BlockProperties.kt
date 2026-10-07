package org.valkyrienskies.mod.common.blockstate

import org.joml.primitives.AABBic
import java.util.function.Supplier

data class PendingBlockProperties(
    val priority: Int,
    val solid: PendingSolidProperties? = null,
    val medium: PendingMediumProperties? = null,
    val displacement: AABBic? = null
) : Supplier<Int> {
    override fun get(): Int {
        return priority
    }
}


data class PendingSolidProperties(
    val mass: NumericValue,
    val friction: NumericValue,
    val elasticity: NumericValue,
    val hardness: NumericValue,
    val noCollision: Boolean = false,
    val shapeOverride: AABBic? = null
)

data class PendingMediumProperties(
    val dragCoefficient: NumericValue,
    val shape: AABBic? = null
)

data class SolidProperties(
    val mass: Double,
    val friction: Double,
    val elasticity: Double,
    val hardness: Double,
    val noCollision: Boolean = false,
    val shapeOverride: AABBic? = null
)

data class MediumProperties(
    val dragCoefficient: Double,
    val shape: AABBic? = null
)
