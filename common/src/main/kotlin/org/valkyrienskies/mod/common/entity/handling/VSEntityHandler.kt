package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ClientShip
import org.valkyrienskies.core.api.Ship

interface VSEntityHandler {

    /**
     * freshEntityInShipyard
     * Gets called when a new entity gets made in the shipyard
     */
    fun freshEntityInShipyard(entity: Entity, ship: Ship, position: Vector3dc)

    /**
     * ApplyRenderTransform
     * Gets called every render when the entity lives in the shipyard
     * You need to apply a transform the matrixStack is identity when this is called
     */
    fun <T : Entity> applyRenderTransform(
        ship: ClientShip, entity: T, entityRenderer: EntityRenderer<T>,
        x: Double, y: Double, z: Double,
        rotationYaw: Float, partialTicks: Float,
        matrixStack: PoseStack, buffer: MultiBufferSource, packedLight: Int
    )
}
