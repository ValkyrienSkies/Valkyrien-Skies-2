package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ClientShip
import org.valkyrienskies.core.api.Ship

interface VSEntityHandler {

    fun updatedPosition(entity: Entity, ship: Ship, position: Vector3dc)

    // matrixStack is in camera space
    fun <T : Entity> applyRenderTransform(
        ship: ClientShip, entity: T, entityRenderer: EntityRenderer<T>,
        x: Double, y: Double, z: Double,
        rotationYaw: Float, partialTicks: Float,
        matrixStack: PoseStack, buffer: MultiBufferSource, packedLight: Int
    )
}
