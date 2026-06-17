package org.valkyrienskies.mod.common.air_pockets

import java.util.BitSet

internal fun forEachBoundaryIndexGraph(sizeX: Int, sizeY: Int, sizeZ: Int, cb: (Int) -> Unit) {
    fun idx(lx: Int, ly: Int, lz: Int) = lx + sizeX * (ly + sizeY * lz)

    for (y in 0 until sizeY) {
        for (x in 0 until sizeX) {
            cb(idx(x, y, 0))
            cb(idx(x, y, sizeZ - 1))
        }
    }
    for (z in 0 until sizeZ) {
        for (x in 0 until sizeX) {
            cb(idx(x, 0, z))
            cb(idx(x, sizeY - 1, z))
        }
    }
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            cb(idx(0, y, z))
            cb(idx(sizeX - 1, y, z))
        }
    }
}

internal fun floodFillFromBoundaryGraph(
    open: BitSet,
    sizeX: Int,
    sizeY: Int,
    sizeZ: Int,
    edgeCond: ((idx: Int, lx: Int, ly: Int, lz: Int, dirCode: Int) -> Int)? = null,
): BitSet {
    val volume = sizeX * sizeY * sizeZ
    val visited = BitSet(volume)
    val queue = IntArray(volume)
    var head = 0
    var tail = 0

    fun tryEnqueue(idx: Int) {
        if (!open.get(idx) || visited.get(idx)) return
        visited.set(idx)
        queue[tail++] = idx
    }

    forEachBoundaryIndexGraph(sizeX, sizeY, sizeZ) { idx -> tryEnqueue(idx) }

    val strideY = sizeX
    val strideZ = sizeX * sizeY

    fun canTraverse(idx: Int, lx: Int, ly: Int, lz: Int, dirCode: Int): Boolean {
        val c = edgeCond ?: return true
        return c(idx, lx, ly, lz, dirCode) > 0
    }

    while (head < tail) {
        val idx = queue[head++]

        val lx = idx % sizeX
        val t = idx / sizeX
        val ly = t % sizeY
        val lz = t / sizeY

        if (lx > 0 && canTraverse(idx, lx, ly, lz, 0)) tryEnqueue(idx - 1)
        if (lx + 1 < sizeX && canTraverse(idx, lx, ly, lz, 1)) tryEnqueue(idx + 1)
        if (ly > 0 && canTraverse(idx, lx, ly, lz, 2)) tryEnqueue(idx - strideY)
        if (ly + 1 < sizeY && canTraverse(idx, lx, ly, lz, 3)) tryEnqueue(idx + strideY)
        if (lz > 0 && canTraverse(idx, lx, ly, lz, 4)) tryEnqueue(idx - strideZ)
        if (lz + 1 < sizeZ && canTraverse(idx, lx, ly, lz, 5)) tryEnqueue(idx + strideZ)
    }

    return visited
}

internal fun floodFillFromSeedsGraph(
    open: BitSet,
    sizeX: Int,
    sizeY: Int,
    sizeZ: Int,
    seeds: BitSet,
    edgeCond: ((idx: Int, lx: Int, ly: Int, lz: Int, dirCode: Int) -> Int)? = null,
): BitSet {
    val volume = sizeX * sizeY * sizeZ
    val visited = BitSet(volume)
    val queue = IntArray(volume)
    var head = 0
    var tail = 0

    var idx = seeds.nextSetBit(0)
    while (idx >= 0) {
        if (open.get(idx)) {
            visited.set(idx)
            queue[tail++] = idx
        }
        idx = seeds.nextSetBit(idx + 1)
    }

    val strideY = sizeX
    val strideZ = sizeX * sizeY

    fun tryEnqueue(i: Int) {
        if (!open.get(i) || visited.get(i)) return
        visited.set(i)
        queue[tail++] = i
    }

    fun canTraverse(idxCur: Int, lx: Int, ly: Int, lz: Int, dirCode: Int): Boolean {
        val c = edgeCond ?: return true
        return c(idxCur, lx, ly, lz, dirCode) > 0
    }

    while (head < tail) {
        val cur = queue[head++]

        val lx = cur % sizeX
        val t = cur / sizeX
        val ly = t % sizeY
        val lz = t / sizeY

        if (lx > 0 && canTraverse(cur, lx, ly, lz, 0)) tryEnqueue(cur - 1)
        if (lx + 1 < sizeX && canTraverse(cur, lx, ly, lz, 1)) tryEnqueue(cur + 1)
        if (ly > 0 && canTraverse(cur, lx, ly, lz, 2)) tryEnqueue(cur - strideY)
        if (ly + 1 < sizeY && canTraverse(cur, lx, ly, lz, 3)) tryEnqueue(cur + strideY)
        if (lz > 0 && canTraverse(cur, lx, ly, lz, 4)) tryEnqueue(cur - strideZ)
        if (lz + 1 < sizeZ && canTraverse(cur, lx, ly, lz, 5)) tryEnqueue(cur + strideZ)
    }

    return visited
}
