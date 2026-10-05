package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3i
import org.joml.Vector3ic
import org.valkyrienskies.core.api.events.FractureEvent
import org.valkyrienskies.core.api.events.FractureReason
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.impl.api_impl.config.ConfigFracturingMode
import org.valkyrienskies.core.impl.api_impl.config.ConfigPhysicsBackendType
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.util.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

object FractureEventHandler {
    private val logger by logger()

    var enabled: Boolean = true

    private fun isFracturingActive(): Boolean {
        if (!enabled) return false
        val backend = VSCoreConfig.SERVER.physics.physicsBackend
        val isFracturingBackend = backend == ConfigPhysicsBackendType.KRUNCH_KONSTANT ||
            backend == ConfigPhysicsBackendType.KRUNCH_VOX3D
        return isFracturingBackend && VSCoreConfig.SERVER.physics.fracturing != ConfigFracturingMode.OFF
    }

    private data class PendingFracture(
        val parentShipId: ShipId,
        val cells: List<Vector3ic>,
        val reason: FractureReason,
    )

    private val queues = ConcurrentHashMap<DimensionId, ConcurrentLinkedQueue<PendingFracture>>()

    @JvmStatic
    fun onFracture(event: FractureEvent) {
        if (!isFracturingActive()) return
        if (event.cells.isEmpty()) return
        val cellsCopy = event.cells.map { Vector3i(it) as Vector3ic }
        val q = queues.computeIfAbsent(event.dimensionId) { ConcurrentLinkedQueue() }
        q.add(PendingFracture(event.parentShipId, cellsCopy, event.reason))
    }

    @JvmStatic
    fun tick(level: ServerLevel) {
        if (!isFracturingActive()) {
            if (queues.isNotEmpty()) queues.clear()
            return
        }
        val q = queues[level.dimensionId] ?: return

        val pending = ArrayList<PendingFracture>()
        while (true) pending.add(q.poll() ?: break)
        if (pending.isEmpty()) return

        val seen = HashSet<Set<BlockPos>>()
        val blockSets = pending.mapNotNull { frac ->
            val blocks = HashSet<BlockPos>(frac.cells.size)
            for (cell in frac.cells) {
                val pos = BlockPos(cell.x(), cell.y(), cell.z())
                if (!level.getBlockState(pos).isAir) blocks.add(pos)
            }
            if (blocks.isEmpty() || !seen.add(blocks)) null else blocks
        }
        if (blockSets.isEmpty()) return

        try {
            val ships = ShipAssembler.batchAssembleToShips(level, blockSets, 1.0)
            logger.info(
                "Fracture: materialised ${ships.size} fragment ship(s) from ${pending.size} fracture event(s) " +
                    "in ${level.dimensionId}"
            )
        } catch (e: Throwable) {
            logger.error("Fracture: failed to materialise ${blockSets.size} fragment(s)", e)
        }
    }
}
