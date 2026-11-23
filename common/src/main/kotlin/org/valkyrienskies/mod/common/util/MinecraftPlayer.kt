package org.valkyrienskies.mod.common.util

import net.minecraft.Util
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
 * We use this wrapper around [PlayerEntity] to create [VsiPlayer] objects used by vs-core.
 *
 * A server player keeps one wrapper for its whole connection: when Minecraft replaces the entity (respawn, leaving the
 * End), the new entity takes the wrapper over through [rebind], so vs-core's ship tracking and network session stay with
 * the connection and see the current entity. Wrappers compare by identity.
 */
class MinecraftPlayer(playerObject: Player) : VsiPlayer {

    // Read from the entity: wrappers are created before Player's constructor sets the profile UUID.
    override val uuid: UUID get() = playerEntityReference.get()?.uuid ?: Util.NIL_UUID

    // Hold a weak reference to avoid memory leaks
    @Volatile
    var playerEntityReference: WeakReference<Player> = WeakReference(playerObject)
        private set

    val player: Player get() = playerEntityReference.get()!!

    override val isAdmin: Boolean
        get() = player.hasPermissions(4)

    override val canModifyServerConfig: Boolean
        get() = vsCore.hooks.isPhysicalClient || player.hasPermissions(4)

    override val dimension: DimensionId
        get() = player.level().dimensionId

    override val forceWatchingShips = HashSet<ShipId>()

    override fun getPosition(dest: Vector3d): Vector3d {
        return dest.set(player.x, player.y, player.z)
    }

    override fun getPlayerState(): VsiPlayerState {
        val mountedShipAndPos = getShipMountedToData(player)
        return VsiPlayerState(
            Vector3d(player.x, player.y, player.z),
            Vector3d(Vector3d(player.x - player.xo, player.y - player.yo, player.z - player.zo)),
            dimension,
            mountedShipAndPos?.shipMountedTo?.id,
            mountedShipAndPos?.mountPosInShip,
        )
    }

    /** Points this wrapper at [replacement], the entity that took over this player's connection. */
    fun rebind(replacement: Player) {
        playerEntityReference = WeakReference(replacement)
    }
}
