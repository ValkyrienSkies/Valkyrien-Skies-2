package org.valkyrienskies.mod.common.air_pockets

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.valkyrienskies.mod.util.FluidStateManager
import kotlin.math.abs

const val POINT_QUERY_EPS: Double = 1e-5

internal fun findOpenShipBlockPosForPoint(
    level: Level,
    state: ShipPocketState,
    shipPos: Vector3d,
    tmp: BlockPos.MutableBlockPos,
    queryCache: FluidStateManager.QueryCache? = null,
): BlockPos.MutableBlockPos? {
    val x = shipPos.x
    val y = shipPos.y
    val z = shipPos.z

    if (isOpenAtShipPoint(state, x, y, z, tmp)) return tmp

    val e = POINT_QUERY_EPS
    for (dx in doubleArrayOf(-e, 0.0, e)) {
        for (dy in doubleArrayOf(-e, 0.0, e)) {
            for (dz in doubleArrayOf(-e, 0.0, e)) {
                if (dx == 0.0 && dy == 0.0 && dz == 0.0) continue
                if (isOpenAtShipPoint(state, x + dx, y + dy, z + dz, tmp)) return tmp
            }
        }
    }

    val baseX = Mth.floor(x)
    val baseY = Mth.floor(y)
    val baseZ = Mth.floor(z)
    val baseLX = baseX - state.minX
    val baseLY = baseY - state.minY
    val baseLZ = baseZ - state.minZ
    val baseInBounds =
        baseLX in 0 until state.sizeX &&
            baseLY in 0 until state.sizeY &&
            baseLZ in 0 until state.sizeZ
    val baseIdx = if (baseInBounds) indexOf(state, baseLX, baseLY, baseLZ) else -1

    fun edgePasses(dirCode: Int): Boolean {
        if (baseIdx < 0) return true
        return edgeConductance(state, baseIdx, baseLX, baseLY, baseLZ, dirCode) > 0
    }

    // If we're inside a water-blocking block space (doors/panes/walls/etc), try to use the collision shape to pick
    // the open neighbor cell that the point can actually "exit" through. This avoids flickering at thin collision
    // blocks like doors (player can be inside the same block coordinate, but only on one side of the plane).
    run {
        tmp.set(baseX, baseY, baseZ)
        val bs = FluidStateManager.getBlockState(level, tmp, queryCache)
        val shape = bs.getCollisionShape(level, tmp)
        if (!shape.isEmpty) {
            // For full blocks, treat the collision shape as a solid hull voxel and pick the open neighbor cell
            // based on which face the point is closest to. This prevents ambiguous mapping when a world-space point
            // falls inside a solid ship block due to rotation/rounding (e.g. right on the hull).
            val fullBlock = bs.isCollisionShapeFullBlock(level, tmp)

            val fx = (x - baseX.toDouble()).coerceIn(0.0, 1.0)
            val fy = (y - baseY.toDouble()).coerceIn(0.0, 1.0)
            val fz = (z - baseZ.toDouble()).coerceIn(0.0, 1.0)

            val eps = 1e-4
            val boxes: List<AABB> = shape.toAabbs()

            fun contains(px: Double, py: Double, pz: Double): Boolean {
                if (fullBlock) return false
                for (box in boxes) {
                    if (px >= box.minX && px <= box.maxX &&
                        py >= box.minY && py <= box.maxY &&
                        pz >= box.minZ && pz <= box.maxZ
                    ) {
                        return true
                    }
                }
                return false
            }

            fun consider(
                dirX: Int,
                dirY: Int,
                dirZ: Int,
                dirCode: Int,
                sampleX: Double,
                sampleY: Double,
                sampleZ: Double,
                faceDist: Double,
                best: DoubleArray,
            ) {
                if (contains(sampleX, sampleY, sampleZ)) return
                if (!edgePasses(dirCode)) return
                tmp.set(baseX + dirX, baseY + dirY, baseZ + dirZ)
                if (!isOpen(state, tmp)) return
                val interior = isInterior(state, tmp)
                if (faceDist < best[0] - 1e-12 || (faceDist <= best[0] + 1e-12 && interior && best[1] == 0.0)) {
                    best[0] = faceDist
                    best[1] = if (interior) 1.0 else 0.0
                    best[2] = tmp.x.toDouble()
                    best[3] = tmp.y.toDouble()
                    best[4] = tmp.z.toDouble()
                }
            }

            val best = doubleArrayOf(Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0, 0.0)

            consider(-1, 0, 0, 0, eps, fy, fz, fx, best) // west
            consider(1, 0, 0, 1, 1.0 - eps, fy, fz, 1.0 - fx, best) // east
            consider(0, -1, 0, 2, fx, eps, fz, fy, best) // down
            consider(0, 1, 0, 3, fx, 1.0 - eps, fz, 1.0 - fy, best) // up
            consider(0, 0, -1, 4, fx, fy, eps, fz, best) // north
            consider(0, 0, 1, 5, fx, fy, 1.0 - eps, 1.0 - fz, best) // south

            if (best[0] != Double.POSITIVE_INFINITY) {
                tmp.set(best[2].toInt(), best[3].toInt(), best[4].toInt())
                return tmp
            }
        }
    }

    // Otherwise, fall back to finding the nearest open neighbor cell so entities/camera don't flicker between
    // "in water" and "in air" near boundaries.
    var bestDistSq = Double.POSITIVE_INFINITY
    var bestX = 0
    var bestY = 0
    var bestZ = 0

    for (dx in -1..1) {
        val px = baseX + dx
        val cx = px + 0.5
        val ddx = x - cx
        for (dy in -1..1) {
            val py = baseY + dy
            val cy = py + 0.5
            val ddy = y - cy
            for (dz in -1..1) {
                if (dx == 0 && dy == 0 && dz == 0) continue
                val pz = baseZ + dz
                tmp.set(px, py, pz)
                if (!isOpen(state, tmp)) continue

                if (baseIdx >= 0) {
                    val manhattan = abs(dx) + abs(dy) + abs(dz)
                    if (manhattan == 1) {
                        val dirCode = when {
                            dx < 0 -> 0
                            dx > 0 -> 1
                            dy < 0 -> 2
                            dy > 0 -> 3
                            dz < 0 -> 4
                            else -> 5
                        }
                        if (!edgePasses(dirCode)) continue
                    }
                }

                val cz = pz + 0.5
                val ddz = z - cz
                val distSq = ddx * ddx + ddy * ddy + ddz * ddz

                if (distSq < bestDistSq - 1e-12) {
                    bestDistSq = distSq
                    bestX = px
                    bestY = py
                    bestZ = pz
                }
            }
        }
    }

    if (bestDistSq != Double.POSITIVE_INFINITY) {
        tmp.set(bestX, bestY, bestZ)
        return tmp
    }

    return null
}

internal fun findShipFluidAtShipPoint(
    level: Level,
    shipPos: Vector3d,
    shipBlockPos: BlockPos.MutableBlockPos,
    queryCache: FluidStateManager.QueryCache? = null,
): FluidState {
    val baseX = shipBlockPos.x
    val baseY = shipBlockPos.y
    val baseZ = shipBlockPos.z

    var shipFluid = FluidStateManager.getBlockState(level, shipBlockPos, queryCache).fluidState
    if (!shipFluid.isEmpty) return shipFluid

    val e = POINT_QUERY_EPS
    for (dxi in -1..1) {
        val dx = dxi.toDouble() * e
        for (dyi in -1..1) {
            val dy = dyi.toDouble() * e
            for (dzi in -1..1) {
                val dz = dzi.toDouble() * e
                if (dxi == 0 && dyi == 0 && dzi == 0) continue
                shipBlockPos.set(
                    Mth.floor(shipPos.x + dx),
                    Mth.floor(shipPos.y + dy),
                    Mth.floor(shipPos.z + dz),
                )
                shipFluid = FluidStateManager.getBlockState(level, shipBlockPos, queryCache).fluidState
                if (!shipFluid.isEmpty) return shipFluid
            }
        }
    }

    shipBlockPos.set(baseX, baseY, baseZ)
    return shipFluid
}

internal fun findNearbyAirPocket(
    state: ShipPocketState,
    shipPos: Vector3d,
    shipBlockPos: BlockPos.MutableBlockPos,
    radius: Int,
): BlockPos.MutableBlockPos? {
    return findNearbyStateCellByPredicate(
        state = state,
        shipPos = shipPos,
        shipBlockPos = shipBlockPos,
        radius = radius,
        predicate = ::isAirPocket,
    )
}

internal fun findNearbyWorldFluidSuppressionZone(
    state: ShipPocketState,
    shipPos: Vector3d,
    shipBlockPos: BlockPos.MutableBlockPos,
    radius: Int,
): BlockPos.MutableBlockPos? {
    return findNearbyStateCellByPredicate(
        state = state,
        shipPos = shipPos,
        shipBlockPos = shipBlockPos,
        radius = radius,
        predicate = ::isWorldFluidSuppressionCell,
    )
}

private fun findNearbyStateCellByPredicate(
    state: ShipPocketState,
    shipPos: Vector3d,
    shipBlockPos: BlockPos.MutableBlockPos,
    radius: Int,
    predicate: (ShipPocketState, BlockPos) -> Boolean,
): BlockPos.MutableBlockPos? {
    val baseX = shipBlockPos.x
    val baseY = shipBlockPos.y
    val baseZ = shipBlockPos.z

    if (predicate(state, shipBlockPos)) return shipBlockPos

    val e = POINT_QUERY_EPS
    for (dxi in -1..1) {
        val dx = dxi.toDouble() * e
        for (dyi in -1..1) {
            val dy = dyi.toDouble() * e
            for (dzi in -1..1) {
                val dz = dzi.toDouble() * e
                if (dx == 0.0 && dy == 0.0 && dz == 0.0) continue
                shipBlockPos.set(
                    Mth.floor(shipPos.x + dx),
                    Mth.floor(shipPos.y + dy),
                    Mth.floor(shipPos.z + dz),
                )
                if (predicate(state, shipBlockPos)) return shipBlockPos
            }
        }
    }

    if (radius <= 0) return null

    var bestDistSq = Double.POSITIVE_INFINITY
    var bestX = 0
    var bestY = 0
    var bestZ = 0

    val x = shipPos.x
    val y = shipPos.y
    val z = shipPos.z

    for (dx in -radius..radius) {
        val px = baseX + dx
        val cx = px + 0.5
        val ddx = x - cx
        for (dy in -radius..radius) {
            val py = baseY + dy
            val cy = py + 0.5
            val ddy = y - cy
            for (dz in -radius..radius) {
                val pz = baseZ + dz
                shipBlockPos.set(px, py, pz)
                if (!predicate(state, shipBlockPos)) continue

                val cz = pz + 0.5
                val ddz = z - cz
                val distSq = ddx * ddx + ddy * ddy + ddz * ddz

                if (distSq < bestDistSq - 1e-12) {
                    bestDistSq = distSq
                    bestX = px
                    bestY = py
                    bestZ = pz
                }
            }
        }
    }

    if (bestDistSq != Double.POSITIVE_INFINITY) {
        shipBlockPos.set(bestX, bestY, bestZ)
        return shipBlockPos
    }

    return null
}
