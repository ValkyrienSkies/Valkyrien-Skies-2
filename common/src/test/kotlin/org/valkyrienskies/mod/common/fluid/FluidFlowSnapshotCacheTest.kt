package org.valkyrienskies.mod.common.fluid

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FluidFlowSnapshotCacheTest {
    @Test
    fun `vertical movement rescans uncovered heights but reuses covered heights`() {
        val cache = FluidFlowSnapshotCache<IntRange>()
        val scans = ArrayList<IntRange>()
        fun tick(range: IntRange) = cache.refresh("world", mapOf(1L to range), 8) { _, heights ->
            scans.add(heights)
            heights
        }
        tick(0..3)
        tick(1..2)
        tick(2..8)
        tick(-4..2)
        assertEquals(listOf(0..3, 0..8, -4..8), scans)
        assertEquals(-4..8, cache.snapshot("world")[1L]!!.value)
    }

    @Test
    fun `waiting refresh includes new vertical demand and dirty state`() {
        val cache = FluidFlowSnapshotCache<IntRange>()
        cache.refresh("world", linkedMapOf(1L to (0..3), 2L to (4..7)), 1) { _, heights -> heights }
        cache.markDirty("world", 2L)
        cache.refresh("world", mapOf(2L to (10..15)), 1) { _, heights -> heights }
        assertEquals(4..15, cache.snapshot("world")[2L]!!.value)
        assertFalse(cache.snapshot("world").containsKey(1L))
    }

    @Test
    fun `unloaded chunk is retried without publishing an empty permanent cache`() {
        val cache = FluidFlowSnapshotCache<String>()
        val active = linkedMapOf(1L to (0..3), 2L to (0..3))
        cache.refresh("world", active, 1) { _, _ -> null }
        assertTrue(cache.snapshot("world").isEmpty())
        val scanned = ArrayList<Long>()
        repeat(2) {
            cache.refresh("world", active, 1) { chunk, _ -> scanned.add(chunk); "loaded" }
        }
        assertEquals(listOf(2L, 1L), scanned)
        assertEquals(2, cache.snapshot("world").size)
    }

    @Test
    fun `dimensions have independent refresh budgets and old snapshots stay immutable`() {
        val cache = FluidFlowSnapshotCache<Int>()
        cache.refresh("first", mapOf(1L to (0..3)), 0) { _, _ -> fail("Over budget") }
        cache.refresh("second", mapOf(2L to (0..3)), 1) { _, _ -> 1 }
        val old = cache.snapshot("second")
        cache.markDirty("second", 2L)
        cache.refresh("second", mapOf(2L to (0..3)), 1) { _, _ -> 2 }
        assertEquals(1, old[2L]!!.value)
        assertEquals(2, cache.snapshot("second")[2L]!!.value)
        assertTrue(cache.snapshot("first").isEmpty())
    }

    @Test
    fun `dirty cached chunks refresh even when their height range is unchanged`() {
        val cache = FluidFlowSnapshotCache<Int>()
        val active = mapOf(1L to (0..3))
        cache.refresh("world", active, 1) { _, _ -> 1 }
        cache.markDirty("world", 1L)
        cache.refresh("world", active, 0) { _, _ -> fail("Over budget") }
        cache.refresh("world", active, 1) { _, _ -> 2 }
        assertEquals(2, cache.snapshot("world")[1L]!!.value)
    }

    @Test
    fun `inactive and closed dimensions drop snapshots and pending work`() {
        val cache = FluidFlowSnapshotCache<Int>()
        cache.refresh("world", mapOf(1L to (0..3), 2L to (0..3)), 1) { _, _ -> 1 }
        cache.clearDimension("world")
        cache.refresh("world", mapOf(3L to (0..3)), 1) { chunk, _ ->
            assertEquals(3L, chunk)
            2
        }
        assertEquals(setOf(3L), cache.snapshot("world").keys)
        cache.clear()
        assertTrue(cache.snapshot("world").isEmpty())
        cache.refresh("world", mapOf(1L to (0..3)), 1) { _, _ -> 3 }
        cache.refresh("world", emptyMap(), 1) { _, _ -> fail("Inactive chunk") }
        assertTrue(cache.snapshot("world").isEmpty())
    }
}
