package org.valkyrienskies.mod.common.blockstate

import org.joml.Vector3d
import org.joml.primitives.AABBic

data class LiquidStateProperties(
    val density: Double,
    val dragCoefficient: Double,
    val velocity: Vector3d,
    val shapeOverride: AABBic? = null
)

data class PendingLiquidStateProperties(
    val density: NumericValue,
    val dragCoefficient: NumericValue,
    val velocity: Vector3d,
    val shapeOverride: AABBic? = null
)

data class DisplacementStateProperties(val shape: AABBic? = null)
