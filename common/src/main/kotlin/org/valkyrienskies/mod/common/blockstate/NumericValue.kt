package org.valkyrienskies.mod.common.blockstate

import net.minecraft.resources.ResourceLocation

sealed class NumericValue {
    data class Literal(val value: Double) : NumericValue()
    data class Dependent(val targetId: ResourceLocation, val targetState: String, val mult: Double) : NumericValue()
}
