package org.valkyrienskies.mod.client

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import org.joml.Vector3dc
import org.valkyrienskies.core.game.ships.ShipObjectClient

interface IVSCamera {
    fun setupWithShipMounted(
        level: BlockGetter, renderViewEntity: Entity, thirdPerson: Boolean, thirdPersonReverse: Boolean,
        partialTicks: Float, shipMountedTo: Pair<ShipObjectClient, Vector3dc>
    )
}
