package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import org.joml.Vector3d
import org.valkyrienskies.core.api.events.CollisionEvent
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.util.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object ImpactFractureHandler {
    private val logger by logger()

    var enabled: Boolean = true

    var minApproachSpeed: Double = 4.0

    var maxCarveRadius: Int = 3

    private val CARVE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE or
        Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_MOVE_BY_PISTON

    private data class PendingImpact(val shipId: ShipId, val point: Vector3d, val approach: Double)

    private val queues = ConcurrentHashMap<DimensionId, ConcurrentLinkedQueue<PendingImpact>>()

    @JvmStatic
    fun logRegistered() {
        logger.info("[impact-debug] collision listener registered; enabled=$enabled minApproachSpeed=$minApproachSpeed")
    }

    @JvmStatic
    fun onCollision(event: CollisionEvent) {
        if (!enabled) return
        var worstApproach = 0.0
        var worstPoint: Vector3d? = null
        for (contact in event.contactPoints) {
            val approach = abs(contact.velocity.dot(contact.normal))
            if (approach > worstApproach) {
                worstApproach = approach
                worstPoint = Vector3d(contact.position) // copy: event objects may be recycled
            }
        }
        logger.debug("[impact-debug] collision ships=${event.shipIdA}/${event.shipIdB} " +
            "maxApproach=${"%.2f".format(worstApproach)} threshold=$minApproachSpeed")
        if (worstPoint == null || worstApproach < minApproachSpeed) return
        val q = queues.computeIfAbsent(event.dimensionId) { ConcurrentLinkedQueue() }
        q.add(PendingImpact(event.shipIdA, Vector3d(worstPoint), worstApproach))
        q.add(PendingImpact(event.shipIdB, Vector3d(worstPoint), worstApproach))
    }

    @JvmStatic
    fun tick(level: ServerLevel) {
        if (!enabled) return
        val q = queues[level.dimensionId] ?: return
        while (true) {
            val impact = q.poll() ?: break
            carve(level, impact)
        }
    }

    private fun carve(level: ServerLevel, impact: PendingImpact) {
        val ship = level.shipObjectWorld.allShips.getById(impact.shipId) as? ServerShip
        if (ship == null) {
            carveWorld(level, impact)
            return
        }
        if (ship.isStatic) return // don't shatter pinned ships

        val local = ship.worldToShip.transformPosition(Vector3d(impact.point))
        val cx = floor(local.x).toInt()
        val cy = floor(local.y).toInt()
        val cz = floor(local.z).toInt()

        val radius = min(maxCarveRadius, max(1, floor(impact.approach / minApproachSpeed).toInt()))
        val r2 = radius * radius
        val air = Blocks.AIR.defaultBlockState()
        var removed = 0
        for (dx in -radius..radius) for (dy in -radius..radius) for (dz in -radius..radius) {
            if (dx * dx + dy * dy + dz * dz > r2) continue
            val pos = BlockPos(cx + dx, cy + dy, cz + dz)
            if (!level.getBlockState(pos).isAir) {
                level.setBlock(pos, air, CARVE_FLAGS)
                removed++
            }
        }
        if (removed > 0) {
            logger.info(
                "Impact fracture: carved $removed blocks from ship ${impact.shipId} " +
                    "at ($cx,$cy,$cz) approach=${"%.1f".format(impact.approach)}"
            )
            ValkyrienSkiesMod.splitHandler.queueSplit(level, impact.shipId)
        }
    }

    private fun carveWorld(level: ServerLevel, impact: PendingImpact) {
        val cx = floor(impact.point.x).toInt()
        val cy = floor(impact.point.y).toInt()
        val cz = floor(impact.point.z).toInt()
        val radius = min(maxCarveRadius, max(1, floor(impact.approach / minApproachSpeed).toInt()))
        val r2 = radius * radius
        val blocks = HashSet<BlockPos>()
        for (dx in -radius..radius) for (dy in -radius..radius) for (dz in -radius..radius) {
            if (dx * dx + dy * dy + dz * dz > r2) continue
            val pos = BlockPos(cx + dx, cy + dy, cz + dz)
            if (!level.getBlockState(pos).isAir) blocks.add(pos)
        }
        if (blocks.isEmpty()) {
            logger.info("[impact-debug] world impact at ($cx,$cy,$cz) but no solid terrain within radius $radius")
            return
        }
        try {
            val newShip = ShipAssembler.assembleToShip(level, blocks, 1.0)
            logger.info("Impact fracture: gouged ${blocks.size} world blocks into debris ship ${newShip.id} at ($cx,$cy,$cz)")
        } catch (e: Throwable) {
            logger.error("Impact fracture: failed to assemble world debris at ($cx,$cy,$cz)", e)
        }
    }
}
