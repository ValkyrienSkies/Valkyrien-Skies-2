package org.valkyrienskies.mod.client

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipTransform

interface IVSCamera {
    fun setupWithShipMounted(
        level: BlockGetter,
        renderViewEntity: Entity,
        thirdPerson: Boolean,
        thirdPersonReverse: Boolean,
        partialTicks: Float,
        shipMountedTo: ClientShip,
        inShipPlayerPosition: Vector3dc
    )

    val shipMountedRenderTransform: ShipTransform?

    fun resetShipMountedRenderTransform()
    fun setPositionVS(pos: Vec3)

    fun setRotationVS(yaw: Float, pitch: Float, roll: Float)

    fun getZrot() : Float
}
