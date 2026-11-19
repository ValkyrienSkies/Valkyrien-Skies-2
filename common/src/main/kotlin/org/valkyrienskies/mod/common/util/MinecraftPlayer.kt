package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.player.Player
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.core.internal.world.VsiPlayerState
import org.valkyrienskies.core.internal.world.properties.DimensionId
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipMountedToData
import org.valkyrienskies.mod.common.vsCore
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * A connection's stable [VsiPlayer], rebound when respawn replaces its [Player] entity.
 * Identity is deliberately per wrapper, not per account UUID: a reconnect needs fresh tracking,
 * whereas respawn must retain this exact wrapper and all of its existing tracking state.
 */
class MinecraftPlayer(playerObject: Player) : VsiPlayer {

    override val uuid: UUID = playerObject.uuid

    // Network readers may retain this wrapper across a game-thread respawn. Replace the weak
    // reference rather than clearing it, so they see either complete entity reference.
    @Volatile
    var playerEntityReference: WeakReference<Player> = WeakReference(playerObject)
        private set

    fun rebindPlayer(playerObject: Player) {
        require(playerObject.uuid == uuid) { "Cannot rebind a player wrapper to another account" }
        playerEntityReference = WeakReference(playerObject)
    }

    val player: Player get() = playerEntityReference.get()!!

    override val isAdmin: Boolean
        get() = player.hasPermissions(4)

    override val canModifyServerConfig: Boolean
        get() = vsCore.hooks.isPhysicalClient || player.hasPermissions(4)

    override val dimension: DimensionId
        get() = player.level().dimensionId

    override val forceWatchingShips = HashSet<ShipId>()

    override fun getPosition(dest: Vector3d): Vector3d {
        val player = player
        return dest.set(player.x, player.y, player.z)
    }

    override fun getPlayerState(): VsiPlayerState {
        val player = player
        val mountedShipAndPos = getShipMountedToData(player)
        return VsiPlayerState(
            Vector3d(player.x, player.y, player.z),
            Vector3d(Vector3d(player.x - player.xo, player.y - player.yo, player.z - player.zo)),
            player.level().dimensionId,
            mountedShipAndPos?.shipMountedTo?.id,
            mountedShipAndPos?.mountPosInShip,
        )
    }
}
