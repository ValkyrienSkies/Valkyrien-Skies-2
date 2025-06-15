package org.valkyrienskies.mod.client

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
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
}
