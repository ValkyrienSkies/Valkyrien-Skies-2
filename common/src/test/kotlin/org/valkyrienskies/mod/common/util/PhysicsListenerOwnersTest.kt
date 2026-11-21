package org.valkyrienskies.mod.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class PhysicsListenerOwnersTest {
    @Test
    fun `registration before assembly gains its owner without registering again`() {
        val listener = Any()
        val listeners = ConcurrentHashMap<String, Pair<Long?, Any>>()
        listeners["block"] = Pair(null, listener)
        var owner: Long? = null
        refreshPhysicsListenerOwners(listeners) { owner }
        assertNull(listeners.getValue("block").first)

        owner = 27L
        refreshPhysicsListenerOwners(listeners) { owner }
        assertEquals(27L, listeners.getValue("block").first)
        assertSame(listener, listeners.getValue("block").second)
    }

    @Test
    fun `changed and removed owners replace stale IDs`() {
        val listener = Any()
        val listeners = ConcurrentHashMap<String, Pair<Long?, Any>>()
        listeners["block"] = Pair(27L, listener)
        refreshPhysicsListenerOwners(listeners) { 42L }
        assertEquals(42L, listeners.getValue("block").first)
        refreshPhysicsListenerOwners(listeners) { null }
        assertNull(listeners.getValue("block").first)
        assertSame(listener, listeners.getValue("block").second)
    }

    @Test
    fun `unchanged owners retain their existing snapshots`() {
        val listeners = ConcurrentHashMap<String, Pair<Long?, Any>>()
        val registration = Pair(27L, Any())
        val groundRegistration = Pair(null, Any())
        listeners["ship"] = registration
        listeners["ground"] = groundRegistration
        repeat(3) { refreshPhysicsListenerOwners(listeners) { if (it == "ship") 27L else null } }
        assertSame(registration, listeners["ship"])
        assertSame(groundRegistration, listeners["ground"])
    }

    @Test
    fun `refresh does not resurrect a registration removed during resolution`() {
        val listeners = ConcurrentHashMap<String, Pair<Long?, Any>>()
        listeners["block"] = Pair(null, Any())
        refreshPhysicsListenerOwners(listeners) {
            listeners.remove(it)
            27L
        }
        assertFalse(listeners.containsKey("block"))
    }

    @Test
    fun `refresh does not overwrite a replacement listener`() {
        val listeners = ConcurrentHashMap<String, Pair<Long?, Any>>()
        val replacement = Pair(42L, Any())
        listeners["block"] = Pair(null, Any())
        refreshPhysicsListenerOwners(listeners) {
            listeners[it] = replacement
            27L
        }
        assertSame(replacement, listeners["block"])
    }

    @Test
    fun `refresh preserves equal but distinct replacement listeners`() {
        data class Listener(val kind: String)
        val listeners = ConcurrentHashMap<String, Pair<Long?, Listener>>()
        listeners["block"] = Pair(null, Listener("thruster"))
        val replacement = Pair(null, Listener("thruster"))
        refreshPhysicsListenerOwners(listeners) {
            listeners[it] = replacement
            27L
        }
        assertSame(replacement, listeners["block"])
    }

    @Test
    fun `each registration gets the owner of its own position`() {
        val listeners = ConcurrentHashMap<String, Pair<Long?, Any>>()
        listeners["a"] = Pair(null, Any())
        listeners["b"] = Pair(null, Any())
        refreshPhysicsListenerOwners(listeners) { if (it == "a") 27L else 42L }
        assertEquals(27L, listeners.getValue("a").first)
        assertEquals(42L, listeners.getValue("b").first)
    }
}
