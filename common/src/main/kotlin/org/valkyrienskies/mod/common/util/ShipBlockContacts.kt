package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.Ship
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object ShipBlockContacts {
    data class Contact(val normal: Vector3d, val velocity: Vector3d, val point: Vector3d)

    fun sweptBounds(ship: Ship): AABBd {
        val local = ship.shipAABB ?: return AABBd(ship.worldAABB)
        return AABBd(local.minX().toDouble(), local.minY().toDouble(), local.minZ().toDouble(),
            local.maxX().toDouble(), local.maxY().toDouble(), local.maxZ().toDouble())
            .transform(ship.prevTickTransform.shipToWorld).union(ship.worldAABB)
    }

    fun find(level: ServerLevel, source: Ship, sensor: AABBdc, target: Ship? = null,
        sweep: Boolean = true): Contact? {
        if (source.id == target?.id) return null
        val current = Matrix4d(target?.worldToShip ?: Matrix4d()).mul(source.shipToWorld)
        val previous = Matrix4d(target?.prevTickTransform?.worldToShip ?: Matrix4d())
            .mul(source.prevTickTransform.shipToWorld)
        val region = AABBd(sensor).transform(Matrix4d(current).invert())
        if (sweep) region.union(AABBd(sensor).transform(Matrix4d(previous).invert()))
        val bounds = source.shipAABB ?: return null
        val minX = max(bounds.minX(), floor(region.minX - 1.0).toInt())
        val minY = max(bounds.minY(), floor(region.minY - 1.0).toInt())
        val minZ = max(bounds.minZ(), floor(region.minZ - 1.0).toInt())
        val maxX = min(bounds.maxX() - 1, ceil(region.maxX + 1.0).toInt())
        val maxY = min(bounds.maxY() - 1, ceil(region.maxY + 1.0).toInt())
        val maxZ = min(bounds.maxZ() - 1, ceil(region.maxZ + 1.0).toInt())
        if (minX > maxX || minY > maxY || minZ > maxZ) return null
        // Limit work for extreme ship scales and long motion steps.
        if ((maxX.toLong() - minX + 1) * (maxY.toLong() - minY + 1) * (maxZ.toLong() - minZ + 1) > 32768) return null
        val pos = BlockPos.MutableBlockPos()
        for (x in minX..maxX) for (z in minZ..maxZ) {
            if (!source.activeChunksSet.contains(x shr 4, z shr 4) || !level.hasChunk(x shr 4, z shr 4)) continue
            for (y in minY..maxY) {
                pos.set(x, y, z)
                val state = level.getBlockState(pos)
                if (state.isAir) continue
                for (box in state.getCollisionShape(level, pos).toAabbs()) {
                    val localBox = box.move(x.toDouble(), y.toDouble(), z.toDouble()).toJOML()
                    val normal = ShipInteractionMath.sweptNormal(localBox, current, previous, sensor, sweep) ?: continue
                    val point = Vector3d((sensor.minX() + sensor.maxX()) * 0.5,
                        (sensor.minY() + sensor.maxY()) * 0.5, (sensor.minZ() + sensor.maxZ()) * 0.5)
                    target?.shipToWorld?.transformPosition(point)
                    val velocity = ShipInteractionMath.pointVelocity(source.velocity, source.omega, source.transform.position, point)
                    if (target != null) velocity.sub(ShipInteractionMath.pointVelocity(
                        target.velocity, target.omega, target.transform.position, point))
                    return Contact(normal, velocity, point)
                }
            }
        }
        return null
    }
}
