package org.valkyrienskies.mod.common.blockstate

import net.minecraft.resources.ResourceLocation
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.SolidBlockShape
import java.util.function.Supplier

data class VSBlockProperties(
    val solid: SolidProperties? = null,
    val medium: MediumProperties? = null,
    val displacement: AABBic? = null
)

data class VSFluidProperties(
    val density: Double,
    val dragCoefficient: Double,
)


data class FluidTagProperties(
    override val priority: Int,
    override val properties: PendingFluidProperties,
    override val exclusions: Collection<ResourceLocation>
) : TagProperties<PendingFluidProperties>(priority, properties, exclusions)

data class BlockTagProperties(
    override val priority: Int,
    override val properties: PendingBlockProperties,
    override val exclusions: Collection<ResourceLocation>
) : TagProperties<PendingBlockProperties>(priority, properties, exclusions)

sealed class TagProperties<T>(
    open val priority: Int,
    open val properties: T,
    open val exclusions: Collection<ResourceLocation>
) : Supplier<Int> {
    override fun get(): Int = priority
}


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

data class PendingFluidProperties(
    val priority: Int,
    val properties: PendingLiquidProperties
) : Supplier<Int> {
    override fun get(): Int {
        return priority
    }
}


data class SolidProperties(
    val mass: Double,
    val friction: Double,
    val elasticity: Double,
    val hardness: Double,
    val shapeOverride: SolidBlockShape?
)

data class MediumProperties(
    val dragCoefficient: Double,
    val shape: AABBic? = null
)


data class PendingSolidProperties(
    val mass: NumericValue,
    val friction: NumericValue,
    val elasticity: NumericValue,
    val hardness: NumericValue,
    val shapeOverride: SolidBlockShape?
)

data class PendingLiquidProperties(
    val density: NumericValue,
    val dragCoefficient: NumericValue,
)

data class PendingMediumProperties(
    val dragCoefficient: NumericValue,
    val shape: AABBic? = null
)

