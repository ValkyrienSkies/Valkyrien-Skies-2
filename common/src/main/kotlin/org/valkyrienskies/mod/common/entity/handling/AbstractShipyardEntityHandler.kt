package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import org.joml.Quaternionf
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft

abstract class AbstractShipyardEntityHandler : VSEntityHandler {
    override fun freshEntityInShipyard(entity: Entity, ship: Ship) {
        // do nothing
    }

    override fun entityRemovedFromShipyard(entity: Entity, ship: Ship) {
        // do nothing
    }

    override fun <T : Entity> applyRenderTransform(
        ship: ClientShip, entity: T, entityRenderer: EntityRenderer<T>, x: Double, y: Double, z: Double,
        rotationYaw: Float, partialTicks: Float, matrixStack: PoseStack, buffer: MultiBufferSource, packedLight: Int
    ) {
        val transform = ship.renderTransform

        val entityPosition = entity.getPosition(partialTicks)
        val transformed = transform.shipToWorld.transformPosition(entityPosition.toJOML())

        val camX = x - entityPosition.x
        val camY = y - entityPosition.y
        val camZ = z - entityPosition.z
        val offset = entityRenderer.getRenderOffset(entity, partialTicks)
        val scale = transform.shipToWorldScaling

        matrixStack.translate(transformed.x + camX, transformed.y + camY, transformed.z + camZ)
        matrixStack.mulPose(Quaternionf(transform.shipToWorldRotation))
        matrixStack.scale(scale.x().toFloat(), scale.y().toFloat(), scale.z().toFloat())
        matrixStack.translate(offset.x, offset.y, offset.z)
    }

    override fun positionSetFromVehicle(self: Entity, vehicle: Entity, x: Double, y: Double, z: Double) {
        self.setPos(x, y, z)
    }

    override fun getTeleportPos(self: Entity, pos: Vector3d): Vector3d {
        return pos
    }

    override fun applyRenderOnMountedEntity(
        ship: ClientShip, self: Entity, passenger: Entity, partialTicks: Float, matrixStack: PoseStack
    ) {
        // TODO: somewhere else position is already applied in the matrix stack
        // EW: i think it was in entity dragging logic
        matrixStack.mulPose(Quaternionf(ship.renderTransform.shipToWorldRotation))
    }
}
