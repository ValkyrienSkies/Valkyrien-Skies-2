package org.valkyrienskies.mod.common.blockstate

import org.joml.primitives.AABBic

data class SolidStateProperties(
    val mass: Double,
    val friction: Double,
    val elasticity: Double,
    val hardness: Double,
    val noCollision: Boolean = false,
    val shapeOverride: AABBic? = null
)

data class PendingSolidStateProperties(
    val mass: NumericValue,
    val friction: NumericValue,
    val elasticity: NumericValue,
    val hardness: NumericValue,
    val noCollision: Boolean = false,
    val shapeOverride: AABBic? = null
)

data class MediumStateProperties(
    val dragCoefficient: Double,
    val shape: AABBic? = null
)

data class PendingMediumStateProperties(
    val dragCoefficient: NumericValue,
    val shape: AABBic? = null
)
