package org.valkyrienskies.mod.common.pathfinding

import org.joml.Matrix4dc
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.Ship

/**
 * Snapshot of one ship's pathfinding-relevant state at the start of a single A* pass. Populated by
 * [NearbyShipsForPathfinding.collect], stashed in [PathPerFrameRegistry]'s ThreadLocal, and read
 * by every per-cell ship-aware peek (`MixinPathNavigationRegion`, `SwimNodeEvaluatorMixin`).
 *
 * [worldToShip] is a snapshot, not a live read — ships tick concurrently with pathfinding, so
 * sampling the live transform per cell would yield non-deterministic nodes within one pass.
 *
 * Kept as a regular class with [JvmField] vals (rather than a `data class`) so Java consumers in
 * the hot per-cell loop get direct field access instead of synthetic getter calls.
 */
class NearbyShip(
    @JvmField val ship: Ship,
    @JvmField val worldAABB: AABBdc,
    @JvmField val worldToShip: Matrix4dc,
)
