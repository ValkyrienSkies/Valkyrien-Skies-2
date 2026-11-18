package org.valkyrienskies.mod.common.networking

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PendingEntityUpdatesTest {
    @Test
    fun `missing entity or ship retains only the newest update`() {
        val pending = PendingEntityUpdates<String>()
        val applied = mutableListOf<String>()
        pending.put(1, "ship A")
        pending.tick { false }
        pending.put(1, "world")
        pending.tick { applied.add(it); true }
        pending.tick { fail<Boolean>("Already delivered") }
        assertEquals(listOf("world"), applied)
    }

    @Test
    fun `changing levels discards old entity ids`() {
        val pending = PendingEntityUpdates<String>()
        pending.useContext(Any())
        pending.put(1, "old level")
        pending.useContext(Any())
        pending.tick { fail<Boolean>("Previous level") }
    }

    @Test
    fun `same level preserves updates and expired updates are removed`() {
        val level = Any()
        val pending = PendingEntityUpdates<String>(maxAge = 2)
        pending.useContext(level)
        pending.put(1, "update")
        pending.useContext(level)
        var attempts = 0
        repeat(4) { pending.tick { attempts++; false } }
        assertEquals(2, attempts)
    }

    @Test
    fun `loading queue is bounded and replacing an entry preserves newer entities`() {
        val pending = PendingEntityUpdates<String>(capacity = 2)
        pending.put(1, "old")
        pending.put(2, "second")
        pending.put(1, "new")
        pending.put(3, "third")
        val applied = mutableListOf<String>()
        pending.tick { applied.add(it); true }
        assertEquals(listOf("new", "third"), applied)
    }
}
