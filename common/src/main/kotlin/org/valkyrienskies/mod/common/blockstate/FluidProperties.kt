package org.valkyrienskies.mod.common.blockstate

import net.minecraft.resources.ResourceLocation
import org.joml.Vector3d
import org.joml.primitives.AABBic
import java.util.function.Supplier

data class FluidTagProperties(
    val priority: Int,
    val properties: PendingFluidProperties,
    val exclusions: Collection<ResourceLocation>
) : Supplier<Int> {
    override fun get(): Int {
        return priority
    }
}

data class PendingFluidProperties(
    val priority: Int,
    val properties: PendingLiquidProperties
) : Supplier<Int> {
    override fun get(): Int {
        return priority
    }
}


data class PendingLiquidProperties(
    val density: NumericValue,
    val dragCoefficient: NumericValue,
    val velocity: Vector3d,
    val shapeOverride: AABBic? = null
)

