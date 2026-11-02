package org.valkyrienskies.mod.common.air_pockets

import java.util.BitSet

internal const val COMPONENT_TRAVERSAL_MAX_STEPS: Int = 12_000_000

internal fun hasComponentTraversalSupport(state: ShipPocketState): Boolean {
    val volume = state.sizeX * state.sizeY * state.sizeZ
    if (volume <= 0) return false
    if (state.componentGraphDegraded) return false
    if (state.templateIndexByVoxel.size != volume) return false
    if (state.shapeTemplatePalette.isEmpty()) return false
    if (state.voxelInteriorComponentMask.size < volume) return false
    if (state.voxelExteriorComponentMask.size < volume) return false
    if (state.voxelSimulationComponentMask.size < volume) return false
    return true
}

internal fun interiorComponentMaskAt(state: ShipPocketState, idx: Int): Long {
    if (idx < 0 || idx >= state.voxelInteriorComponentMask.size) return 0L
    return state.voxelInteriorComponentMask[idx]
}

internal fun exteriorComponentMaskAt(state: ShipPocketState, idx: Int): Long {
    if (idx < 0 || idx >= state.voxelExteriorComponentMask.size) return 0L
    return state.voxelExteriorComponentMask[idx]
}

internal fun allOpenComponentMaskAt(state: ShipPocketState, idx: Int): Long {
    return interiorComponentMaskAt(state, idx) or exteriorComponentMaskAt(state, idx)
}

internal fun neighborIndex(state: ShipPocketState, idx: Int, dirCode: Int): Int {
    val sizeX = state.sizeX
    val sizeY = state.sizeY
    val sizeZ = state.sizeZ
    val strideY = sizeX
    val strideZ = sizeX * sizeY

    val lx = idx % sizeX
    val t = idx / sizeX
    val ly = t % sizeY
    val lz = t / sizeY

    return when (dirCode) {
        0 -> if (lx > 0) idx - 1 else -1
        1 -> if (lx + 1 < sizeX) idx + 1 else -1
        2 -> if (ly > 0) idx - strideY else -1
        3 -> if (ly + 1 < sizeY) idx + strideY else -1
        4 -> if (lz > 0) idx - strideZ else -1
        else -> if (lz + 1 < sizeZ) idx + strideZ else -1
    }
}

internal fun connectedNeighborComponentMask(
    state: ShipPocketState,
    idxA: Int,
    idxB: Int,
    dirCodeFromA: Int,
    componentMaskA: Long,
    componentMaskB: Long,
): Long {
    if (componentMaskA == 0L || componentMaskB == 0L) return 0L
    if (!hasComponentTraversalSupport(state)) {
        return if (computeFilteredFaceConductance(
                state = state,
                idxA = idxA,
                idxB = idxB,
                dirCode = dirCodeFromA,
                componentMaskA = componentMaskA,
                componentMaskB = componentMaskB,
            ) > 0
        ) {
            componentMaskB
        } else {
            0L
        }
    }

    val templateAIdx = state.templateIndexByVoxel[idxA]
    val templateBIdx = state.templateIndexByVoxel[idxB]
    if (templateAIdx !in state.shapeTemplatePalette.indices || templateBIdx !in state.shapeTemplatePalette.indices) {
        return 0L
    }
    val templateA = state.shapeTemplatePalette[templateAIdx]
    val templateB = state.shapeTemplatePalette[templateBIdx]

    var outMask = 0L
    forEachTemplateFaceConnection(templateA, templateB, dirCodeFromA) { componentA, componentB ->
        if (((componentMaskA ushr componentA) and 1L) == 0L) return@forEachTemplateFaceConnection
        if (((componentMaskB ushr componentB) and 1L) == 0L) return@forEachTemplateFaceConnection
        outMask = outMask or (1L shl componentB)
    }
    return outMask
}

internal fun traverseConnectedComponents(
    state: ShipPocketState,
    seedVoxel: Int,
    seedMask: Long,
    allowedMaskAt: (Int) -> Long,
    visitedVoxels: BitSet,
    queueIdx: IntArray,
    queueMask: LongArray,
    visitedMaskByVoxel: LongArray,
    onVisit: (voxelIdx: Int, newlyVisitedMask: Long) -> Unit,
): Boolean {
    if (seedVoxel < 0 || seedMask == 0L) return true
    val volume = state.sizeX * state.sizeY * state.sizeZ
    if (seedVoxel >= volume) return true

    var head = 0
    var tail = 0
    queueIdx[tail] = seedVoxel
    queueMask[tail] = seedMask
    tail++

    var steps = 0
    while (head < tail) {
        if (++steps > COMPONENT_TRAVERSAL_MAX_STEPS) {
            return false
        }
        val curIdx = queueIdx[head]
        val curMaskRaw = queueMask[head]
        head++

        val allowedCurMask = allowedMaskAt(curIdx)
        val newMask = (curMaskRaw and allowedCurMask) and visitedMaskByVoxel[curIdx].inv()
        if (newMask == 0L) continue

        visitedMaskByVoxel[curIdx] = visitedMaskByVoxel[curIdx] or newMask
        visitedVoxels.set(curIdx)
        onVisit(curIdx, newMask)

        for (dirCode in 0..5) {
            val nIdx = neighborIndex(state, curIdx, dirCode)
            if (nIdx < 0 || nIdx >= volume) continue
            if (!state.open.get(nIdx)) continue

            val nAllowed = allowedMaskAt(nIdx)
            if (nAllowed == 0L) continue

            val nMask = connectedNeighborComponentMask(
                state = state,
                idxA = curIdx,
                idxB = nIdx,
                dirCodeFromA = dirCode,
                componentMaskA = newMask,
                componentMaskB = nAllowed,
            ) and visitedMaskByVoxel[nIdx].inv()
            if (nMask == 0L) continue

            if (tail >= queueIdx.size || tail >= queueMask.size) return false
            queueIdx[tail] = nIdx
            queueMask[tail] = nMask
            tail++
        }
    }
    return true
}
