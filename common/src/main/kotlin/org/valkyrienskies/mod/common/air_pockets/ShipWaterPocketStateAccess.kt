package org.valkyrienskies.mod.common.air_pockets

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth

internal enum class PointVoidClass {
    OUT_OF_BOUNDS,
    SOLID,
    EXTERIOR_VOID,
    INTERIOR_VOID,
}

internal data class PointVoidClassification(
    val kind: PointVoidClass,
    val voxelIndex: Int = -1,
    val voxelX: Int = 0,
    val voxelY: Int = 0,
    val voxelZ: Int = 0,
    val localComponent: Int = -1,
)

private const val POINT_CLASSIFY_EPS = 1e-5

internal fun indexOf(state: ShipPocketState, lx: Int, ly: Int, lz: Int): Int =
    lx + state.sizeX * (ly + state.sizeY * lz)

internal fun isBoundaryShellIndex(state: ShipPocketState, idx: Int): Boolean {
    val volume = state.sizeX * state.sizeY * state.sizeZ
    if (idx !in 0 until volume) return false
    val sx = state.sizeX
    val sy = state.sizeY
    val lx = idx % sx
    val t = idx / sx
    val ly = t % sy
    val lz = t / sy
    return lx == 0 || lx + 1 == state.sizeX ||
        ly == 0 || ly + 1 == state.sizeY ||
        lz == 0 || lz + 1 == state.sizeZ
}

internal fun shouldPreventExteriorWaterlogging(state: ShipPocketState, idx: Int): Boolean {
    val volume = state.sizeX * state.sizeY * state.sizeZ
    if (idx !in 0 until volume) return false
    if (!state.open.get(idx)) return false
    // Exterior waterlogging must never come from world-fluid contact. Only the simulated domain is allowed to
    // waterlog/drain as part of flooding/draining (or manual placement).
    return !state.simulationDomain.get(idx)
}

internal fun simulationComponentMaskAt(state: ShipPocketState, idx: Int): Long {
    if (idx < 0) return 0L
    if (idx < state.voxelSimulationComponentMask.size) {
        val direct = state.voxelSimulationComponentMask[idx]
        if (direct != 0L) return direct
    }

    val volume = state.sizeX * state.sizeY * state.sizeZ
    if (idx !in 0 until volume) return 0L
    if (!state.simulationDomain.get(idx)) return 0L

    val templateIndices = state.templateIndexByVoxel
    val templates = state.shapeTemplatePalette
    if (templateIndices.size == volume && templates.isNotEmpty()) {
        val templateIdx = templateIndices[idx]
        if (templateIdx in templates.indices) {
            return fullComponentMask(templates[templateIdx].componentCount)
        }
    }

    return -1L
}

internal fun isClassificationInSimulationDomain(
    state: ShipPocketState,
    classification: PointVoidClassification,
): Boolean {
    val idx = classification.voxelIndex
    if (idx < 0) return false
    if (!state.open.get(idx)) return false

    if (classification.localComponent >= 0) {
        val simMask = simulationComponentMaskAt(state, idx)
        return ((simMask ushr classification.localComponent) and 1L) != 0L
    }
    return state.simulationDomain.get(idx)
}

internal fun posFromIndex(state: ShipPocketState, idx: Int, out: BlockPos.MutableBlockPos): BlockPos.MutableBlockPos {
    val sx = state.sizeX
    val sy = state.sizeY
    val lx = idx % sx
    val t = idx / sx
    val ly = t % sy
    val lz = t / sy
    return out.set(state.minX + lx, state.minY + ly, state.minZ + lz)
}

internal fun edgeConductance(state: ShipPocketState, idx: Int, lx: Int, ly: Int, lz: Int, dirCode: Int): Int {
    if (state.faceCondXP.isEmpty() || state.faceCondYP.isEmpty() || state.faceCondZP.isEmpty()) return 1
    val sizeX = state.sizeX
    val sizeY = state.sizeY
    return when (dirCode) {
        0 -> if (lx > 0) state.faceCondXP[idx - 1].toInt() and 0xFFFF else 0
        1 -> if (lx + 1 < sizeX) state.faceCondXP[idx].toInt() and 0xFFFF else 0
        2 -> if (ly > 0) state.faceCondYP[idx - sizeX].toInt() and 0xFFFF else 0
        3 -> if (ly + 1 < sizeY) state.faceCondYP[idx].toInt() and 0xFFFF else 0
        4 -> if (lz > 0) state.faceCondZP[idx - sizeX * sizeY].toInt() and 0xFFFF else 0
        else -> if (lz + 1 < state.sizeZ) state.faceCondZP[idx].toInt() and 0xFFFF else 0
    }
}

internal fun computeFilteredFaceConductance(
    state: ShipPocketState,
    idxA: Int,
    idxB: Int,
    dirCode: Int,
    componentMaskA: Long = -1L,
    componentMaskB: Long = -1L,
): Int {
    val volume = state.sizeX * state.sizeY * state.sizeZ
    if (idxA !in 0 until volume || idxB !in 0 until volume) return 0
    if (!state.open.get(idxA) || !state.open.get(idxB)) return 0

    val templateIndices = state.templateIndexByVoxel
    val templates = state.shapeTemplatePalette
    if (templateIndices.size == volume && templates.isNotEmpty()) {
        val templateIdxA = templateIndices[idxA]
        val templateIdxB = templateIndices[idxB]
        if (templateIdxA in templates.indices && templateIdxB in templates.indices) {
            return computeTemplateFaceConductance(
                templateA = templates[templateIdxA],
                templateB = templates[templateIdxB],
                dirCodeFromA = dirCode,
                componentMaskA = componentMaskA,
                componentMaskB = componentMaskB,
            )
        }
    }

    if (componentMaskA == 0L || componentMaskB == 0L) return 0

    val lx = idxA % state.sizeX
    val t = idxA / state.sizeX
    val ly = t % state.sizeY
    val lz = t / state.sizeY
    return edgeConductance(state, idxA, lx, ly, lz, dirCode)
}

internal fun classifyShipPoint(
    state: ShipPocketState,
    x: Double,
    y: Double,
    z: Double,
    out: BlockPos.MutableBlockPos? = null,
): PointVoidClassification {
    val voxelX = Mth.floor(x)
    val voxelY = Mth.floor(y)
    val voxelZ = Mth.floor(z)

    val lx = voxelX - state.minX
    val ly = voxelY - state.minY
    val lz = voxelZ - state.minZ
    if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) {
        return PointVoidClassification(kind = PointVoidClass.OUT_OF_BOUNDS)
    }

    out?.set(voxelX, voxelY, voxelZ)
    val idx = indexOf(state, lx, ly, lz)

    val templateIndices = state.templateIndexByVoxel
    val templates = state.shapeTemplatePalette
    if (templateIndices.size == state.sizeX * state.sizeY * state.sizeZ && templates.isNotEmpty()) {
        val templateIdx = templateIndices[idx]
        if (templateIdx in templates.indices) {
            val template = templates[templateIdx]
            if (template.componentCount <= 0) {
                return PointVoidClassification(
                    kind = PointVoidClass.SOLID,
                    voxelIndex = idx,
                    voxelX = voxelX,
                    voxelY = voxelY,
                    voxelZ = voxelZ,
                )
            }

            val fx = (x - voxelX.toDouble()).coerceIn(0.0, 0.999999999)
            val fy = (y - voxelY.toDouble()).coerceIn(0.0, 0.999999999)
            val fz = (z - voxelZ.toDouble()).coerceIn(0.0, 0.999999999)

            val sx = (fx * SHAPE_SUBCELL_RES.toDouble()).toInt().coerceIn(0, SHAPE_SUBCELL_RES - 1)
            val sy = (fy * SHAPE_SUBCELL_RES.toDouble()).toInt().coerceIn(0, SHAPE_SUBCELL_RES - 1)
            val sz = (fz * SHAPE_SUBCELL_RES.toDouble()).toInt().coerceIn(0, SHAPE_SUBCELL_RES - 1)
            val subIdx = sx + SHAPE_SUBCELL_RES * (sy + SHAPE_SUBCELL_RES * sz)

            val component = template.componentBySubcell[subIdx].toInt()
            if (component < 0) {
                return PointVoidClassification(
                    kind = PointVoidClass.SOLID,
                    voxelIndex = idx,
                    voxelX = voxelX,
                    voxelY = voxelY,
                    voxelZ = voxelZ,
                )
            }

            val interiorMask = if (idx < state.voxelInteriorComponentMask.size) state.voxelInteriorComponentMask[idx] else 0L
            if (((interiorMask ushr component) and 1L) != 0L) {
                return PointVoidClassification(
                    kind = PointVoidClass.INTERIOR_VOID,
                    voxelIndex = idx,
                    voxelX = voxelX,
                    voxelY = voxelY,
                    voxelZ = voxelZ,
                    localComponent = component,
                )
            }

            val exteriorMask = if (idx < state.voxelExteriorComponentMask.size) state.voxelExteriorComponentMask[idx] else 0L
            if (((exteriorMask ushr component) and 1L) != 0L) {
                return PointVoidClassification(
                    kind = PointVoidClass.EXTERIOR_VOID,
                    voxelIndex = idx,
                    voxelX = voxelX,
                    voxelY = voxelY,
                    voxelZ = voxelZ,
                    localComponent = component,
                )
            }

            if (state.open.get(idx)) {
                val fallbackKind = if (state.strictInterior.get(idx)) PointVoidClass.INTERIOR_VOID else PointVoidClass.EXTERIOR_VOID
                return PointVoidClassification(
                    kind = fallbackKind,
                    voxelIndex = idx,
                    voxelX = voxelX,
                    voxelY = voxelY,
                    voxelZ = voxelZ,
                    localComponent = component,
                )
            }
        }
    }

    if (!state.open.get(idx)) {
        return PointVoidClassification(
            kind = PointVoidClass.SOLID,
            voxelIndex = idx,
            voxelX = voxelX,
            voxelY = voxelY,
            voxelZ = voxelZ,
        )
    }

    return PointVoidClassification(
        kind = if (state.strictInterior.get(idx)) PointVoidClass.INTERIOR_VOID else PointVoidClass.EXTERIOR_VOID,
        voxelIndex = idx,
        voxelX = voxelX,
        voxelY = voxelY,
        voxelZ = voxelZ,
        localComponent = 0,
    )
}

internal fun classifyShipPointWithEpsilon(
    state: ShipPocketState,
    x: Double,
    y: Double,
    z: Double,
    out: BlockPos.MutableBlockPos? = null,
): PointVoidClassification {
    val base = classifyShipPoint(state, x, y, z, out)
    if (base.kind == PointVoidClass.INTERIOR_VOID || base.kind == PointVoidClass.EXTERIOR_VOID) {
        return base
    }

    var bestExterior: PointVoidClassification? = null
    val eps = POINT_CLASSIFY_EPS
    for (dx in doubleArrayOf(-eps, 0.0, eps)) {
        for (dy in doubleArrayOf(-eps, 0.0, eps)) {
            for (dz in doubleArrayOf(-eps, 0.0, eps)) {
                if (dx == 0.0 && dy == 0.0 && dz == 0.0) continue
                val candidate = classifyShipPoint(state, x + dx, y + dy, z + dz, out)
                when (candidate.kind) {
                    PointVoidClass.INTERIOR_VOID -> return candidate
                    PointVoidClass.EXTERIOR_VOID -> if (bestExterior == null) bestExterior = candidate
                    else -> Unit
                }
            }
        }
    }

    return bestExterior ?: base
}

internal fun isOpenAtShipPoint(
    state: ShipPocketState,
    x: Double,
    y: Double,
    z: Double,
    tmp: BlockPos.MutableBlockPos,
): Boolean {
    tmp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z))
    return isOpen(state, tmp)
}

internal fun isInterior(state: ShipPocketState, shipPos: BlockPos): Boolean {
    val lx = shipPos.x - state.minX
    val ly = shipPos.y - state.minY
    val lz = shipPos.z - state.minZ
    if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
    val idx = indexOf(state, lx, ly, lz)
    return state.strictInterior.get(idx)
}

internal fun isOpen(state: ShipPocketState, shipPos: BlockPos): Boolean {
    val lx = shipPos.x - state.minX
    val ly = shipPos.y - state.minY
    val lz = shipPos.z - state.minZ
    if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
    val idx = indexOf(state, lx, ly, lz)
    return state.open.get(idx)
}

internal fun isAirPocket(state: ShipPocketState, shipPos: BlockPos): Boolean {
    val lx = shipPos.x - state.minX
    val ly = shipPos.y - state.minY
    val lz = shipPos.z - state.minZ
    if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
    val idx = indexOf(state, lx, ly, lz)
    return state.unreachableVoid.get(idx) && !state.materializedWater.get(idx)
}

internal fun isWorldFluidSuppressionCell(state: ShipPocketState, shipPos: BlockPos): Boolean {
    val lx = shipPos.x - state.minX
    val ly = shipPos.y - state.minY
    val lz = shipPos.z - state.minZ
    if (lx !in 0 until state.sizeX || ly !in 0 until state.sizeY || lz !in 0 until state.sizeZ) return false
    val idx = indexOf(state, lx, ly, lz)
    return state.simulationDomain.get(idx) && !state.materializedWater.get(idx)
}
