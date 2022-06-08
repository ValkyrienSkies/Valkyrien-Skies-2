package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.player.Player
import org.joml.Vector3d
import org.valkyrienskies.core.game.IPlayer
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * We use this wrapper around [PlayerEntity] to create [IPlayer] objects used by vs-core.
 */
class MinecraftPlayer(playerObject: Player, override val uuid: UUID) : IPlayer {

    // Hold a weak reference to avoid memory leaks
    val playerEntityReference: WeakReference<Player> = WeakReference(playerObject)

    override fun getPosition(dest: Vector3d): Vector3d {
        val player = playerEntityReference.get()
        player as Player
        return dest.set(player.x, player.y, player.z)
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) {
            return true
        }
        if (other is MinecraftPlayer) {
            return uuid == other.uuid
        }
        return false
    }
}
