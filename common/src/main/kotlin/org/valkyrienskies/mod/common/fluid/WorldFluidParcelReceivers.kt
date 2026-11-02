package org.valkyrienskies.mod.common.fluid

import org.joml.Vector3dc
import org.joml.Quaterniond
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.internal.physics.VsiFluidParcelPose
import org.valkyrienskies.core.internal.physics.VsiFluidParcelReceiver
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal object WorldFluidParcelReceivers {
    fun collect(world: VsiServerShipWorld, dimension: String, start: Vector3dc, end: Vector3dc, includeMotion: Boolean,
        reserve: (Long) -> Unit): List<VsiFluidParcelReceiver>? {
        val receivers = ArrayList<VsiFluidParcelReceiver>()
        for (ship in world.loadedShips) {
            if (ship.chunkClaimDimension != dimension) continue
            val topology = world.getFluidTopologySnapshot(ship.id) ?: continue
            if (!topology.enabled || !topology.valid) continue
            val after = ship.transform
            val before = if (includeMotion) ship.prevTickTransform else after
            val scale = max(before.shipToWorldScaling.x(), after.shipToWorldScaling.x())
            if (!scale.isFinite() || scale <= 0) continue
            val bounds = ship.worldAABB
            val center = after.positionInWorld
            val dx = max(abs(bounds.minX() - center.x()), abs(bounds.maxX() - center.x()))
            val dy = max(abs(bounds.minY() - center.y()), abs(bounds.maxY() - center.y()))
            val dz = max(abs(bounds.minZ() - center.z()), abs(bounds.maxZ() - center.z()))
            var radius = sqrt(dx * dx + dy * dy + dz * dz) * scale / after.shipToWorldScaling.x()
            ShipFluidCandidates.topologyBounds(world, ship.id, topology)?.let { domain ->
                val origin = after.positionInShip
                val x = max(abs(domain.minX() - origin.x()), abs(domain.maxX() - origin.x()))
                val y = max(abs(domain.minY() - origin.y()), abs(domain.maxY() - origin.y()))
                val z = max(abs(domain.minZ() - origin.z()), abs(domain.maxZ() - origin.z()))
                radius = max(radius, sqrt(x * x + y * y + z * z) * scale)
            }
            radius += (2 + before.positionInShip.distance(after.positionInShip)) * scale
            val previous = before.positionInWorld
            if (!topology.truncated && (minOf(start.x(), end.x()) > max(center.x(), previous.x()) + radius ||
                max(start.x(), end.x()) < minOf(center.x(), previous.x()) - radius ||
                minOf(start.y(), end.y()) > max(center.y(), previous.y()) + radius ||
                max(start.y(), end.y()) < minOf(center.y(), previous.y()) - radius ||
                minOf(start.z(), end.z()) > max(center.z(), previous.z()) + radius ||
                max(start.z(), end.z()) < minOf(center.z(), previous.z()) - radius)) continue
            if (receivers.size == 4096) return null
            reserve(1024)
            receivers.add(VsiFluidParcelReceiver(ship.id, topology.sourceRevision, pose(before), pose(after)))
        }
        receivers.sortBy { it.bodyId }
        return receivers
    }

    private fun pose(transform: ShipTransform): VsiFluidParcelPose {
        val position = transform.positionInWorld
        val origin = transform.positionInShip
        val rotation = Quaterniond(transform.shipToWorldRotation).normalize()
        return VsiFluidParcelPose(position.x(), position.y(), position.z(),
            rotation.x(), rotation.y(), rotation.z(), rotation.w(),
            origin.x() - .5, origin.y() - .5, origin.z() - .5, transform.shipToWorldScaling.x())
    }
}
