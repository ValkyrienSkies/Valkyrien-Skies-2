package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.logging.LogUtils
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ProjectileUtil
import org.joml.Quaternionf
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import kotlin.math.atan2
import kotlin.math.sqrt

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

    fun moveEntityFromWorldToShipyard(entity: Entity, ship: Ship) =
        moveEntityFromWorldToShipyard(entity, ship, entity.x, entity.y, entity.z)

    fun moveEntityFromWorldToShipyard(entity: Entity, ship: Ship, entityX: Double, entityY: Double, entityZ: Double) {
        val shipyardPos = ship.worldToShip.transformPosition(entityX, entityY, entityZ, Vector3d())
        val relativePos: Vector3d = entity.position().toJOML().sub(ship.transform.positionInWorld)
        val shipPosVelocity = Vector3d(ship.velocity)
            .add(Vector3d(ship.omega).cross(relativePos))
            .mul(0.05)
        val relativeDeltaOnShip: Vector3d = entity.deltaMovement.toJOML().sub(shipPosVelocity)
        ship.worldToShip.transformDirection(relativeDeltaOnShip)
        entity.setPos(shipyardPos.x, shipyardPos.y, shipyardPos.z)
        entity.deltaMovement = relativeDeltaOnShip.toMinecraft()

        entity.xo = shipyardPos.x
        entity.yo = shipyardPos.y
        entity.zo = shipyardPos.z


        val direction : Vector3d
        val yaw : Double
        val pitch : Double

        if (entity is AbstractArrow) {
            direction = entity.deltaMovement.toJOML()
            yaw = atan2(direction.x, direction.z)
            pitch = atan2(direction.y, sqrt((direction.x * direction.x) + (direction.z * direction.z)))
        } else {
            direction = ship.worldToShip.transformDirection(entity.lookAngle.toJOML())
            yaw = atan2(-direction.x, direction.z)
            pitch = atan2(-direction.y, sqrt((direction.x * direction.x) + (direction.z * direction.z)))
        }

        entity.yRot = (yaw * (180 / Math.PI)).toFloat()
        entity.xRot = (pitch * (180 / Math.PI)).toFloat()
        LogUtils.getLogger().info("Yaw {}, Pitch {}", entity.yRot, entity.xRot)
        entity.yRotO = entity.yRot
        entity.xRotO = entity.xRot

        if (entity is AbstractHurtingProjectile) {
            val power = Vector3d(entity.xPower, entity.yPower, entity.zPower)
            ship.worldToShip.transformDirection(power)

            entity.xPower = power.x
            entity.yPower = power.y
            entity.zPower = power.z

            ProjectileUtil.rotateTowardsMovement(entity, 1.0f)
        }
    }
}
