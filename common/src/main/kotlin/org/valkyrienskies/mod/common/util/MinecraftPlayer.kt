package org.valkyrienskies.mod.common.util

import net.minecraft.world.entity.player.Player
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.core.apigame.world.PlayerState
import org.valkyrienskies.core.apigame.world.properties.DimensionId
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getPassengerPos
import org.valkyrienskies.mod.common.getShipObjectEntityMountedTo
import org.valkyrienskies.mod.common.vsCore
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * We use this wrapper around [PlayerEntity] to create [IPlayer] objects used by vs-core.
 */
class MinecraftPlayer(playerObject: Player) : IPlayer {

    override val uuid: UUID = playerObject.uuid

    // Hold a weak reference to avoid memory leaks
    val playerEntityReference: WeakReference<Player> = WeakReference(playerObject)

    val player: Player get() = playerEntityReference.get()!!

    override val isAdmin: Boolean
        get() = player.hasPermissions(4)

    override val canModifyServerConfig: Boolean
        get() = vsCore.hooks.isPhysicalClient || player.hasPermissions(4)

    override val dimension: DimensionId
        get() = player.level().dimensionId

    override fun getPosition(dest: Vector3d): Vector3d {
        return dest.set(player.x, player.y, player.z)
    }

    override fun getPlayerState(): PlayerState {
        var mountedShip: ShipId? = null
        var mountedPos: Vector3dc? = null
        player.level().getShipObjectEntityMountedTo(player)?.let {
            mountedShip = it.id
            mountedPos = player.vehicle!!.getPassengerPos(player.myRidingOffset, 0.0f)
        }
        return PlayerState(
            Vector3d(player.x, player.y, player.z),
            Vector3d(Vector3d(player.x - player.xo, player.y - player.yo, player.z - player.zo)),
            dimension,
            mountedShip,
            mountedPos,
        )
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
