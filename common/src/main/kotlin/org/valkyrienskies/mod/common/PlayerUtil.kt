package org.valkyrienskies.mod.common

import net.minecraft.world.entity.player.Player
import org.valkyrienskies.core.game.ships.ShipObject
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toVec3d
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor
import kotlin.math.asin
import kotlin.math.atan2

object PlayerUtil {

    // Updates player to 'live' in ship space for everything executed in the inside lambda
    // is used for emulating the environment when you interact with a block
    fun <T> transformPlayerTemporarily(player: Player, ship: ShipObject?, inside: () -> T): T {
        val tmpYaw = player.yRot
        val tmpHeadYaw = player.yHeadRot
        val tmpPitch = player.xRot
        val tmpPos = player.position()

        if (ship != null) {
            val shipMatrix = ship.shipData.shipTransform
                .worldToShipMatrix
            val direction = shipMatrix.transformDirection(
                player.lookAngle.toJOML()
            )
            val position = shipMatrix.transformPosition(
                player.position().toJOML()
            )
            val yaw = atan2(direction.x, -direction.z) // yaw in radians
            val pitch = asin(-direction.y)
            player.yRot = (yaw * (180 / Math.PI)).toFloat() + 180
            player.yHeadRot = player.yRot
            player.xRot = (pitch * (180 / Math.PI)).toFloat()
            (player as EntityAccessor).setPosNoUpdates(position.toVec3d())
        }

        try {
            return inside()
        } finally {
            player.xRot = tmpPitch

            player.yRot = tmpYaw
            player.yHeadRot = tmpHeadYaw

            (player as EntityAccessor).setPosNoUpdates(tmpPos)
        }
    }
}
