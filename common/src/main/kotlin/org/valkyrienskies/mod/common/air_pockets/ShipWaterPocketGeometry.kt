package org.valkyrienskies.mod.common.air_pockets

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.phys.shapes.Shapes
import java.util.BitSet
import kotlin.math.abs
import kotlin.math.round

private const val GEOMETRY_FACE_SAMPLES_BASE = 8
private const val GEOMETRY_FACE_SAMPLES_REFINED = 16
private const val GEOMETRY_SAMPLE_EPS = 1e-4
private const val GEOMETRY_BOUNDARY_SNAP_EPS = 1.0 / 256.0

internal const val SHAPE_SUBCELL_RES = 8
internal const val SHAPE_SUBCELL_COUNT = SHAPE_SUBCELL_RES * SHAPE_SUBCELL_RES * SHAPE_SUBCELL_RES
internal const val SHAPE_FACE_SAMPLE_RES = SHAPE_SUBCELL_RES
internal const val SHAPE_FACE_SAMPLE_COUNT = SHAPE_FACE_SAMPLE_RES * SHAPE_FACE_SAMPLE_RES
internal const val SHAPE_MAX_COMPONENTS = 64

internal const val SHAPE_FACE_NEG_X = 0
internal const val SHAPE_FACE_POS_X = 1
internal const val SHAPE_FACE_NEG_Y = 2
internal const val SHAPE_FACE_POS_Y = 3
internal const val SHAPE_FACE_NEG_Z = 4
internal const val SHAPE_FACE_POS_Z = 5

private const val SHAPE_COMPONENT_SOLID: Byte = -1
private const val SHAPE_COMPONENT_UNASSIGNED: Byte = -2

internal data class ShapeWaterGeometry(
    val fullSolid: Boolean,
    val refined: Boolean,
    val boxes: List<AABB>,
)

internal data class ShapeCellTemplate(
    // Bit=1 means solid, bit=0 means void.
    val occupancyMask: LongArray,
    // Per subcell local component id (0..63), -1 for solid.
    val componentBySubcell: ByteArray,
    val componentCount: Int,
    // For each face, which local components are open on that face.
    val faceComponentMask: LongArray,
    // Number of open face samples (0..64) per face.
    val faceOpenCount: IntArray,
    // Per face sample local component id (0..63), -1 for solid.
    val faceSampleComponent: ByteArray,
) {
    val hasOpenVolume: Boolean
        get() = componentCount > 0
}

internal fun fullComponentMask(componentCount: Int): Long {
    return when {
        componentCount <= 0 -> 0L
        componentCount >= 64 -> -1L
        else -> (1L shl componentCount) - 1L
    }
}

private fun subcellIndex(sx: Int, sy: Int, sz: Int): Int {
    return sx + SHAPE_SUBCELL_RES * (sy + SHAPE_SUBCELL_RES * sz)
}

private fun subcellSolid(occupancyMask: LongArray, subIdx: Int): Boolean {
    val word = subIdx ushr 6
    val bit = subIdx and 63
    return ((occupancyMask[word] ushr bit) and 1L) != 0L
}

private fun setSubcellSolid(occupancyMask: LongArray, subIdx: Int) {
    val word = subIdx ushr 6
    val bit = subIdx and 63
    occupancyMask[word] = occupancyMask[word] or (1L shl bit)
}

private fun faceSampleSubcellIndex(face: Int, sampleIdx: Int): Int {
    val u = sampleIdx and (SHAPE_FACE_SAMPLE_RES - 1)
    val v = sampleIdx ushr 3
    return when (face) {
        SHAPE_FACE_NEG_X -> subcellIndex(0, u, v)
        SHAPE_FACE_POS_X -> subcellIndex(SHAPE_SUBCELL_RES - 1, u, v)
        SHAPE_FACE_NEG_Y -> subcellIndex(u, 0, v)
        SHAPE_FACE_POS_Y -> subcellIndex(u, SHAPE_SUBCELL_RES - 1, v)
        SHAPE_FACE_NEG_Z -> subcellIndex(u, v, 0)
        else -> subcellIndex(u, v, SHAPE_SUBCELL_RES - 1)
    }
}

private fun faceForDirCode(dirCode: Int): Int {
    return when (dirCode) {
        0 -> SHAPE_FACE_NEG_X
        1 -> SHAPE_FACE_POS_X
        2 -> SHAPE_FACE_NEG_Y
        3 -> SHAPE_FACE_POS_Y
        4 -> SHAPE_FACE_NEG_Z
        else -> SHAPE_FACE_POS_Z
    }
}

private fun oppositeFace(face: Int): Int {
    return when (face) {
        SHAPE_FACE_NEG_X -> SHAPE_FACE_POS_X
        SHAPE_FACE_POS_X -> SHAPE_FACE_NEG_X
        SHAPE_FACE_NEG_Y -> SHAPE_FACE_POS_Y
        SHAPE_FACE_POS_Y -> SHAPE_FACE_NEG_Y
        SHAPE_FACE_NEG_Z -> SHAPE_FACE_POS_Z
        else -> SHAPE_FACE_NEG_Z
    }
}

internal fun buildShapeCellTemplate(geom: ShapeWaterGeometry): ShapeCellTemplate {
    val occupancyMask = LongArray((SHAPE_SUBCELL_COUNT + 63) ushr 6)
    val componentBySubcell = ByteArray(SHAPE_SUBCELL_COUNT) { SHAPE_COMPONENT_UNASSIGNED }

    var hasOpen = false
    for (sz in 0 until SHAPE_SUBCELL_RES) {
        val z = (sz + 0.5) / SHAPE_SUBCELL_RES.toDouble()
        for (sy in 0 until SHAPE_SUBCELL_RES) {
            val y = (sy + 0.5) / SHAPE_SUBCELL_RES.toDouble()
            for (sx in 0 until SHAPE_SUBCELL_RES) {
                val x = (sx + 0.5) / SHAPE_SUBCELL_RES.toDouble()
                val subIdx = subcellIndex(sx, sy, sz)
                if (isSolidAt(geom, x, y, z)) {
                    componentBySubcell[subIdx] = SHAPE_COMPONENT_SOLID
                    setSubcellSolid(occupancyMask, subIdx)
                } else {
                    hasOpen = true
                }
            }
        }
    }

    var componentCount = 0
    if (hasOpen) {
        val queue = IntArray(SHAPE_SUBCELL_COUNT)
        for (start in 0 until SHAPE_SUBCELL_COUNT) {
            if (subcellSolid(occupancyMask, start)) continue
            if (componentBySubcell[start] != SHAPE_COMPONENT_UNASSIGNED) continue

            val componentId = if (componentCount < SHAPE_MAX_COMPONENTS) componentCount else SHAPE_MAX_COMPONENTS - 1
            if (componentCount < SHAPE_MAX_COMPONENTS) {
                componentCount++
            }

            var head = 0
            var tail = 0
            queue[tail++] = start
            componentBySubcell[start] = componentId.toByte()

            while (head < tail) {
                val cur = queue[head++]
                val cx = cur and 7
                val ct = cur ushr 3
                val cy = ct and 7
                val cz = ct ushr 3

                fun tryNeighbor(nx: Int, ny: Int, nz: Int) {
                    if (nx !in 0 until SHAPE_SUBCELL_RES ||
                        ny !in 0 until SHAPE_SUBCELL_RES ||
                        nz !in 0 until SHAPE_SUBCELL_RES
                    ) {
                        return
                    }
                    val n = subcellIndex(nx, ny, nz)
                    if (subcellSolid(occupancyMask, n)) return
                    if (componentBySubcell[n] != SHAPE_COMPONENT_UNASSIGNED) return
                    componentBySubcell[n] = componentId.toByte()
                    queue[tail++] = n
                }

                tryNeighbor(cx - 1, cy, cz)
                tryNeighbor(cx + 1, cy, cz)
                tryNeighbor(cx, cy - 1, cz)
                tryNeighbor(cx, cy + 1, cz)
                tryNeighbor(cx, cy, cz - 1)
                tryNeighbor(cx, cy, cz + 1)
            }
        }
    }

    val faceComponentMask = LongArray(6)
    val faceOpenCount = IntArray(6)
    val faceSampleComponent = ByteArray(6 * SHAPE_FACE_SAMPLE_COUNT) { SHAPE_COMPONENT_SOLID }

    for (face in 0 until 6) {
        val faceOffset = face * SHAPE_FACE_SAMPLE_COUNT
        for (sampleIdx in 0 until SHAPE_FACE_SAMPLE_COUNT) {
            val subIdx = faceSampleSubcellIndex(face, sampleIdx)
            val component = componentBySubcell[subIdx].toInt()
            faceSampleComponent[faceOffset + sampleIdx] = component.toByte()
            if (component >= 0) {
                faceOpenCount[face]++
                faceComponentMask[face] = faceComponentMask[face] or (1L shl component)
            }
        }
    }

    return ShapeCellTemplate(
        occupancyMask = occupancyMask,
        componentBySubcell = componentBySubcell,
        componentCount = componentCount,
        faceComponentMask = faceComponentMask,
        faceOpenCount = faceOpenCount,
        faceSampleComponent = faceSampleComponent,
    )
}

internal inline fun forEachTemplateFaceConnection(
    templateA: ShapeCellTemplate,
    templateB: ShapeCellTemplate,
    dirCodeFromA: Int,
    block: (componentA: Int, componentB: Int) -> Unit,
) {
    val faceA = faceForDirCode(dirCodeFromA)
    val faceB = oppositeFace(faceA)
    val faceOffsetA = faceA * SHAPE_FACE_SAMPLE_COUNT
    val faceOffsetB = faceB * SHAPE_FACE_SAMPLE_COUNT

    for (sampleIdx in 0 until SHAPE_FACE_SAMPLE_COUNT) {
        val componentA = templateA.faceSampleComponent[faceOffsetA + sampleIdx].toInt()
        if (componentA < 0) continue
        val componentB = templateB.faceSampleComponent[faceOffsetB + sampleIdx].toInt()
        if (componentB < 0) continue
        block(componentA, componentB)
    }
}

internal fun computeTemplateFaceConductance(
    templateA: ShapeCellTemplate,
    templateB: ShapeCellTemplate,
    dirCodeFromA: Int,
    componentMaskA: Long = -1L,
    componentMaskB: Long = -1L,
): Int {
    var count = 0
    forEachTemplateFaceConnection(templateA, templateB, dirCodeFromA) { componentA, componentB ->
        if (componentMaskA != -1L && ((componentMaskA ushr componentA) and 1L) == 0L) {
            return@forEachTemplateFaceConnection
        }
        if (componentMaskB != -1L && ((componentMaskB ushr componentB) and 1L) == 0L) {
            return@forEachTemplateFaceConnection
        }
        count++
    }
    return count
}

internal fun computeTemplateAxisConductance(
    templateA: ShapeCellTemplate,
    templateB: ShapeCellTemplate,
    axis: Int,
): Int {
    val dirCode = when (axis) {
        0 -> 1 // +X
        1 -> 3 // +Y
        else -> 5 // +Z
    }
    return computeTemplateFaceConductance(templateA, templateB, dirCode)
}

private fun isGameplaySealedState(state: BlockState): Boolean {
    val block = state.block
    return when (block) {
        is DoorBlock -> !state.getValue(BlockStateProperties.OPEN)
        is FenceGateBlock -> !state.getValue(BlockStateProperties.OPEN)
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
    val minX = snapBoundaryCoord(box.minX)
    val minY = snapBoundaryCoord(box.minY)
    val minZ = snapBoundaryCoord(box.minZ)
    val maxX = snapBoundaryCoord(box.maxX)
    val maxY = snapBoundaryCoord(box.maxY)
    val maxZ = snapBoundaryCoord(box.maxZ)
    return AABB(minX, minY, minZ, maxX, maxY, maxZ)
}

private fun snapFacePlaneCoord(value: Double): Double {
    val clamped = value.coerceIn(0.0, 1.0)
    val snapped16 = round(clamped * 16.0) / 16.0
    return if (abs(clamped - snapped16) <= GEOMETRY_BOUNDARY_SNAP_EPS) snapped16.coerceIn(0.0, 1.0) else clamped
}

private fun stabilizeOpenTrapdoorFacePlane(box: AABB): AABB {
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
    val forceRefined = state.block is TrapDoorBlock && state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)
    val boxes = if (forceRefined) rawBoxes.map(::stabilizeOpenTrapdoorFacePlane) else rawBoxes
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

internal fun computeFaceConductance(geomA: ShapeWaterGeometry, geomB: ShapeWaterGeometry, axis: Int): Int {
    if (geomA.fullSolid || geomB.fullSolid) return 0

    val samples = if (geomA.refined || geomB.refined) GEOMETRY_FACE_SAMPLES_REFINED else GEOMETRY_FACE_SAMPLES_BASE
    val eps = GEOMETRY_SAMPLE_EPS
    var openSamples = 0

    for (sv in 0 until samples) {
        val v = (sv + 0.5) / samples.toDouble()
        for (su in 0 until samples) {
            val u = (su + 0.5) / samples.toDouble()

            val aSolid: Boolean
            val bSolid: Boolean
            when (axis) {
                0 -> { // +X face of A to -X face of B
                    aSolid = isSolidAt(geomA, 1.0 - eps, u, v)
                    bSolid = isSolidAt(geomB, eps, u, v)
                }
                1 -> { // +Y face of A to -Y face of B
                    aSolid = isSolidAt(geomA, u, 1.0 - eps, v)
                    bSolid = isSolidAt(geomB, u, eps, v)
                }
                else -> { // +Z face of A to -Z face of B
                    aSolid = isSolidAt(geomA, u, v, 1.0 - eps)
                    bSolid = isSolidAt(geomB, u, v, eps)
                }
            }

            if (!aSolid && !bSolid) {
                openSamples++
            }
        }
    }

    return openSamples
}

internal fun computeInteriorMaskHeuristic(open: BitSet, sizeX: Int, sizeY: Int, sizeZ: Int): BitSet {
    val volumeLong = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
    if (volumeLong <= 0L) return BitSet()
    val volume = volumeLong.toInt()

    if (open.isEmpty) return BitSet(volume)

    val strideY = sizeX
    val strideZ = sizeX * sizeY

    val negX = BitSet(volume)
    val posX = BitSet(volume)
    val negY = BitSet(volume)
    val posY = BitSet(volume)
    val negZ = BitSet(volume)
    val posZ = BitSet(volume)
    val negXZ = BitSet(volume)
    val posXZ = BitSet(volume)
    val negXPosZ = BitSet(volume)
    val posXNegZ = BitSet(volume)

    // X direction (for each Y/Z line).
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            val base = sizeX * (y + sizeY * z)
            var seenSolid = false
            for (x in 0 until sizeX) {
                val idx = base + x
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negX.set(idx)
                }
            }
            seenSolid = false
            for (x in sizeX - 1 downTo 0) {
                val idx = base + x
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posX.set(idx)
                }
            }
        }
    }

    // Y direction (for each X/Z line).
    for (z in 0 until sizeZ) {
        for (x in 0 until sizeX) {
            var seenSolid = false
            var idx = x + strideZ * z
            for (y in 0 until sizeY) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negY.set(idx)
                }
                idx += strideY
            }
            seenSolid = false
            idx = x + strideZ * z + strideY * (sizeY - 1)
            for (y in sizeY - 1 downTo 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posY.set(idx)
                }
                idx -= strideY
            }
        }
    }

    // Z direction (for each X/Y line).
    for (y in 0 until sizeY) {
        for (x in 0 until sizeX) {
            var seenSolid = false
            var idx = x + strideY * y
            for (z in 0 until sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negZ.set(idx)
                }
                idx += strideZ
            }
            seenSolid = false
            idx = x + strideY * y + strideZ * (sizeZ - 1)
            for (z in sizeZ - 1 downTo 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posZ.set(idx)
                }
                idx -= strideZ
            }
        }
    }

    // Diagonal directions in the XZ plane (for each Y slice).
    val diagInc = 1 + strideZ
    val diagDec = 1 - strideZ
    val diagDecRev = strideZ - 1
    for (y in 0 until sizeY) {
        val yBase = strideY * y

        // (-X,-Z) / (+X,+Z)
        for (startX in 0 until sizeX) {
            var x = startX
            var z = 0
            var idx = yBase + startX
            var seenSolid = false
            while (x < sizeX && z < sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negXZ.set(idx)
                }
                x++
                z++
                idx += diagInc
            }
        }
        for (startZ in 1 until sizeZ) {
            var x = 0
            var z = startZ
            var idx = yBase + strideZ * startZ
            var seenSolid = false
            while (x < sizeX && z < sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negXZ.set(idx)
                }
                x++
                z++
                idx += diagInc
            }
        }
        for (startX in 0 until sizeX) {
            var x = startX
            var z = sizeZ - 1
            var idx = yBase + startX + strideZ * (sizeZ - 1)
            var seenSolid = false
            while (x >= 0 && z >= 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posXZ.set(idx)
                }
                x--
                z--
                idx -= diagInc
            }
        }
        for (startZ in sizeZ - 2 downTo 0) {
            var x = sizeX - 1
            var z = startZ
            var idx = yBase + (sizeX - 1) + strideZ * startZ
            var seenSolid = false
            while (x >= 0 && z >= 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posXZ.set(idx)
                }
                x--
                z--
                idx -= diagInc
            }
        }

        // (-X,+Z) / (+X,-Z)
        for (startX in 0 until sizeX) {
            var x = startX
            var z = sizeZ - 1
            var idx = yBase + startX + strideZ * (sizeZ - 1)
            var seenSolid = false
            while (x < sizeX && z >= 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negXPosZ.set(idx)
                }
                x++
                z--
                idx += diagDec
            }
        }
        for (startZ in sizeZ - 2 downTo 0) {
            var x = 0
            var z = startZ
            var idx = yBase + strideZ * startZ
            var seenSolid = false
            while (x < sizeX && z >= 0) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    negXPosZ.set(idx)
                }
                x++
                z--
                idx += diagDec
            }
        }
        for (startX in 0 until sizeX) {
            var x = startX
            var z = 0
            var idx = yBase + startX
            var seenSolid = false
            while (x >= 0 && z < sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posXNegZ.set(idx)
                }
                x--
                z++
                idx += diagDecRev
            }
        }
        for (startZ in 1 until sizeZ) {
            var x = sizeX - 1
            var z = startZ
            var idx = yBase + (sizeX - 1) + strideZ * startZ
            var seenSolid = false
            while (x >= 0 && z < sizeZ) {
                if (!open.get(idx)) {
                    seenSolid = true
                } else if (seenSolid) {
                    posXNegZ.set(idx)
                }
                x--
                z++
                idx += diagDecRev
            }
        }
    }

    val interior = BitSet(volume)
    var idx = open.nextSetBit(0)
    while (idx >= 0 && idx < volume) {
        val lx = idx % sizeX
        val t = idx / sizeX
        val ly = t % sizeY
        val lz = t / sizeY

        if (lx == 0 || lx + 1 == sizeX || ly == 0 || ly + 1 == sizeY || lz == 0 || lz + 1 == sizeZ) {
            idx = open.nextSetBit(idx + 1)
            continue
        }

        val pairX = negX.get(idx) && posX.get(idx)
        val pairY = negY.get(idx) && posY.get(idx)
        val pairZ = negZ.get(idx) && posZ.get(idx)
        val pairDiagA = negXZ.get(idx) && posXZ.get(idx)
        val pairDiagB = negXPosZ.get(idx) && posXNegZ.get(idx)

        var axisPairs = 0
        if (pairX) axisPairs++
        if (pairY) axisPairs++
        if (pairZ) axisPairs++
        if (pairDiagA) axisPairs++
        if (pairDiagB) axisPairs++

        val diagPairs = (if (pairDiagA) 1 else 0) + (if (pairDiagB) 1 else 0)
        if (axisPairs >= 3 && (pairY || diagPairs == 2)) {
            interior.set(idx)
        }

        idx = open.nextSetBit(idx + 1)
    }

    return interior
}
