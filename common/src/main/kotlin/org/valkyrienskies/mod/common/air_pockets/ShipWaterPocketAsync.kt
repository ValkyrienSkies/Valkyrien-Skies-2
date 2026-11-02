package org.valkyrienskies.mod.common.air_pockets

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluid
import java.lang.Double
import java.util.BitSet

internal data class GeometryAsyncSnapshot(
    val generation: Long,
    val invalidationStamp: Long,
    val geometrySignature: Long,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val prevMinX: Int,
    val prevMinY: Int,
    val prevMinZ: Int,
    val prevSizeX: Int,
    val prevSizeY: Int,
    val prevSizeZ: Int,
    val prevSimulationDomain: BitSet,
    val floodFluid: Fluid,
    val blockStates: Array<BlockState>,
    val shapeGeometry: Array<ShapeWaterGeometry>,
)

internal data class GeometryAsyncResult(
    val generation: Long,
    val invalidationStamp: Long,
    val geometrySignature: Long,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val open: BitSet,
    val exterior: BitSet,
    val strictInterior: BitSet,
    val simulationDomain: BitSet,
    val interior: BitSet,
    val flooded: BitSet,
    val materializedWater: BitSet,
    val outsideVoid: BitSet,
    val faceCondXP: ShortArray,
    val faceCondYP: ShortArray,
    val faceCondZP: ShortArray,
    val templatePalette: List<ShapeCellTemplate>,
    val templateIndexByVoxel: IntArray,
    val voxelExteriorComponentMask: LongArray,
    val voxelInteriorComponentMask: LongArray,
    val voxelSimulationComponentMask: LongArray,
    val componentGraphDegraded: Boolean,
    val computeNanos: Long,
)

private val EMPTY_GEOMETRY = ShapeWaterGeometry(
    fullSolid = false,
    refined = false,
    boxes = emptyList(),
)

private fun canonicalFloodSource(fluid: Fluid): Fluid = floodCanonicalSource(fluid)

private fun mixHash64(acc: Long, value: Long): Long {
    var h = acc xor value
    h *= -7046029254386353131L
    h = h xor (h ushr 32)
    h *= -7046029254386353131L
    return h xor (h ushr 29)
}

private fun geometryStateHash(blockState: BlockState, geom: ShapeWaterGeometry, idx: Int): Long {
    var h = -7046029254386353131L
    h = mixHash64(h, idx.toLong())
    h = mixHash64(h, blockState.hashCode().toLong())
    h = mixHash64(h, if (geom.fullSolid) 0xF00DL else 0x0L)
    h = mixHash64(h, if (geom.refined) 0xBEEFL else 0x0L)
    h = mixHash64(h, geom.boxes.size.toLong())
    for (box in geom.boxes) {
        h = mixHash64(h, Double.doubleToLongBits(box.minX))
        h = mixHash64(h, Double.doubleToLongBits(box.minY))
        h = mixHash64(h, Double.doubleToLongBits(box.minZ))
        h = mixHash64(h, Double.doubleToLongBits(box.maxX))
        h = mixHash64(h, Double.doubleToLongBits(box.maxY))
        h = mixHash64(h, Double.doubleToLongBits(box.maxZ))
    }
    return h
}

private fun countsAsMaterializedFloodFluid(state: BlockState, floodFluid: Fluid): Boolean {
    val currentFluid = state.fluidState
    if (currentFluid.isEmpty) return false
    if (floodCanonicalSource(currentFluid.type) != floodCanonicalSource(floodFluid)) return false
    if (state.block is LiquidBlock) return true
    return isWaterloggableFloodState(state, floodFluid) && state.getValue(BlockStateProperties.WATERLOGGED)
}

private const val MAX_COMPONENT_GRAPH_NODES = 12_000_000
private const val MIN_HEURISTIC_PROMOTED_COMPONENT_SIZE = 8

private class ShapeTemplateKey(
    private val fullSolid: Boolean,
    private val refined: Boolean,
    private val boxBits: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShapeTemplateKey) return false
        if (fullSolid != other.fullSolid) return false
        if (refined != other.refined) return false
        return boxBits.contentEquals(other.boxBits)
    }

    override fun hashCode(): Int {
        var result = fullSolid.hashCode()
        result = 31 * result + refined.hashCode()
        result = 31 * result + boxBits.contentHashCode()
        return result
    }

    companion object {
        fun fromGeometry(geom: ShapeWaterGeometry): ShapeTemplateKey {
            val bits = LongArray(geom.boxes.size * 6)
            var i = 0
            for (box in geom.boxes) {
                bits[i++] = Double.doubleToLongBits(box.minX)
                bits[i++] = Double.doubleToLongBits(box.minY)
                bits[i++] = Double.doubleToLongBits(box.minZ)
                bits[i++] = Double.doubleToLongBits(box.maxX)
                bits[i++] = Double.doubleToLongBits(box.maxY)
                bits[i++] = Double.doubleToLongBits(box.maxZ)
            }
            return ShapeTemplateKey(
                fullSolid = geom.fullSolid,
                refined = geom.refined,
                boxBits = bits,
            )
        }
    }
}

internal fun captureGeometryAsyncSnapshot(
    level: Level,
    generation: Long,
    invalidationStamp: Long,
    minX: Int,
    minY: Int,
    minZ: Int,
    sizeX: Int,
    sizeY: Int,
    sizeZ: Int,
    prevMinX: Int,
    prevMinY: Int,
    prevMinZ: Int,
    prevSizeX: Int,
    prevSizeY: Int,
    prevSizeZ: Int,
    prevSimulationDomain: BitSet,
    floodFluid: Fluid,
): GeometryAsyncSnapshot {
    val volume = sizeX * sizeY * sizeZ
    val blockStates = Array(volume) { Blocks.AIR.defaultBlockState() }
    val shapeGeometry = Array(volume) { EMPTY_GEOMETRY }
    var signature = 0x1234_5678_9ABCL
    signature = mixHash64(signature, sizeX.toLong())
    signature = mixHash64(signature, sizeY.toLong())
    signature = mixHash64(signature, sizeZ.toLong())
    signature = mixHash64(signature, minX.toLong())
    signature = mixHash64(signature, minY.toLong())
    signature = mixHash64(signature, minZ.toLong())

    val pos = BlockPos.MutableBlockPos()
    var idx = 0
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            for (x in 0 until sizeX) {
                pos.set(minX + x, minY + y, minZ + z)
                val state = level.getBlockState(pos)
                blockStates[idx] = state
                val geometry = computeShapeWaterGeometry(level, pos, state)
                shapeGeometry[idx] = geometry
                signature = mixHash64(signature, geometryStateHash(state, geometry, idx))
                idx++
            }
        }
    }

    return GeometryAsyncSnapshot(
        generation = generation,
        invalidationStamp = invalidationStamp,
        geometrySignature = signature,
        minX = minX,
        minY = minY,
        minZ = minZ,
        sizeX = sizeX,
        sizeY = sizeY,
        sizeZ = sizeZ,
        prevMinX = prevMinX,
        prevMinY = prevMinY,
        prevMinZ = prevMinZ,
        prevSizeX = prevSizeX,
        prevSizeY = prevSizeY,
        prevSizeZ = prevSizeZ,
        prevSimulationDomain = prevSimulationDomain,
        floodFluid = floodFluid,
        blockStates = blockStates,
        shapeGeometry = shapeGeometry,
    )
}

internal fun computeGeometryAsync(snapshot: GeometryAsyncSnapshot): GeometryAsyncResult {
    val startNanos = System.nanoTime()

    val sizeX = snapshot.sizeX
    val sizeY = snapshot.sizeY
    val sizeZ = snapshot.sizeZ
    val volume = sizeX * sizeY * sizeZ

    val open = BitSet(volume)
    val flooded = BitSet(volume)
    val materialized = BitSet(volume)
    val faceCondXP = ShortArray(volume)
    val faceCondYP = ShortArray(volume)
    val faceCondZP = ShortArray(volume)
    val templateIndexByVoxel = IntArray(volume)
    val templatePalette = ArrayList<ShapeCellTemplate>()
    val templateLookup = HashMap<ShapeTemplateKey, Int>()

    var idx = 0
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            for (x in 0 until sizeX) {
                val geom = snapshot.shapeGeometry[idx]
                val templateKey = ShapeTemplateKey.fromGeometry(geom)
                val templateIdx = templateLookup.getOrPut(templateKey) {
                    val next = templatePalette.size
                    templatePalette.add(buildShapeCellTemplate(geom))
                    next
                }
                templateIndexByVoxel[idx] = templateIdx
                val template = templatePalette[templateIdx]
                if (template.hasOpenVolume) {
                    open.set(idx)
                }

                val bs = snapshot.blockStates[idx]
                if (countsAsMaterializedFloodFluid(bs, snapshot.floodFluid)) {
                    flooded.set(idx)
                    materialized.set(idx)
                }

                idx++
            }
        }
    }

    val strideY = sizeX
    val strideZ = sizeX * sizeY
    val nodeBaseByVoxel = IntArray(volume) { -1 }
    var nodeCount = 0
    var componentGraphDegraded = false

    var openIdx = open.nextSetBit(0)
    while (openIdx >= 0 && openIdx < volume) {
        val template = templatePalette[templateIndexByVoxel[openIdx]]
        val componentCount = template.componentCount
        if (componentCount > 0) {
            nodeBaseByVoxel[openIdx] = nodeCount
            nodeCount += componentCount
            if (nodeCount > MAX_COMPONENT_GRAPH_NODES) {
                componentGraphDegraded = true
                break
            }
        }
        openIdx = open.nextSetBit(openIdx + 1)
    }

    val parent = if (!componentGraphDegraded) IntArray(nodeCount) { it } else IntArray(0)
    val rank = if (!componentGraphDegraded) ByteArray(nodeCount) else ByteArray(0)
    val boundaryNode = if (!componentGraphDegraded) BooleanArray(nodeCount) else BooleanArray(0)

    fun findRoot(x: Int): Int {
        if (componentGraphDegraded) return 0
        var cur = x
        while (parent[cur] != cur) {
            cur = parent[cur]
        }
        var walk = x
        while (parent[walk] != walk) {
            val next = parent[walk]
            parent[walk] = cur
            walk = next
        }
        return cur
    }

    fun unionNodes(a: Int, b: Int) {
        if (componentGraphDegraded) return
        var ra = findRoot(a)
        var rb = findRoot(b)
        if (ra == rb) return

        val rankA = rank[ra].toInt()
        val rankB = rank[rb].toInt()
        if (rankA < rankB) {
            val t = ra
            ra = rb
            rb = t
        }

        parent[rb] = ra
        if (rankA == rankB) {
            rank[ra] = (rankA + 1).toByte()
        }
    }

    fun markBoundaryComponents(baseNode: Int, componentMask: Long) {
        if (componentGraphDegraded || componentMask == 0L || baseNode < 0) return
        var mask = componentMask
        while (mask != 0L) {
            val bit = java.lang.Long.numberOfTrailingZeros(mask)
            boundaryNode[baseNode + bit] = true
            mask = mask and (mask - 1L)
        }
    }

    idx = 0
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            for (x in 0 until sizeX) {
                if (!open.get(idx)) {
                    idx++
                    continue
                }

                val templateIdx = templateIndexByVoxel[idx]
                val template = templatePalette[templateIdx]
                val baseNode = nodeBaseByVoxel[idx]

                if (!componentGraphDegraded && baseNode >= 0) {
                    if (x == 0) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_NEG_X])
                    if (x + 1 == sizeX) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_POS_X])
                    if (y == 0) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_NEG_Y])
                    if (y + 1 == sizeY) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_POS_Y])
                    if (z == 0) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_NEG_Z])
                    if (z + 1 == sizeZ) markBoundaryComponents(baseNode, template.faceComponentMask[SHAPE_FACE_POS_Z])
                }

                if (x + 1 < sizeX) {
                    val n = idx + 1
                    if (open.get(n)) {
                        val nTemplate = templatePalette[templateIndexByVoxel[n]]
                        var cond = 0
                        forEachTemplateFaceConnection(template, nTemplate, dirCodeFromA = 1) { compA, compB ->
                            cond++
                            if (!componentGraphDegraded) {
                                val nBase = nodeBaseByVoxel[n]
                                if (baseNode >= 0 && nBase >= 0) {
                                    unionNodes(baseNode + compA, nBase + compB)
                                }
                            }
                        }
                        faceCondXP[idx] = cond.toShort()
                    }
                }
                if (y + 1 < sizeY) {
                    val n = idx + strideY
                    if (open.get(n)) {
                        val nTemplate = templatePalette[templateIndexByVoxel[n]]
                        var cond = 0
                        forEachTemplateFaceConnection(template, nTemplate, dirCodeFromA = 3) { compA, compB ->
                            cond++
                            if (!componentGraphDegraded) {
                                val nBase = nodeBaseByVoxel[n]
                                if (baseNode >= 0 && nBase >= 0) {
                                    unionNodes(baseNode + compA, nBase + compB)
                                }
                            }
                        }
                        faceCondYP[idx] = cond.toShort()
                    }
                }
                if (z + 1 < sizeZ) {
                    val n = idx + strideZ
                    if (open.get(n)) {
                        val nTemplate = templatePalette[templateIndexByVoxel[n]]
                        var cond = 0
                        forEachTemplateFaceConnection(template, nTemplate, dirCodeFromA = 5) { compA, compB ->
                            cond++
                            if (!componentGraphDegraded) {
                                val nBase = nodeBaseByVoxel[n]
                                if (baseNode >= 0 && nBase >= 0) {
                                    unionNodes(baseNode + compA, nBase + compB)
                                }
                            }
                        }
                        faceCondZP[idx] = cond.toShort()
                    }
                }

                idx++
            }
        }
    }

    fun edgeCond(idxCur: Int, lx: Int, ly: Int, lz: Int, dirCode: Int): Int {
        return when (dirCode) {
            0 -> if (lx > 0) faceCondXP[idxCur - 1].toInt() and 0xFFFF else 0
            1 -> if (lx + 1 < sizeX) faceCondXP[idxCur].toInt() and 0xFFFF else 0
            2 -> if (ly > 0) faceCondYP[idxCur - strideY].toInt() and 0xFFFF else 0
            3 -> if (ly + 1 < sizeY) faceCondYP[idxCur].toInt() and 0xFFFF else 0
            4 -> if (lz > 0) faceCondZP[idxCur - strideZ].toInt() and 0xFFFF else 0
            else -> if (lz + 1 < sizeZ) faceCondZP[idxCur].toInt() and 0xFFFF else 0
        }
    }

    val exterior = BitSet(volume)
    val strictInterior = BitSet(volume)
    val simulationDomain = BitSet(volume)
    val voxelExteriorComponentMask = LongArray(volume)
    val voxelInteriorComponentMask = LongArray(volume)
    val voxelSimulationComponentMask = LongArray(volume)

    if (componentGraphDegraded) {
        val strictExterior = floodFillFromBoundaryGraph(open, sizeX, sizeY, sizeZ) { idxCur, lx, ly, lz, dir ->
            edgeCond(idxCur, lx, ly, lz, dir)
        }
        val degradedInterior = open.clone() as BitSet
        degradedInterior.andNot(strictExterior)
        exterior.or(strictExterior)
        strictInterior.or(degradedInterior)
        simulationDomain.or(degradedInterior)

        // Add a conductance-based enclosure heuristic so leaky pockets remain simulated even when strict interior
        // collapses under holes. This intentionally does NOT subtract strictExterior.
        val enclosedHeuristic = computeEnclosedHeuristicFromGeometry(
            open = open,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            faceCondXP = faceCondXP,
            faceCondYP = faceCondYP,
            faceCondZP = faceCondZP,
            passCondThreshold = MIN_OPENING_CONDUCTANCE,
        )
        val promoted = enclosedHeuristic.clone() as BitSet
        promoted.andNot(simulationDomain)

        val promotedVisited = BitSet(volume)
        val promotedQueue = IntArray(volume)

        var startPromoted = promoted.nextSetBit(0)
        while (startPromoted >= 0 && startPromoted < volume) {
            if (promotedVisited.get(startPromoted)) {
                startPromoted = promoted.nextSetBit(startPromoted + 1)
                continue
            }

            var head = 0
            var tail = 0
            var componentSize = 0
            var hasNonBoundaryCell = false
            var touchesAnchoredCell = false

            promotedVisited.set(startPromoted)
            promotedQueue[tail++] = startPromoted

            fun tryEnqueuePromoted(idx: Int) {
                if (idx < 0 || idx >= volume) return
                if (!promoted.get(idx) || promotedVisited.get(idx)) return
                promotedVisited.set(idx)
                promotedQueue[tail++] = idx
            }

            while (head < tail) {
                val cur = promotedQueue[head++]
                componentSize++

                val cx = cur % sizeX
                val ct = cur / sizeX
                val cy = ct % sizeY
                val cz = ct / sizeY

                if (cx > 0 && cx + 1 < sizeX &&
                    cy > 0 && cy + 1 < sizeY &&
                    cz > 0 && cz + 1 < sizeZ
                ) {
                    hasNonBoundaryCell = true
                }

                if (flooded.get(cur) || materialized.get(cur)) {
                    touchesAnchoredCell = true
                }

                if (cx > 0) tryEnqueuePromoted(cur - 1)
                if (cx + 1 < sizeX) tryEnqueuePromoted(cur + 1)
                if (cy > 0) tryEnqueuePromoted(cur - strideY)
                if (cy + 1 < sizeY) tryEnqueuePromoted(cur + strideY)
                if (cz > 0) tryEnqueuePromoted(cur - strideZ)
                if (cz + 1 < sizeZ) tryEnqueuePromoted(cur + strideZ)
            }

            val promoteComponent = touchesAnchoredCell ||
                (hasNonBoundaryCell && componentSize >= MIN_HEURISTIC_PROMOTED_COMPONENT_SIZE)

            if (promoteComponent) {
                for (i in 0 until tail) {
                    val cur = promotedQueue[i]
                    simulationDomain.set(cur)
                    if (voxelSimulationComponentMask[cur] == 0L) {
                        val template = templatePalette[templateIndexByVoxel[cur]]
                        voxelSimulationComponentMask[cur] = fullComponentMask(template.componentCount)
                    }
                }
            }

            startPromoted = promoted.nextSetBit(startPromoted + 1)
        }

        openIdx = open.nextSetBit(0)
        while (openIdx >= 0 && openIdx < volume) {
            val template = templatePalette[templateIndexByVoxel[openIdx]]
            val fullMask = fullComponentMask(template.componentCount)
            if (strictExterior.get(openIdx)) {
                voxelExteriorComponentMask[openIdx] = fullMask
            }
            if (degradedInterior.get(openIdx)) {
                voxelInteriorComponentMask[openIdx] = fullMask
                voxelSimulationComponentMask[openIdx] = fullMask
            }
            openIdx = open.nextSetBit(openIdx + 1)
        }
    } else {
        val rootBoundary = BooleanArray(nodeCount)
        for (node in 0 until nodeCount) {
            if (!boundaryNode[node]) continue
            rootBoundary[findRoot(node)] = true
        }

        openIdx = open.nextSetBit(0)
        while (openIdx >= 0 && openIdx < volume) {
            val template = templatePalette[templateIndexByVoxel[openIdx]]
            val baseNode = nodeBaseByVoxel[openIdx]
            var exteriorMask = 0L
            var interiorMask = 0L

            if (baseNode >= 0) {
                for (component in 0 until template.componentCount) {
                    val root = findRoot(baseNode + component)
                    if (rootBoundary[root]) {
                        exteriorMask = exteriorMask or (1L shl component)
                    } else {
                        interiorMask = interiorMask or (1L shl component)
                    }
                }
            }

            voxelExteriorComponentMask[openIdx] = exteriorMask
            voxelInteriorComponentMask[openIdx] = interiorMask
            if (exteriorMask != 0L) exterior.set(openIdx)
            if (interiorMask != 0L) {
                strictInterior.set(openIdx)
                simulationDomain.set(openIdx)
                voxelSimulationComponentMask[openIdx] = interiorMask
            }

            openIdx = open.nextSetBit(openIdx + 1)
        }

        // Keep gameplay flood-domain projection: promote voxels that look locally enclosed under shape conductance.
        // This is intentionally NOT based on boundary connectivity, so leaky pockets stay simulated under holes.
        //
        // The heuristic is later filtered by component size to avoid pulling tiny exterior stair/trapdoor cavities
        // into the simulation domain.
        val enclosedHeuristic = computeEnclosedHeuristicFromGeometry(
            open = open,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            faceCondXP = faceCondXP,
            faceCondYP = faceCondYP,
            faceCondZP = faceCondZP,
            passCondThreshold = MIN_OPENING_CONDUCTANCE,
        )
        val promotedInterior = enclosedHeuristic.clone() as BitSet
        promotedInterior.andNot(simulationDomain)

        val promotedVisited = BitSet(volume)
        val promotedQueue = IntArray(volume)

        var startPromoted = promotedInterior.nextSetBit(0)
        while (startPromoted >= 0 && startPromoted < volume) {
            if (promotedVisited.get(startPromoted)) {
                startPromoted = promotedInterior.nextSetBit(startPromoted + 1)
                continue
            }

            var head = 0
            var tail = 0
            var componentSize = 0
            var hasNonBoundaryCell = false
            var touchesAnchoredCell = false

            promotedVisited.set(startPromoted)
            promotedQueue[tail++] = startPromoted

            fun tryEnqueuePromoted(idx: Int) {
                if (idx < 0 || idx >= volume) return
                if (!promotedInterior.get(idx) || promotedVisited.get(idx)) return
                promotedVisited.set(idx)
                promotedQueue[tail++] = idx
            }

            while (head < tail) {
                val cur = promotedQueue[head++]
                componentSize++

                val cx = cur % sizeX
                val ct = cur / sizeX
                val cy = ct % sizeY
                val cz = ct / sizeY

                if (cx > 0 && cx + 1 < sizeX &&
                    cy > 0 && cy + 1 < sizeY &&
                    cz > 0 && cz + 1 < sizeZ
                ) {
                    hasNonBoundaryCell = true
                }

                if (flooded.get(cur) || materialized.get(cur)) {
                    touchesAnchoredCell = true
                }

                if (cx > 0) tryEnqueuePromoted(cur - 1)
                if (cx + 1 < sizeX) tryEnqueuePromoted(cur + 1)
                if (cy > 0) tryEnqueuePromoted(cur - strideY)
                if (cy + 1 < sizeY) tryEnqueuePromoted(cur + strideY)
                if (cz > 0) tryEnqueuePromoted(cur - strideZ)
                if (cz + 1 < sizeZ) tryEnqueuePromoted(cur + strideZ)
            }

            val promoteComponent = touchesAnchoredCell ||
                (hasNonBoundaryCell && componentSize >= MIN_HEURISTIC_PROMOTED_COMPONENT_SIZE)

            if (promoteComponent) {
                for (i in 0 until tail) {
                    val cur = promotedQueue[i]
                    simulationDomain.set(cur)
                    if (voxelSimulationComponentMask[cur] == 0L) {
                        val template = templatePalette[templateIndexByVoxel[cur]]
                        voxelSimulationComponentMask[cur] = fullComponentMask(template.componentCount)
                    }
                }
            }

            startPromoted = promotedInterior.nextSetBit(startPromoted + 1)
        }
    }

    // Merge previous simulation domain (bounds remap) to prevent one-tick domain collapse when bounds shift.
    run {
        val prev = snapshot.prevSimulationDomain
        if (!prev.isEmpty) {
            val prevSizeX = snapshot.prevSizeX
            val prevSizeY = snapshot.prevSizeY
            val prevSizeZ = snapshot.prevSizeZ
            val prevVolumeLong = prevSizeX.toLong() * prevSizeY.toLong() * prevSizeZ.toLong()
            if (prevVolumeLong > 0L) {
                val prevVolume = prevVolumeLong.toInt()
                var prevIdx = prev.nextSetBit(0)
                while (prevIdx >= 0 && prevIdx < prevVolume) {
                    val px = prevIdx % prevSizeX
                    val pt = prevIdx / prevSizeX
                    val py = pt % prevSizeY
                    val pz = pt / prevSizeY

                    val worldX = snapshot.prevMinX + px
                    val worldY = snapshot.prevMinY + py
                    val worldZ = snapshot.prevMinZ + pz

                    val lx = worldX - snapshot.minX
                    val ly = worldY - snapshot.minY
                    val lz = worldZ - snapshot.minZ
                    if (lx in 0 until sizeX && ly in 0 until sizeY && lz in 0 until sizeZ) {
                        // Never remap into the boundary shell; that prevents "outside suppression" bleed.
                        if (lx > 0 && lx + 1 < sizeX &&
                            ly > 0 && ly + 1 < sizeY &&
                            lz > 0 && lz + 1 < sizeZ
                        ) {
                            val newIdx = lx + sizeX * (ly + sizeY * lz)
                            if (open.get(newIdx)) {
                                simulationDomain.set(newIdx)
                                if (voxelSimulationComponentMask[newIdx] == 0L) {
                                    val template = templatePalette[templateIndexByVoxel[newIdx]]
                                    voxelSimulationComponentMask[newIdx] = fullComponentMask(template.componentCount)
                                }
                            }
                        }
                    }

                    prevIdx = prev.nextSetBit(prevIdx + 1)
                }
            }
        }
    }
    simulationDomain.and(open)

    // Compute "true outside" void space for this geometry: boundary-connected open volume excluding simulationDomain.
    val outsideVoid = computeOutsideVoidFromGeometry(
        open = open,
        simulationDomain = simulationDomain,
        sizeX = sizeX,
        sizeY = sizeY,
        sizeZ = sizeZ,
        faceCondXP = faceCondXP,
        faceCondYP = faceCondYP,
        faceCondZP = faceCondZP,
        passCondThreshold = MIN_OPENING_CONDUCTANCE,
    )

    // Outside exterior-connected waterloggable cells should not be treated as materialized flood fluid; otherwise
    // ocean contact can add fake in-ship mass/weight.
    openIdx = open.nextSetBit(0)
    while (openIdx >= 0 && openIdx < volume) {
        if (materialized.get(openIdx) && !simulationDomain.get(openIdx)) {
            materialized.clear(openIdx)
            flooded.clear(openIdx)
        }
        openIdx = open.nextSetBit(openIdx + 1)
    }

    return GeometryAsyncResult(
        generation = snapshot.generation,
        invalidationStamp = snapshot.invalidationStamp,
        geometrySignature = snapshot.geometrySignature,
        minX = snapshot.minX,
        minY = snapshot.minY,
        minZ = snapshot.minZ,
        sizeX = sizeX,
        sizeY = sizeY,
        sizeZ = sizeZ,
        open = open,
        exterior = exterior,
        strictInterior = strictInterior,
        simulationDomain = simulationDomain,
        interior = strictInterior,
        flooded = flooded,
        materializedWater = materialized,
        outsideVoid = outsideVoid,
        faceCondXP = faceCondXP,
        faceCondYP = faceCondYP,
        faceCondZP = faceCondZP,
        templatePalette = templatePalette,
        templateIndexByVoxel = templateIndexByVoxel,
        voxelExteriorComponentMask = voxelExteriorComponentMask,
        voxelInteriorComponentMask = voxelInteriorComponentMask,
        voxelSimulationComponentMask = voxelSimulationComponentMask,
        componentGraphDegraded = componentGraphDegraded,
        computeNanos = System.nanoTime() - startNanos,
    )
}
