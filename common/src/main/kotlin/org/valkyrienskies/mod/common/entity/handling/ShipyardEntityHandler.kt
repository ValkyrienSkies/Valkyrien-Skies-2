package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.entity.Entity
import org.valkyrienskies.core.api.ClientShip
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft

object ShipyardEntityHandler : VSEntityHandler {
    override fun manipulateRenderMatrix(ship: ClientShip, entity: Entity, matrixStack: PoseStack, partialTicks: Float) {
        val shipTransform = ship.renderTransform

        val entityPosition = entity.getPosition(partialTicks);
        val transformed = shipTransform.shipToWorldMatrix
            .transformPosition(entityPosition.toJOML())

        matrixStack.translate(-entityPosition.x, -entityPosition.y, -entityPosition.z)
        matrixStack.translate(transformed.x, transformed.y, transformed.z)
        matrixStack.mulPose(shipTransform.shipCoordinatesToWorldCoordinatesRotation.toMinecraft())
    }
}
