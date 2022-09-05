package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.entity.Entity
import org.valkyrienskies.core.api.ClientShip

object WorldEntityHandler : VSEntityHandler {
    override fun manipulateRenderMatrix(
        ship: ClientShip, entity: Entity, matrixStack: PoseStack, partialTicks: Float
    ) {
    }
}
