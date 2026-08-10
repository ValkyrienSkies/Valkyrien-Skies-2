package org.valkyrienskies.mod.common.fluid.client

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.abs
import kotlin.math.round

/**
 * Block-shape geometry used by the client fluid cull/overlay renderers.
 *
 * Ported from the air-pocket subsystem's geometry pass, reduced to the subset the client renderers
 * actually need: turning a [BlockState] into the boxes that block fluid, and deciding whether a cell
 * still has any open volume left. The server-side connectivity solver that used to share this file
 * is gone — flood topology now arrives from vs-core — so only the shape half survives here.
 */

private const val GEOMETRY_FACE_SAMPLES_BASE = 8
private const val GEOMETRY_FACE_SAMPLES_REFINED = 16
private const val GEOMETRY_BOUNDARY_SNAP_EPS = 1.0 / 256.0

internal data class ShapeWaterGeometry(
    val fullSolid: Boolean,
    val refined: Boolean,
    val boxes: List<AABB>,
)

private fun isGameplaySealedState(state: BlockState): Boolean {
    return when (state.block) {
        is FenceGateBlock -> !state.getValue(BlockStateProperties.OPEN)
        else -> false
    }
}

private fun isOpenThinBarrierState(state: BlockState): Boolean {
    if (!state.hasProperty(BlockStateProperties.OPEN) || !state.getValue(BlockStateProperties.OPEN)) {
        return false
    }
    return when (state.block) {
        is DoorBlock,
        is TrapDoorBlock,
        -> true
        else -> false
    }
}

private fun shapeBlockingScore(shape: VoxelShape): Double {
    if (shape.isEmpty()) return Double.NEGATIVE_INFINITY
    var score = 0.0
    for (box in shape.toAabbs()) {
        val dx = (box.maxX - box.minX).coerceIn(0.0, 1.0)
        val dy = (box.maxY - box.minY).coerceIn(0.0, 1.0)
        val dz = (box.maxZ - box.minZ).coerceIn(0.0, 1.0)
        score += dx * dy * dz
    }
    return score
}

private fun resolveFluidOcclusionShape(level: Level, pos: BlockPos, state: BlockState): VoxelShape {
    val collision = state.getCollisionShape(level, pos)
    val occlusion = state.getOcclusionShape(level, pos)
    val union =
        when {
            collision.isEmpty() && occlusion.isEmpty() -> Shapes.empty()
            collision.isEmpty() -> occlusion
            occlusion.isEmpty() -> collision
            else -> Shapes.or(collision, occlusion)
        }

    var best = Shapes.empty()
    var bestScore = Double.NEGATIVE_INFINITY
    for (candidate in arrayOf(collision, occlusion, union)) {
        val score = shapeBlockingScore(candidate)
        if (score > bestScore + 1.0e-9) {
            bestScore = score
            best = candidate
        }
    }
    return best
}

private fun snapBoundaryCoord(value: Double): Double {
    val clamped = value.coerceIn(0.0, 1.0)
    if (clamped <= GEOMETRY_BOUNDARY_SNAP_EPS) return 0.0
    if (clamped >= 1.0 - GEOMETRY_BOUNDARY_SNAP_EPS) return 1.0
    return clamped
}

private fun snapFluidBoundaryBox(box: AABB): AABB {
    return AABB(
        snapBoundaryCoord(box.minX),
        snapBoundaryCoord(box.minY),
        snapBoundaryCoord(box.minZ),
        snapBoundaryCoord(box.maxX),
        snapBoundaryCoord(box.maxY),
        snapBoundaryCoord(box.maxZ),
    )
}

private fun snapFacePlaneCoord(value: Double): Double {
    val clamped = value.coerceIn(0.0, 1.0)
    val snapped16 = round(clamped * 16.0) / 16.0
    return if (abs(clamped - snapped16) <= GEOMETRY_BOUNDARY_SNAP_EPS) snapped16.coerceIn(0.0, 1.0) else clamped
}

private fun stabilizeOpenThinBarrierFacePlane(box: AABB): AABB {
    var minX = snapFacePlaneCoord(box.minX)
    var minY = snapFacePlaneCoord(box.minY)
    var minZ = snapFacePlaneCoord(box.minZ)
    var maxX = snapFacePlaneCoord(box.maxX)
    var maxY = snapFacePlaneCoord(box.maxY)
    var maxZ = snapFacePlaneCoord(box.maxZ)

    // Keep thin open trapdoor planes glued to their boundary face so connectivity won't flicker from tiny gaps.
    if (maxX - minX <= GEOMETRY_BOUNDARY_SNAP_EPS) {
        if (minX <= GEOMETRY_BOUNDARY_SNAP_EPS) {
            minX = 0.0
            maxX = maxOf(maxX, GEOMETRY_BOUNDARY_SNAP_EPS)
        } else if (maxX >= 1.0 - GEOMETRY_BOUNDARY_SNAP_EPS) {
            maxX = 1.0
            minX = minOf(minX, 1.0 - GEOMETRY_BOUNDARY_SNAP_EPS)
        }
    }
    if (maxY - minY <= GEOMETRY_BOUNDARY_SNAP_EPS) {
        if (minY <= GEOMETRY_BOUNDARY_SNAP_EPS) {
            minY = 0.0
            maxY = maxOf(maxY, GEOMETRY_BOUNDARY_SNAP_EPS)
        } else if (maxY >= 1.0 - GEOMETRY_BOUNDARY_SNAP_EPS) {
            maxY = 1.0
            minY = minOf(minY, 1.0 - GEOMETRY_BOUNDARY_SNAP_EPS)
        }
    }
    if (maxZ - minZ <= GEOMETRY_BOUNDARY_SNAP_EPS) {
        if (minZ <= GEOMETRY_BOUNDARY_SNAP_EPS) {
            minZ = 0.0
            maxZ = maxOf(maxZ, GEOMETRY_BOUNDARY_SNAP_EPS)
        } else if (maxZ >= 1.0 - GEOMETRY_BOUNDARY_SNAP_EPS) {
            maxZ = 1.0
            minZ = minOf(minZ, 1.0 - GEOMETRY_BOUNDARY_SNAP_EPS)
        }
    }

    return AABB(minX, minY, minZ, maxX, maxY, maxZ)
}

private fun isFullCubeBox(box: AABB): Boolean {
    val eps = 1e-6
    return box.minX <= eps && box.minY <= eps && box.minZ <= eps &&
        box.maxX >= 1.0 - eps && box.maxY >= 1.0 - eps && box.maxZ >= 1.0 - eps
}

private fun requiresRefinedSampling(shape: VoxelShape, boxes: List<AABB>): Boolean {
    if (shape.isEmpty() || boxes.isEmpty()) return false
    if (boxes.size != 1) return true

    val box = boxes[0]
    val eps = 1e-6
    fun isGridAligned(v: Double): Boolean {
        val n = v * GEOMETRY_FACE_SAMPLES_BASE.toDouble()
        return abs(n - round(n)) <= eps
    }

    // Partial or angled-ish shapes with non-grid-aligned boundaries get refined sampling.
    return !isGridAligned(box.minX) || !isGridAligned(box.maxX) ||
        !isGridAligned(box.minY) || !isGridAligned(box.maxY) ||
        !isGridAligned(box.minZ) || !isGridAligned(box.maxZ)
}

internal fun computeShapeWaterGeometry(level: Level, pos: BlockPos, state: BlockState): ShapeWaterGeometry {
    if (state.isAir) return ShapeWaterGeometry(fullSolid = false, refined = false, boxes = emptyList())
    if (isGameplaySealedState(state)) {
        return ShapeWaterGeometry(
            fullSolid = true,
            refined = true,
            boxes = listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
        )
    }

    val shape = resolveFluidOcclusionShape(level, pos, state)
    if (shape.isEmpty()) {
        return ShapeWaterGeometry(fullSolid = false, refined = false, boxes = emptyList())
    }

    val rawBoxes = shape.toAabbs()
        .asSequence()
        .map(::snapFluidBoundaryBox)
        .filter { it.maxX - it.minX > 1e-9 && it.maxY - it.minY > 1e-9 && it.maxZ - it.minZ > 1e-9 }
        .toList()
    val forceRefined = isOpenThinBarrierState(state)
    val boxes = if (forceRefined) rawBoxes.map(::stabilizeOpenThinBarrierFacePlane) else rawBoxes
    if (boxes.isEmpty()) {
        return ShapeWaterGeometry(fullSolid = false, refined = false, boxes = emptyList())
    }

    val fullSolid = state.isCollisionShapeFullBlock(level, pos) || boxes.any(::isFullCubeBox)
    if (fullSolid) {
        return ShapeWaterGeometry(
            fullSolid = true,
            refined = false,
            boxes = listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
        )
    }

    return ShapeWaterGeometry(
        fullSolid = false,
        refined = forceRefined || requiresRefinedSampling(shape, boxes),
        boxes = boxes,
    )
}

private fun isSolidAt(geom: ShapeWaterGeometry, x: Double, y: Double, z: Double): Boolean {
    if (geom.fullSolid) return true
    if (geom.boxes.isEmpty()) return false
    val eps = 1e-8
    for (box in geom.boxes) {
        if (
            x >= box.minX - eps && x <= box.maxX + eps &&
            y >= box.minY - eps && y <= box.maxY + eps &&
            z >= box.minZ - eps && z <= box.maxZ + eps
        ) {
            return true
        }
    }
    return false
}

internal fun hasOpenVolume(geom: ShapeWaterGeometry): Boolean {
    if (geom.fullSolid) return false
    if (geom.boxes.isEmpty()) return true

    val samples = if (geom.refined) GEOMETRY_FACE_SAMPLES_REFINED else GEOMETRY_FACE_SAMPLES_BASE
    for (sz in 0 until samples) {
        val z = (sz + 0.5) / samples.toDouble()
        for (sy in 0 until samples) {
            val y = (sy + 0.5) / samples.toDouble()
            for (sx in 0 until samples) {
                val x = (sx + 0.5) / samples.toDouble()
                if (!isSolidAt(geom, x, y, z)) return true
            }
        }
    }
    return false
}

/** Java-facing entry points for the client cull/overlay renderers. */
object ShipFluidCullBridge {

    /** The shape that blocks fluid in this cell, as the cull mask builder wants it. */
    @JvmStatic
    fun buildCullVoxelShape(level: Level, pos: BlockPos, state: BlockState): VoxelShape {
        val geom = computeShapeWaterGeometry(level, pos, state)
        if (geom.fullSolid) return Shapes.block()
        if (geom.boxes.isEmpty()) return Shapes.empty()

        var shape: VoxelShape = Shapes.empty()
        for (box in geom.boxes) {
            shape = Shapes.or(shape, Shapes.create(box))
        }
        return shape
    }

    /** True when fluid can still occupy some part of this cell. */
    @JvmStatic
    fun isOpenCell(level: Level, pos: BlockPos, state: BlockState): Boolean {
        return hasOpenVolume(computeShapeWaterGeometry(level, pos, state))
    }
}
