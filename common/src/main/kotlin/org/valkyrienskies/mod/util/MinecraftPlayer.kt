package org.valkyrienskies.mod.util

import net.minecraft.entity.player.PlayerEntity
import org.joml.Vector3d
import org.valkyrienskies.core.game.IPlayer
import java.lang.ref.WeakReference
import java.util.*

/**
 * We use this wrapper around [PlayerEntity] to create [IPlayer] objects used by vs-core.
 */
class MinecraftPlayer(playerObject: PlayerEntity, private val playerID: UUID) : IPlayer {

    // Hold a weak reference to avoid memory leaks
    private val playerEntityReference: WeakReference<PlayerEntity> = WeakReference(playerObject)

    override fun getPosition(dest: Vector3d): Vector3d {
        val player = playerEntityReference.get()
        player as PlayerEntity
        return dest.set(player.x, player.y, player.z)
    }

    override fun getPlayerID(): UUID {
        return playerID
    }

    override fun hashCode(): Int {
        return playerID.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) {
            return true
        }
        if (other is MinecraftPlayer) {
            return playerID == other.playerID
        }
        return false
    }
}