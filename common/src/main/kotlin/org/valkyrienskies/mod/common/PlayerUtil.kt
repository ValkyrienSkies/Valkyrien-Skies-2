package org.valkyrienskies.mod.common

import com.google.common.collect.MapMaker
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.ships.ShipObject
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor
import java.util.concurrent.ConcurrentMap
import kotlin.math.atan2
import kotlin.math.sqrt

object PlayerUtil {

    private val prevPosInfo: ConcurrentMap<Player, TempPlayerPosInfo> = MapMaker().weakKeys().makeMap()

    private data class TempPlayerPosInfo(val yaw: Float, val headYaw: Float, val pitch: Float, val pos: Vec3)

    @JvmStatic
    fun transformPlayerTemporarily(player: Player, world: Level, blockInShip: BlockPos) =
        transformPlayerTemporarily(player, world.getShipObjectManagingPos(blockInShip))

    @JvmStatic
    fun transformPlayerTemporarily(player: Player, ship: ShipObject?) {
        if (ChunkAllocator.isBlockInShipyard(player.x, player.y, player.z)) {
            // player is already in shipyard
            return
        }

        prevPosInfo[player] = TempPlayerPosInfo(player.yRot, player.yHeadRot, player.xRot, player.position())

        if (ship != null) {
            val shipMatrix = ship.shipTransform.worldToShipMatrix
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
    }

    @JvmStatic
    fun untransformPlayer(player: Player) {
        val info = prevPosInfo.remove(player) ?: return
        player.xRot = info.pitch

        player.yRot = info.yaw
        player.yHeadRot = info.headYaw

        (player as EntityAccessor).setPosNoUpdates(info.pos)
    }

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
        transformPlayerTemporarily(player, ship)

        try {
            return inside()
        } finally {
            untransformPlayer(player)
        }
    }
}
