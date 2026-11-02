package org.valkyrienskies.mod.common.air_pockets

import java.util.BitSet

private fun condU16(v: Short): Int = v.toInt() and 0xFFFF

internal fun computeOutsideVoidFromGeometry(
    open: BitSet,
    simulationDomain: BitSet,
    sizeX: Int,
    sizeY: Int,
    sizeZ: Int,
    faceCondXP: ShortArray,
    faceCondYP: ShortArray,
    faceCondZP: ShortArray,
    passCondThreshold: Int = MIN_OPENING_CONDUCTANCE,
): BitSet {
    val volumeLong = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
    if (volumeLong <= 0L) return BitSet()
    val volume = volumeLong.toInt()

    val outsideCandidates = open.clone() as BitSet
    outsideCandidates.andNot(simulationDomain)
    if (outsideCandidates.isEmpty) return BitSet(volume)

    val visited = BitSet(volume)
    val queue = IntArray(volume)
    var head = 0
    var tail = 0

    val strideY = sizeX
    val strideZ = sizeX * sizeY

    fun edgeCond(idxCur: Int, lx: Int, ly: Int, lz: Int, dirCode: Int): Int {
        return when (dirCode) {
            0 -> if (lx > 0) condU16(faceCondXP[idxCur - 1]) else 0
            1 -> if (lx + 1 < sizeX) condU16(faceCondXP[idxCur]) else 0
            2 -> if (ly > 0) condU16(faceCondYP[idxCur - strideY]) else 0
            3 -> if (ly + 1 < sizeY) condU16(faceCondYP[idxCur]) else 0
            4 -> if (lz > 0) condU16(faceCondZP[idxCur - strideZ]) else 0
            else -> if (lz + 1 < sizeZ) condU16(faceCondZP[idxCur]) else 0
        }
    }

    fun tryEnqueue(i: Int) {
        if (i < 0 || i >= volume) return
        if (!outsideCandidates.get(i) || visited.get(i)) return
        visited.set(i)
        queue[tail++] = i
    }

    forEachBoundaryIndexGraph(sizeX, sizeY, sizeZ) { boundaryIdx ->
        tryEnqueue(boundaryIdx)
    }

    fun trySpread(cur: Int, lx: Int, ly: Int, lz: Int, n: Int, dirCode: Int) {
        if (n < 0 || n >= volume) return
        if (!outsideCandidates.get(n) || visited.get(n)) return
        val cond = edgeCond(cur, lx, ly, lz, dirCode)
        if (cond >= passCondThreshold) {
            visited.set(n)
            queue[tail++] = n
        }
    }

    while (head < tail) {
        val cur = queue[head++]

        val lx = cur % sizeX
        val t = cur / sizeX
        val ly = t % sizeY
        val lz = t / sizeY

        if (lx > 0) trySpread(cur, lx, ly, lz, cur - 1, 0)
        if (lx + 1 < sizeX) trySpread(cur, lx, ly, lz, cur + 1, 1)
        if (ly > 0) trySpread(cur, lx, ly, lz, cur - strideY, 2)
        if (ly + 1 < sizeY) trySpread(cur, lx, ly, lz, cur + strideY, 3)
        if (lz > 0) trySpread(cur, lx, ly, lz, cur - strideZ, 4)
        if (lz + 1 < sizeZ) trySpread(cur, lx, ly, lz, cur + strideZ, 5)
    }

    return visited
}

internal fun computeEnclosedHeuristicFromGeometry(
    open: BitSet,
    sizeX: Int,
    sizeY: Int,
    sizeZ: Int,
    faceCondXP: ShortArray,
    faceCondYP: ShortArray,
    faceCondZP: ShortArray,
    passCondThreshold: Int = MIN_OPENING_CONDUCTANCE,
): BitSet {
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

    // X direction (for each Y/Z line).
    for (z in 0 until sizeZ) {
        for (y in 0 until sizeY) {
            val base = sizeX * (y + sizeY * z)
            var seenWall = false
            for (x in 0 until sizeX) {
                val idx = base + x
                if (!open.get(idx)) {
                    seenWall = true
                    continue
                }
                if (x > 0 && open.get(idx - 1)) {
                    val cond = condU16(faceCondXP[idx - 1])
                    if (cond < passCondThreshold) seenWall = true
                }
                if (seenWall) negX.set(idx)
            }
            seenWall = false
            for (x in sizeX - 1 downTo 0) {
                val idx = base + x
                if (!open.get(idx)) {
                    seenWall = true
                    continue
                }
                if (x + 1 < sizeX && open.get(idx + 1)) {
                    val cond = condU16(faceCondXP[idx])
                    if (cond < passCondThreshold) seenWall = true
                }
                if (seenWall) posX.set(idx)
            }
        }
    }

    // Y direction (for each X/Z line).
    for (z in 0 until sizeZ) {
        for (x in 0 until sizeX) {
            var seenWall = false
            var idx = x + strideZ * z
            for (y in 0 until sizeY) {
                if (!open.get(idx)) {
                    seenWall = true
                } else {
                    if (y > 0 && open.get(idx - strideY)) {
                        val cond = condU16(faceCondYP[idx - strideY])
                        if (cond < passCondThreshold) seenWall = true
                    }
                    if (seenWall) negY.set(idx)
                }
                idx += strideY
            }
            seenWall = false
            idx = x + strideZ * z + strideY * (sizeY - 1)
            for (y in sizeY - 1 downTo 0) {
                if (!open.get(idx)) {
                    seenWall = true
                } else {
                    if (y + 1 < sizeY && open.get(idx + strideY)) {
                        val cond = condU16(faceCondYP[idx])
                        if (cond < passCondThreshold) seenWall = true
                    }
                    if (seenWall) posY.set(idx)
                }
                idx -= strideY
            }
        }
    }

    // Z direction (for each X/Y line).
    for (y in 0 until sizeY) {
        for (x in 0 until sizeX) {
            var seenWall = false
            var idx = x + strideY * y
            for (z in 0 until sizeZ) {
                if (!open.get(idx)) {
                    seenWall = true
                } else {
                    if (z > 0 && open.get(idx - strideZ)) {
                        val cond = condU16(faceCondZP[idx - strideZ])
                        if (cond < passCondThreshold) seenWall = true
                    }
                    if (seenWall) negZ.set(idx)
                }
                idx += strideZ
            }
            seenWall = false
            idx = x + strideY * y + strideZ * (sizeZ - 1)
            for (z in sizeZ - 1 downTo 0) {
                if (!open.get(idx)) {
                    seenWall = true
                } else {
                    if (z + 1 < sizeZ && open.get(idx + strideZ)) {
                        val cond = condU16(faceCondZP[idx])
                        if (cond < passCondThreshold) seenWall = true
                    }
                    if (seenWall) posZ.set(idx)
                }
                idx -= strideZ
            }
        }
    }

    val enclosed = BitSet(volume)
    var idx = open.nextSetBit(0)
    while (idx >= 0 && idx < volume) {
        val lx = idx % sizeX
        val t = idx / sizeX
        val ly = t % sizeY
        val lz = t / sizeY

        // Never treat boundary-shell voxels as enclosed interior.
        if (lx == 0 || lx + 1 == sizeX || ly == 0 || ly + 1 == sizeY || lz == 0 || lz + 1 == sizeZ) {
            idx = open.nextSetBit(idx + 1)
            continue
        }

        var blockedDirs = 0
        val bnx = negX.get(idx)
        val bpx = posX.get(idx)
        val bny = negY.get(idx)
        val bpy = posY.get(idx)
        val bnz = negZ.get(idx)
        val bpz = posZ.get(idx)
        if (bnx) blockedDirs++
        if (bpx) blockedDirs++
        if (bny) blockedDirs++
        if (bpy) blockedDirs++
        if (bnz) blockedDirs++
        if (bpz) blockedDirs++

        var axisPairs = 0
        if (bnx && bpx) axisPairs++
        if (bny && bpy) axisPairs++
        if (bnz && bpz) axisPairs++

        // Require both side-axis pairs to be blocked. Without this, a long cavity that is open on one side can still
        // satisfy the generic "4 dirs + 2 axis pairs" rule and get promoted as enclosed.
        val sideAxisPairsBlocked = (if (bnx && bpx) 1 else 0) + (if (bnz && bpz) 1 else 0)
        if (blockedDirs >= 4 && axisPairs >= 2 && sideAxisPairsBlocked == 2) {
            enclosed.set(idx)
        }

        idx = open.nextSetBit(idx + 1)
    }

    return enclosed
}
