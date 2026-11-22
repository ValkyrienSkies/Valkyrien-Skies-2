package org.valkyrienskies.mod.common.util

import java.util.function.Predicate
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.mod.common.getShipsIntersecting

// Which ship block occupies a world cell: project the cell centre into each ship overlapping it.
object ShipBlockQueries {

    private val CENTER: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val LOCAL: ThreadLocal<Vector3d> = ThreadLocal.withInitial { Vector3d() }
    private val PROBE: ThreadLocal<AABBd> = ThreadLocal.withInitial { AABBd() }

    /** Shipyard pos of the first ship block at [worldPos] matching [predicate], or null. */
    @JvmStatic
    fun findShipBlockPos(level: Level, worldPos: BlockPos, predicate: Predicate<BlockState>): BlockPos? {
        val x = worldPos.x.toDouble()
        val y = worldPos.y.toDouble()
        val z = worldPos.z.toDouble()
        val center = CENTER.get().set(x + 0.5, y + 0.5, z + 0.5)
        val probe = PROBE.get().setMin(x, y, z).setMax(x + 1.0, y + 1.0, z + 1.0)
        for (ship in level.getShipsIntersecting(probe)) {
            if (!ship.worldAABB.containsPoint(center)) continue
            val local = ship.transform.worldToShip.transformPosition(center, LOCAL.get())
            val shipPos = BlockPos.containing(local.x, local.y, local.z)
            if (predicate.test(level.getBlockState(shipPos))) return shipPos
        }
        return null
    }

    /** The first ship block at [worldPos] matching [predicate], or null. */
    @JvmStatic
    fun findShipBlock(level: Level, worldPos: BlockPos, predicate: Predicate<BlockState>): BlockState? =
        findShipBlockPos(level, worldPos, predicate)?.let(level::getBlockState)
}
