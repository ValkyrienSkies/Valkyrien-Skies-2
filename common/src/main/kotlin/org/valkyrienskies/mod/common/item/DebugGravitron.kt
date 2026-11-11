package org.valkyrienskies.mod.common.item

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ShipTeleportData
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.internal.ships.VsiServerShip
import org.valkyrienskies.mod.common.assembly.ShipWelder
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.SplittingDisablerAttachment
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.common.world.clipIncludeShips
import java.util.UUID

@OptIn(GameTickOnly::class)
object DebugGravitron {
    private data class Hold(val level: ServerLevel, val player: UUID, val hand: InteractionHand,
                            val shipId: Long, val anchor: Vector3d, val distance: Double,
                            val rotation: Quaterniond, val wasStatic: Boolean)
    private val holds = LinkedHashMap<UUID, Hold>()

    internal fun isHeld(level: ServerLevel, shipId: Long) = holds.values.any {
        it.level === level && it.shipId == shipId
    }

    fun rightClick(player: ServerPlayer, hand: InteractionHand) {
        if (!canUse(player) || player.getItemInHand(hand).item !is DebugGravitronItem) return
        holds[player.uuid]?.let {
            release(it, false)
            message(player, "released")
            return
        }
        val level = player.serverLevel()
        val hit = trace(player)
        val ship = if (hit.type == HitResult.Type.BLOCK) level.getLoadedShipManagingPos(hit.blockPos) else null
        if (ship == null) { message(player, "not_ship"); return }
        if (isBusy(level, ship) || isHeld(level, ship.id)) {
            message(player, "busy")
            return
        }
        val anchor = ship.worldToShip.transformPosition(hit.location.toJOML())
        holds[player.uuid] = Hold(level, player.uuid, hand, ship.id, anchor,
            hit.location.distanceTo(player.eyePosition).coerceIn(2.0, 64.0),
            Quaterniond(ship.transform.rotation), ship.isStatic)
        stop(level, ship)
        message(player, "grabbed")
    }

    @JvmStatic
    fun freeze(player: ServerPlayer) {
        if (!canUse(player) || player.mainHandItem.item !is DebugGravitronItem) return
        val hold = holds[player.uuid]
        val level = player.serverLevel()
        if (hold != null && hold.level !== level) return
        val hit = if (hold == null) trace(player) else null
        val ship = if (hold != null) level.shipObjectWorld.loadedShips.getById(hold.shipId)
            else hit?.takeIf { it.type == HitResult.Type.BLOCK }?.let { level.getLoadedShipManagingPos(it.blockPos) }
        if (ship == null) { message(player, "not_ship"); return }
        if (isBusy(level, ship) || hold == null && isHeld(level, ship.id)) {
            message(player, "busy")
            return
        }
        stop(level, ship)
        holds.remove(player.uuid)
        message(player, "frozen")
    }

    @JvmStatic
    fun tick(level: ServerLevel) {
        for (hold in holds.values.toList()) {
            if (hold.level.server.getLevel(hold.level.dimension()) !== hold.level) {
                release(hold, hold.wasStatic)
                continue
            }
            if (hold.level !== level) continue
            val player = level.server.playerList.getPlayer(hold.player)
            val ship = level.shipObjectWorld.loadedShips.getById(hold.shipId)
            if (player == null || player.level() !== level || !canUse(player) || ship == null ||
                player.getItemInHand(hold.hand).item !is DebugGravitronItem || isBusy(level, ship)) {
                release(hold, hold.wasStatic)
                continue
            }
            val position = GravitronPlacement.position(player.eyePosition.toJOML(), player.lookAngle.toJOML(),
                hold.distance, hold.anchor, ship.kinematics.positionInModel, ship.transform.scaling, hold.rotation)
            ship.isStatic = true
            move(level, ship, position, hold.rotation)
        }
    }

    @JvmStatic
    fun releaseAll(server: MinecraftServer) {
        holds.values.toList().filter { it.level.server === server }.forEach { release(it, it.wasStatic) }
    }

    private fun release(hold: Hold, makeStatic: Boolean) {
        holds.remove(hold.player)
        hold.level.shipObjectWorld.allShips.getById(hold.shipId)?.isStatic = makeStatic
    }

    private fun stop(level: ServerLevel, ship: LoadedServerShip) {
        ship.isStatic = true
        move(level, ship, Vector3d(ship.kinematics.position), Quaterniond(ship.transform.rotation))
    }

    private fun move(level: ServerLevel, ship: LoadedServerShip, position: Vector3dc, rotation: Quaterniondc) {
        val data = vsCore.newShipTeleportData(newPos = position, newRot = rotation)
        level.shipObjectWorld.teleportShip(ship, object : ShipTeleportData by data {
            override val shouldTeleportJointedShips = false
        })
    }

    private fun trace(player: ServerPlayer) = player.serverLevel().clipIncludeShips(ClipContext(
        player.eyePosition, player.eyePosition.add(player.lookAngle.scale(64.0)),
        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))

    private fun canUse(player: ServerPlayer) = player.isAlive && !player.isSpectator && player.mayBuild() &&
        player.server.playerList.getPlayer(player.uuid) === player

    private fun isBusy(level: ServerLevel, ship: LoadedServerShip) = ShipWelder.isBusy(level, ship.id) ||
        ship.transformProvider != null || (ship as VsiServerShip).kinematicTarget != null ||
        ship.getAttachment<SplittingDisablerAttachment>()?.canSplit() == false

    private fun message(player: ServerPlayer, suffix: String) {
        player.sendSystemMessage(Component.translatable("item.valkyrienskies.debug_gravitron.$suffix"))
    }
}
