package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.entity.Entity
import org.valkyrienskies.core.api.ClientShip

interface VSEntityHandler {

    // TODO for teleporting out of shipyard if needed
    // fun onEntityAppear(entity: Entity)

    // matrixStack is in camera space
    fun manipulateRenderMatrix(ship: ClientShip, entity: Entity, matrixStack: PoseStack, partialTicks: Float)
}
