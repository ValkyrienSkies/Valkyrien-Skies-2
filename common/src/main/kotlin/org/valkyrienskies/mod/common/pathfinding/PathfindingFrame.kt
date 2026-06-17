package org.valkyrienskies.mod.common.pathfinding

import net.minecraft.world.entity.Mob
import org.joml.Matrix4d
import org.joml.Matrix4dc
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.mod.common.getShipMountedTo
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider

/**
 * Coordinate frame a single pathfinder invocation runs in: either world, or one ship's local space.
 *
 * One frame per `findPath` — chosen at `createPath` time and fixed for the whole A* search.
 */
sealed class PathfindingFrame {
    object World : PathfindingFrame()

    /** [shipToWorldSnapshot] is captured at pathfind time so later code can detect ship rotation drift. */
    data class InShip(
        val ship: LoadedShip,
        val shipId: ShipId,
        val shipToWorldSnapshot: Matrix4dc,
    ) : PathfindingFrame()

    companion object {
        /**
         * Set around a single `findPath` call. Cross-thread pathfinder mods (Lithium/Performant)
         * must also mirror the frame onto the mob via [IPathfindingFrameProvider].
         */
        @JvmStatic
        val CURRENT: ThreadLocal<PathfindingFrame?> = ThreadLocal.withInitial { null }

        /**
         * Resolve the frame from the engine's authoritative dragger state — never AABB heuristics,
         * which misclassify mobs in a ship's airspace or on a not-yet-registered ship.
         *
         * `lastShipStoodOn` is looked up by id directly: a mob on a rendered ship has a world-space
         * position that is not inside any ship's chunk claim, so chunk-claim lookup would miss it.
         */
        @JvmStatic
        fun resolveForMob(mob: Mob): PathfindingFrame {
            val level = mob.level() ?: return World

            val draggingProvider = mob as? IEntityDraggingInformationProvider
            val stoodOn: ShipId? = draggingProvider?.draggingInformation?.lastShipStoodOn
            if (stoodOn != null) {
                val ship = level.shipObjectWorld.loadedShips.getById(stoodOn)
                if (ship != null) {
                    return InShip(ship, ship.id, Matrix4d(ship.transform.shipToWorld))
                }
            }

            val mountedTo = getShipMountedTo(mob)
            if (mountedTo != null) {
                return InShip(mountedTo, mountedTo.id, Matrix4d(mountedTo.transform.shipToWorld))
            }

            return World
        }

        /**
         * In-flight frame for the current pathfind. Reads [CURRENT] first, falling back to the mob's
         * stored frame for cross-thread / post-pathfind hooks where the ThreadLocal context is gone.
         * `mob` is nullable for cell-peek call sites that have no mob handy.
         */
        @JvmStatic
        fun current(mob: Mob?): PathfindingFrame? {
            CURRENT.get()?.let { return it }
            return (mob as? IPathfindingFrameProvider)?.`vs$getPathfindingFrame`()
        }
    }
}

/** Duck on [Mob] that carries the current frame across worker threads and into post-pathfind hooks. */
interface IPathfindingFrameProvider {
    fun `vs$getPathfindingFrame`(): PathfindingFrame?
    fun `vs$setPathfindingFrame`(frame: PathfindingFrame?)
}
