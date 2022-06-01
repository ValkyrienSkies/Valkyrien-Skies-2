package org.valkyrienskies.mod.common

import net.minecraft.entity.player.PlayerEntity
import org.valkyrienskies.core.game.ships.ShipObject
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toVec3d
import org.valkyrienskies.mod.mixin.accessors.entity.AccessorEntity

object PlayerUtil {

    //updates player to 'live' in ship space for everything executed in the inside lambda
    //is used for emulating the environment when you interact with a block
    fun <T> transformPlayerTemporarily(player: PlayerEntity, ship: ShipObject?, inside: () -> T): T {
        val tmpYaw = if (player.world.isClient)
            player.yaw
        else
            player.headYaw
        val tmpPitch = player.pitch
        val tmpPos = player.pos

        if (ship != null) {
            val shipMatrix = ship.shipData.shipTransform
                .worldToShipMatrix
            val direction = shipMatrix.transformDirection(
                player.rotationVector.toJOML()
            )
            val position = shipMatrix.transformPosition(
                player.pos.toJOML()
            )
            val yaw = Math.atan2(direction.x, -direction.z) //yaw in radians
            val pitch = Math.asin(-direction.y)
            player.headYaw = (yaw * (180 / Math.PI)).toFloat() + 180
            player.pitch = (pitch * (180 / Math.PI)).toFloat()
            (player as AccessorEntity).setPosNoUpdates(position.toVec3d())
        }

        try {
            return inside()
        } finally {
            player.pitch = tmpPitch

            if (player.world.isClient)
                player.yaw = tmpYaw
            else
                player.headYaw = tmpYaw

            (player as AccessorEntity).setPosNoUpdates(tmpPos)
        }
    }
}
