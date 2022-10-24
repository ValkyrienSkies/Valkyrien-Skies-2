package org.valkyrienskies.mod.common

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.ships.ShipObject
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor
import kotlin.math.atan2
import kotlin.math.sqrt

object PlayerUtil {
    /**
     * Updates [player] to 'live' in ship space for everything executed in the inside lambda
     * is used for emulating the environment when you interact with a block, [blockInShip]
     */
    @JvmStatic
    fun <T> transformPlayerTemporarily(player: Player, world: Level, blockInShip: BlockPos, inside: () -> T): T =
        transformPlayerTemporarily(player, world.getShipObjectManagingPos(blockInShip), inside)

    /**
     * Updates [player] to 'live' in ship space for everything executed in the inside lambda
     * is used for emulating the environment when you interact with a block
     */
    @JvmStatic
    fun <T> transformPlayerTemporarily(player: Player, ship: ShipObject?, inside: () -> T): T {
        if (ChunkAllocator.isBlockInShipyard(player.x, player.y, player.z)) {
            // player is already in shipyard
            return inside()
        }

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
            val yaw = -atan2(direction.x, direction.z)
            val pitch = -atan2(direction.y, sqrt((direction.x * direction.x) + (direction.z * direction.z)))
            player.yRot = (yaw * (180 / Math.PI)).toFloat()
            player.yHeadRot = player.yRot
            player.xRot = (pitch * (180 / Math.PI)).toFloat()
            (player as EntityAccessor).setPosNoUpdates(position.toMinecraft())
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
