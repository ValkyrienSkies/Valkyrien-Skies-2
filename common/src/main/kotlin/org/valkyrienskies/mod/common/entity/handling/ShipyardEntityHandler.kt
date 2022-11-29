package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.core.util.x
import org.valkyrienskies.core.util.y
import org.valkyrienskies.core.util.z
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import kotlin.math.roundToInt

object ShipyardEntityHandler : VSEntityHandler {
    override fun freshEntityInShipyard(entity: Entity, ship: Ship, position: Vector3dc) {}

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
        matrixStack.mulPose(transform.shipToWorldRotation.toMinecraft())
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
        matrixStack.mulPose(ship.renderTransform.shipToWorldRotation.toMinecraft())
    }

    override fun onEntityMove(self: Entity, ship: Ship, position: Vector3dc): Vector3dc =
        if (!ship.shipAABB!!.expand(1)// expand happens bcs containsPoint is exclusive but the AABB is inclusive
                .containsPoint(position.x.roundToInt(), position.y.roundToInt(), position.z.roundToInt())
        ) {
            WorldEntityHandler.moveEntityFromShipyardToWorld(self, ship, position)
        } else
            position
}
