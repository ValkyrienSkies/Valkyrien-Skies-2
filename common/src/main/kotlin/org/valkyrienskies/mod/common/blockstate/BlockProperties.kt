package org.valkyrienskies.mod.common.blockstate

import net.minecraft.resources.ResourceLocation

data class StateProperties(
    val priority: Int,
    val solid: SolidStateProperties? = null,
    val liquid: LiquidStateProperties? = null,
    val displacement: DisplacementStateProperties? = null,
    val medium: MediumStateProperties? = null,
)

data class PendingStateProperties(
    val priority: Int,
    val solid: PendingSolidStateProperties? = null,
    val liquid: PendingLiquidStateProperties? = null,
    val displacement: DisplacementStateProperties? = null,
    val medium: PendingMediumStateProperties? = null
)

data class PendingTagProperties(
    val properties: PendingStateProperties,
    val exclude: Set<ResourceLocation>,
    val block: Boolean
)
