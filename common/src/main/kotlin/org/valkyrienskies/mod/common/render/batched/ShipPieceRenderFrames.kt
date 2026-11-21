package org.valkyrienskies.mod.common.render.batched

import net.minecraft.client.multiplayer.ClientLevel
import org.joml.Matrix4d
import org.joml.Matrix4dc
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece
import org.valkyrienskies.mod.common.allShips
import org.valkyrienskies.mod.common.util.ShipDraggingMath

internal class ShipPieceRenderFrame(val previous: ShipPhysicalPiece, val current: ShipPhysicalPiece, val pivot: Vector3dc) {
    fun sample(t: Double): ShipPhysicalPiece {
        if (t <= 0.0) return previous
        if (t >= 1.0 || previous === current) return current
        val a = previous.shipToWorld
        val b = current.shipToWorld
        val center = a.transformPosition(pivot, Vector3d()).lerp(b.transformPosition(pivot, Vector3d()), t)
        val rotation = a.getUnnormalizedRotation(Quaterniond()).slerp(b.getUnnormalizedRotation(Quaterniond()), t).normalize()
        val scale = a.getScale(Vector3d()).lerp(b.getScale(Vector3d()), t)
        val transform = Matrix4d().translation(center).rotate(rotation).scale(scale).translate(-pivot.x(), -pivot.y(), -pivot.z())
        val bounds = AABBd(previous.worldBounds).transform(previous.worldToShip)
            .union(AABBd(current.worldBounds).transform(current.worldToShip)).transform(transform)
        return ShipPhysicalPiece(current.shipId, current.dimensionId, bounds, transform, current.clippingPlanes)
    }
}

internal fun ShipPhysicalPiece.atPreviousPose(chart: Matrix4dc, previousCanonical: Matrix4dc): ShipPhysicalPiece {
    val previous = chart.mul(previousCanonical, Matrix4d())
    val bounds = AABBd(worldBounds).transform(previous.mul(worldToShip, Matrix4d()))
    return ShipPhysicalPiece(shipId, dimensionId, bounds, previous, clippingPlanes)
}

object ShipPieceRenderFrames {
    private class Snapshot(val ship: Ship, val piece: ShipPhysicalPiece, val chart: Matrix4dc, val canonical: Matrix4dc)
    private var level: ClientLevel? = null
    private var snapshots: Map<Long, List<Snapshot>> = emptyMap()
    private val frames = HashMap<ShipPhysicalPiece, ShipPieceRenderFrame>()
    private val sampled = HashMap<ShipPhysicalPiece, ShipPhysicalPiece>()

    @JvmStatic fun clear() {
        level = null
        snapshots = emptyMap()
        frames.clear()
        sampled.clear()
    }

    @JvmStatic fun tick(world: ClientLevel) {
        if (level !== world) clear()
        level = world
        val ships = world.allShips
        if (ships.getSplitShipIds().isEmpty()) {
            if (snapshots.isNotEmpty()) clear()
            return
        }
        frames.clear()
        sampled.clear()
        val next = HashMap<Long, List<Snapshot>>()
        for (id in ships.getSplitShipIds()) {
            val ship = ships.getById(id) ?: continue
            next[id] = ships.getSplitPieces(id).map { piece ->
                val chart = Matrix4d(piece.shipToWorld).mul(ship.worldToShip)
                val before = if (ship.prevTickTransform === ship.transform) piece else snapshots[id]?.singleOrNull { old ->
                    old.ship === ship && old.canonical.equals(ship.prevTickTransform.shipToWorld, 1e-6) &&
                        old.chart.equals(chart, 1e-5) && old.piece.clippingPlanes.size == piece.clippingPlanes.size &&
                        old.piece.clippingPlanes.all { a -> piece.clippingPlanes.any { b -> a.equals(b, 1e-5) } }
                }?.piece ?: piece.atPreviousPose(chart, ship.prevTickTransform.shipToWorld)
                frames[piece] = ShipPieceRenderFrame(before, piece, ship.transform.positionInShip)
                Snapshot(ship, piece, chart, ship.shipToWorld)
            }
        }
        snapshots = next
    }

    @JvmStatic fun beginFrame(t: Float) {
        for ((piece, frame) in frames) sampled[piece] = frame.sample(t.toDouble())
    }

    @JvmStatic fun rendered(piece: ShipPhysicalPiece) = sampled[piece] ?: piece

    @JvmStatic fun rider(piece: ShipPhysicalPiece, previousPosition: Vector3dc, currentPosition: Vector3dc, t: Float): Vector3d? {
        val frame = frames[piece] ?: return null
        return ShipDraggingMath.renderPosition(previousPosition, currentPosition, frame.previous.worldToShip,
            piece.worldToShip, rendered(piece).shipToWorld, t.toDouble())
    }
}
