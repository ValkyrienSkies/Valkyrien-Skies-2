package org.valkyrienskies.mod.common.pathfinding

import net.minecraft.world.level.pathfinder.Path
import java.util.Collections
import java.util.WeakHashMap

/**
 * Stores the [PathfindingFrame] associated with each live [Path], plus a per-pathfind
 * nearby-ship snapshot in a ThreadLocal that the peek mixins read per-cell.
 *
 * The navigator owns each [Path] and we can't hook its disposal, so weak keys let entries
 * die naturally when the navigator drops the path.
 */
object PathPerFrameRegistry {

    private val frameMap: MutableMap<Path, PathfindingFrame> = Collections.synchronizedMap(WeakHashMap())

    private val nearbyShipsTL: ThreadLocal<Array<NearbyShip>> =
        ThreadLocal.withInitial { NearbyShipsForPathfinding.empty() }

    @JvmStatic
    fun register(path: Path, frame: PathfindingFrame) {
        frameMap[path] = frame
    }

    @JvmStatic
    fun get(path: Path): PathfindingFrame? = frameMap[path]

    /** Single-frame paths: every node shares the path's primary frame. */
    @JvmStatic
    fun getFrameAtNodeIndex(path: Path, @Suppress("UNUSED_PARAMETER") nodeIndex: Int): PathfindingFrame? =
        frameMap[path]

    @JvmStatic
    fun setNearbyShips(ships: Array<NearbyShip>) {
        nearbyShipsTL.set(ships)
    }

    @JvmStatic
    fun getNearbyShips(): Array<NearbyShip> = nearbyShipsTL.get()
}
