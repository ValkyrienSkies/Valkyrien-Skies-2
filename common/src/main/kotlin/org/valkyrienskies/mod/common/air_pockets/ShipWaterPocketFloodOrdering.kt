package org.valkyrienskies.mod.common.air_pockets

import it.unimi.dsi.fastutil.ints.IntArrayList

internal data class PendingFloodComponentOrder(
    val orderedIndices: IntArrayList,
    val fairnessWeight: Int,
)

internal fun mergeOrderedFloodComponentAdds(components: List<PendingFloodComponentOrder>): IntArrayList {
    var totalSize = 0
    for (component in components) {
        totalSize += component.orderedIndices.size
    }

    val merged = IntArrayList(totalSize)
    if (components.isEmpty()) return merged

    val cursors = IntArray(components.size)

    // Give every pending component an immediate slot so disconnected pockets start flooding together.
    for (i in components.indices) {
        val ordered = components[i].orderedIndices
        if (ordered.isEmpty) continue
        merged.add(ordered.getInt(0))
        cursors[i] = 1
    }

    var progressed = true
    while (progressed) {
        progressed = false
        for (i in components.indices) {
            val ordered = components[i].orderedIndices
            val cycleBudget = components[i].fairnessWeight.coerceAtLeast(1)
            var emitted = 0
            while (emitted < cycleBudget && cursors[i] < ordered.size) {
                merged.add(ordered.getInt(cursors[i]))
                cursors[i]++
                emitted++
                progressed = true
            }
        }
    }

    return merged
}
