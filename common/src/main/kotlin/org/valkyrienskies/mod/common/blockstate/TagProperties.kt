package org.valkyrienskies.mod.common.blockstate

import net.minecraft.resources.ResourceLocation
import java.util.function.Supplier

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
