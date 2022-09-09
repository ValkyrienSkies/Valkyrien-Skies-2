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
import kotlin.math.atan2
import kotlin.math.sqrt

object WorldEntityHandler : VSEntityHandler {
    override fun freshEntityInShipyard(entity: Entity, ship: Ship, position: Vector3dc) {
        val newPos = ship.shipToWorld.transformPosition(Vector3d(position))
        entity.teleportTo(newPos.x, newPos.y, newPos.z)
        entity.xo = entity.x
        entity.yo = entity.y
        entity.zo = entity.z

        val shipVelocity = Vector3d().add(ship.velocity)
            .add(
                newPos.sub(ship.shipTransform.shipPositionInWorldCoordinates, Vector3d())
                    .cross(ship.omega)
            ).mul(0.05) // Tick velocity

        entity.deltaMovement =
            ship.shipTransform.transformDirectionNoScalingFromShipToWorld(entity.deltaMovement.toJOML(), Vector3d())
                .add(shipVelocity)
                .toMinecraft()

        val direction =
            ship.shipTransform.transformDirectionNoScalingFromShipToWorld(entity.lookAngle.toJOML(), Vector3d())
        val yaw = -atan2(direction.x, direction.z)
        val pitch = -atan2(direction.y, sqrt((direction.x * direction.x) + (direction.z * direction.z)))
        entity.yRot = (yaw * (180 / Math.PI)).toFloat()
        entity.xRot = (pitch * (180 / Math.PI)).toFloat()
        entity.yRotO = entity.yRot
        entity.xRotO = entity.xRot
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
