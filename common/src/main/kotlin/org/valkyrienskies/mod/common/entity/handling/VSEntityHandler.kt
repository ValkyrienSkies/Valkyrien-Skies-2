package org.valkyrienskies.mod.common.entity.handling

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.world.entity.Entity
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship

interface VSEntityHandler {

    /**
     * freshEntityInShipyard
     *
     * Gets called when a new entity gets made in the shipyard
     */
    fun freshEntityInShipyard(entity: Entity, ship: Ship)

    fun entityRemovedFromShipyard(entity: Entity, ship: Ship)

    /**
     * ApplyRenderTransform
     *
     * Gets called every render when the entity lives in the shipyard
     * You need to apply a transform the matrixStack is identity when this is called
     */
    fun <T : Entity> applyRenderTransform(
        ship: ClientShip, entity: T, entityRenderer: EntityRenderer<T>,
        x: Double, y: Double, z: Double,
        rotationYaw: Float, partialTicks: Float,
        matrixStack: PoseStack, buffer: MultiBufferSource, packedLight: Int
    )

    /**
     * Position set from vehicle,
     *
     * Gets called everytime the entity is getting set from a vehicle
     *
     * Should call self.setPos(x, y, z)
     */
    fun positionSetFromVehicle(self: Entity, vehicle: Entity, x: Double, y: Double, z: Double)

    /**
     * Shipyard entities will stay in the shipyard, but world entities are unable to teleport into the shipyard
     */
    fun getTeleportPos(self: Entity, pos: Vector3d): Vector3d

    /**
     * Gets called every render of a passenger when the vehicle (this handler) lives in the shipyard
     * The matrix stack is filled with the existing transform
     */
    fun applyRenderOnMountedEntity(
        ship: ClientShip, self: Entity, passenger: Entity, partialTicks: Float, matrixStack: PoseStack
    )
}
