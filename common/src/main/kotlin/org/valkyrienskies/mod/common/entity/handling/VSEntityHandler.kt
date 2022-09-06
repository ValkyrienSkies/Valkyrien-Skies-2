package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import org.valkyrienskies.core.api.ClientShip

interface VSEntityHandler {

    // TODO for teleporting out of shipyard if needed
    // fun onEntityAppear(entity: Entity)

    // matrixStack is in camera space
    fun <T : Entity> applyTransform(
        ship: ClientShip, entity: T, entityRenderer: EntityRenderer<T>,
        x: Double, y: Double, z: Double,
        rotationYaw: Float, partialTicks: Float,
        matrixStack: PoseStack, buffer: MultiBufferSource, packedLight: Int
    )
}
