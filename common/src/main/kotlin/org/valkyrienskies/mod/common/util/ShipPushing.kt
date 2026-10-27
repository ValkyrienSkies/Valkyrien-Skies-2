package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.getShipStoodOn
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.world.clipIncludeShips
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

@OptIn(GameTickOnly::class)
object ShipPushing {
    private data class Session(val dimension: String, val ship: Long, val block: BlockPos,
        val point: Vector3d, val inward: Vector3d, var movement: Vector3d, var inputTick: Long)
    private val sessions = HashMap<UUID, Session>()
    private val drives = ConcurrentHashMap<UUID, PlayerShipPushDrive>()
    const val REACH = 2.5

    internal fun eligible(player: Player) = player.isAlive && !player.isSpectator && !player.isPassenger &&
        player.isShiftKeyDown && player.mainHandItem.isEmpty

    @JvmStatic
    fun tryStart(player: Player, hand: InteractionHand, hit: BlockHitResult): Boolean {
        if (hand != InteractionHand.MAIN_HAND || !VSGameConfig.SERVER.ShipInteractions.playerShipPushing ||
            !eligible(player)) return false
        val level = player.level()
        val ship = level.getLoadedShipManagingPos(hit.blockPos) ?: return false
        if ((ship as? ServerShip)?.isStatic == true || player.getShipStoodOn()?.id == ship.id) return false
        val point = hit.location.toJOML()
        // Block use receives the hit point in the ship model coordinates.
        if (!point.isFinite || point.x < hit.blockPos.x - 0.01 || point.x > hit.blockPos.x + 1.01 ||
            point.y < hit.blockPos.y - 0.01 || point.y > hit.blockPos.y + 1.01 ||
            point.z < hit.blockPos.z - 0.01 || point.z > hit.blockPos.z + 1.01) return false
        val worldPoint = ship.shipToWorld.transformPosition(point, Vector3d())
        if (worldPoint.distanceSquared(player.eyePosition.toJOML()) > REACH * REACH) return false
        if (level is ServerLevel) {
            if (!visible(player, hit.blockPos, worldPoint)) return false
            stop(player.uuid)
            sessions[player.uuid] = Session(level.dimensionId, ship.id, hit.blockPos.immutable(), point,
                hit.direction.normal.toJOMLD().negate(), Vector3d(), level.gameTime)
        }
        return true
    }

    fun receiveInput(player: ServerPlayer, active: Boolean, left: Float, forward: Float) {
        if (!active || !eligible(player) || !left.isFinite() || !forward.isFinite()) {
            stop(player.uuid)
            return
        }
        val session = sessions[player.uuid] ?: return
        val yaw = Math.toRadians(player.yRot.toDouble())
        val x = left.toDouble().coerceIn(-1.0, 1.0)
        val z = forward.toDouble().coerceIn(-1.0, 1.0)
        val movement = Vector3d(x * cos(yaw) - z * sin(yaw), 0.0, z * cos(yaw) + x * sin(yaw))
        if (movement.lengthSquared() > 1.0) movement.normalize()
        session.movement = movement
        session.inputTick = player.level().gameTime
        // Update input at once. The game tick checks reach before it refreshes the command.
        drives[player.uuid]?.setMovement(movement)
    }

    @JvmStatic
    fun tick(level: ServerLevel) {
        val config = VSGameConfig.SERVER.ShipInteractions
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val (id, session) = iterator.next()
            if (session.dimension != level.dimensionId) continue
            val player = level.server.playerList.getPlayer(id)
            val ship = level.shipObjectWorld.loadedShips.getById(session.ship)
            if (!config.playerShipPushing || player == null || player.level() !== level || !eligible(player) ||
                level.gameTime - session.inputTick > 5 || ship == null || ship.isStatic ||
                player.getShipStoodOn()?.id == ship.id || !level.hasChunkAt(session.block) ||
                level.getLoadedShipManagingPos(session.block)?.id != session.ship ||
                level.getBlockState(session.block).getCollisionShape(level, session.block).isEmpty) {
                iterator.remove()
                drives.remove(id)
                continue
            }
            val point = ship.shipToWorld.transformPosition(session.point, Vector3d())
            val inward = ship.transform.rotation.transform(session.inward, Vector3d())
            val eye = player.eyePosition.toJOML()
            if (point.distanceSquared(eye) > REACH * REACH || point.sub(eye, Vector3d()).dot(inward) < -0.05 ||
                !visible(player, session.block, point)) {
                iterator.remove()
                drives.remove(id)
                continue
            }
            if (session.movement.dot(inward) <= 0.0) {
                drives.remove(id)
                continue
            }
            drives[id] = PlayerShipPushDrive(session.dimension, ship.id, Vector3d(session.point),
                Vector3d(session.inward), Vector3d(session.movement), eye, config.playerPushForce,
                config.playerPushSpeed, REACH, System.nanoTime() + 250_000_000L)
        }
    }

    private fun visible(player: Player, block: BlockPos, point: Vector3d): Boolean {
        val eye = player.eyePosition
        val end = point.toMinecraft()
        val ray = end.subtract(eye)
        val hit = player.level().clipIncludeShips(ClipContext(eye, end.add(ray.normalize().scale(0.05)),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
        return hit.type == HitResult.Type.BLOCK && hit.blockPos == block
    }

    fun physTick(level: PhysLevel) {
        val now = System.nanoTime()
        drives.values.forEach { it.tick(level, now) }
    }

    private fun stop(id: UUID) {
        sessions.remove(id)
        drives.remove(id)
    }

    @JvmStatic
    fun clear() {
        sessions.clear()
        drives.clear()
    }
}
