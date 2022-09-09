package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ClientShip
import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft

object WorldEntityHandler : VSEntityHandler {
    override fun updatedPosition(entity: Entity, ship: Ship, position: Vector3dc) {
        val newPos = ship.shipToWorld.transformPosition(Vector3d(position))
        entity.teleportTo(newPos.x, newPos.y, newPos.z)

        val shipVelocity = Vector3d().add(ship.velocity)
            .add(
                newPos.sub(ship.shipTransform.shipPositionInWorldCoordinates, Vector3d())
                    .cross(ship.omega)
            ).mul(0.05) // Tick velocity

        // TODO doesn't fix anything, delta movement gets applied after tp
        entity.deltaMovement =
            ship.shipTransform.transformDirectionNoScalingFromWorldToShip(entity.deltaMovement.toJOML(), Vector3d())
                .add(shipVelocity)
                .toMinecraft()
    }

    override fun <T : Entity> applyRenderTransform(
        ship: ClientShip, entity: T, entityRenderer: EntityRenderer<T>,
        x: Double, y: Double, z: Double,
        rotationYaw: Float, partialTicks: Float,
        matrixStack: PoseStack, buffer: MultiBufferSource, packedLight: Int
    ) {
        val offset = entityRenderer.getRenderOffset(entity, partialTicks)
        matrixStack.translate(x + offset.x, y + offset.y, z + offset.z)
    }
}
