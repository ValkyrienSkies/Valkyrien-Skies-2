package org.valkyrienskies.mod.common.fluid

/** Game-thread refresh scheduling with immutable snapshots for physics-thread readers. */
internal class FluidFlowSnapshotCache<T> {
    data class Snapshot<T>(val heights: IntRange, val value: T)

    @Volatile
    private var snapshots: Map<String, Map<Long, Snapshot<T>>> = emptyMap()
    private val pending = HashMap<String, LinkedHashMap<Long, IntRange>>()
    private val dirty = HashMap<String, MutableSet<Long>>()

    fun snapshot(dimension: String): Map<Long, Snapshot<T>> = snapshots[dimension].orEmpty()

    @Synchronized
    fun markDirty(dimension: String, chunk: Long) {
        dirty.getOrPut(dimension) { HashSet() }.add(chunk)
    }

    fun refresh(dimension: String, active: Map<Long, IntRange>, budget: Int, scan: (Long, IntRange) -> T?) {
        if (active.isEmpty()) {
            clearDimension(dimension)
            return
        }
        val previous = snapshots[dimension].orEmpty()
        val queue = pending.getOrPut(dimension) { LinkedHashMap() }
        queue.keys.retainAll(active.keys)
        val dirtyChunks = synchronized(this) { dirty.remove(dimension).orEmpty() }
        for ((chunk, heights) in active) {
            val cached = previous[chunk]
            val covered = cached != null && cached.heights.first <= heights.first && cached.heights.last >= heights.last
            if (!covered || chunk in dirtyChunks || chunk in queue) {
                // Include current demand even if this request has waited behind the tick budget.
                queue[chunk] = merge(merge(queue[chunk], cached?.heights), heights)!!
            }
        }
        val next = previous.filterKeys { it in active }.toMutableMap()
        // A missing/unloaded chunk remains pending and is retried, without monopolizing the budget.
        val requests = queue.entries.take(budget.coerceAtLeast(0)).map { it.key to it.value }
        for ((chunk, heights) in requests) {
            queue.remove(chunk)
            val value = scan(chunk, heights)
            if (value == null) {
                next.remove(chunk)
                queue[chunk] = heights
            } else {
                next[chunk] = Snapshot(heights, value)
            }
        }
        snapshots = snapshots + (dimension to next)
    }

    fun clearDimension(dimension: String) {
        snapshots = snapshots - dimension
        pending.remove(dimension)
        synchronized(this) { dirty.remove(dimension) }
    }

    fun clear() {
        snapshots = emptyMap()
        pending.clear()
        synchronized(this) { dirty.clear() }
    }

    private fun merge(a: IntRange?, b: IntRange?): IntRange? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a.first, b.first)..maxOf(a.last, b.last)
    }
}
