package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.impl.util.component1
import org.valkyrienskies.core.impl.util.component2
import org.valkyrienskies.core.impl.util.component3
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import kotlin.math.atan2
import kotlin.math.sqrt

object WorldEntityHandler : VSEntityHandler {
    override fun freshEntityInShipyard(entity: Entity, ship: Ship) {
        moveEntityFromShipyardToWorld(entity, ship)
    }

    override fun entityRemovedFromShipyard(entity: Entity, ship: Ship) {
        // Do nothing
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

    override fun positionSetFromVehicle(self: Entity, vehicle: Entity, x: Double, y: Double, z: Double) {
        val (wx, wy, wz) = self.level.toWorldCoordinates(x, y, z)
        self.setPos(wx, wy, wz)
    }

    override fun getTeleportPos(self: Entity, pos: Vector3d): Vector3d {
        return self.level.toWorldCoordinates(pos)
    }

    override fun applyRenderOnMountedEntity(
        ship: ClientShip, self: Entity, passenger: Entity, partialTicks: Float, matrixStack: PoseStack
    ) {
    }

    private fun moveEntityFromShipyardToWorld(entity: Entity, ship: Ship): Vector3dc {
        val newPos = ship.shipToWorld.transformPosition(entity.position().toJOML())
        entity.setPos(newPos.x, newPos.y, newPos.z)
        entity.xo = entity.x
        entity.yo = entity.y
        entity.zo = entity.z

        val newPosInShipLocal = Vector3d(newPos).sub(ship.transform.positionInWorld)
        val shipVelocity = Vector3d(ship.velocity) // ship linear velocity
            .add(Vector3d(ship.omega).cross(newPosInShipLocal)) // angular velocity
            .mul(0.05) // Tick velocity

        val entityVelocity = ship.transform.shipToWorldRotation.transform(entity.deltaMovement.toJOML())

        entity.deltaMovement = Vector3d(entityVelocity)
            .add(shipVelocity)
            .toMinecraft()

        val direction = ship.transform.shipToWorldRotation.transform(entity.lookAngle.toJOML())
        val yaw = -atan2(direction.x, direction.z)
        val pitch = -atan2(direction.y, sqrt((direction.x * direction.x) + (direction.z * direction.z)))
        entity.yRot = (yaw * (180 / Math.PI)).toFloat()
        entity.xRot = (pitch * (180 / Math.PI)).toFloat()
        entity.yRotO = entity.yRot
        entity.xRotO = entity.xRot

        if (entity is AbstractHurtingProjectile) {
            val power = Vector3d(entity.xPower, entity.yPower, entity.zPower)

            ship.transform.shipToWorldRotation.transform(power)

            entity.xPower = power.x
            entity.yPower = power.y
            entity.zPower = power.z
        }

        return newPos
    }
}
