package org.valkyrienskies.mod.common.portals

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.mod.api.portals.ShipPortalMouth
import org.valkyrienskies.mod.api.portals.ShipPortalPair

@OptIn(VsBeta::class, PhysTickOnly::class)
class ShipPortalUpdatesTest {
    private val queue = ShipPortalUpdates()
    private val key = ResourceLocation("clockwork", "owner/gate")
    private val up = Vec3(0.0, 1.0, 0.0)
    private val a = ShipPortalMouth(Vec3.ZERO, Vec3(1.0, 0.0, 0.0), up)
    private val b = ShipPortalMouth(Vec3(100.0, 0.0, 0.0), Vec3(0.0, 0.0, -1.0), up)
    private val pair = ShipPortalPair(a, b, 1.5, 2.0)

    @Test fun `independent providers and dimensions preserve keys and oriented world frames`() {
        val ip = ResourceLocation("immersive_portals", key.path)
        queue.set("overworld", key, pair)
        queue.set("overworld", ip, pair)
        queue.set("end", key, pair.copy(halfWidth = 2.0))
        val keys = mutableSetOf<String>()
        queue.apply("overworld") { id, definition, _, _ ->
            assertTrue(keys.add(id))
            val d = requireNotNull(definition)
            assertEquals(100.0, d.b.position.x())
            assertTrue(d.b.rotation.transform(Vector3d(0.0, 0.0, 1.0)).equals(Vector3d(1.0, 0.0, 0.0), 1e-10))
            assertEquals(1.5, d.halfWidth)
            true
        }
        assertEquals(setOf("clockwork/owner/gate", "immersive_portals/owner/gate"), keys)
        var visits = 0
        queue.apply("end") { _, d, _, _ -> visits++; assertEquals(2.0, d!!.halfWidth); true }
        assertEquals(1, visits)
        queue.set("overworld", key, pair.copy())
        queue.apply("overworld") { _, _, _, _ -> error("Repeated observation should be idle") }
    }

    @Test fun `busy changes retry and a newer submission survives an in-flight acknowledgement`() {
        val observed = mutableListOf<Double?>()
        queue.set("level", key, pair)
        queue.apply("level") { _, d, _, _ -> observed.add(d?.halfWidth); false }
        queue.apply("level") { _, d, _, _ -> observed.add(d?.halfWidth); queue.remove("level", key); true }
        queue.apply("level") { _, d, _, _ -> observed.add(d?.halfWidth); false }
        queue.apply("level") { _, d, _, _ -> observed.add(d?.halfWidth); queue.set("level", key, pair.copy(halfWidth = 3.0)); true }
        queue.apply("level") { _, d, _, _ -> observed.add(d?.halfWidth); true }
        queue.apply("level") { _, _, _, _ -> error("Already accepted") }
        assertEquals(listOf(1.5, 1.5, null, null, 3.0), observed)
    }

    @Test fun `server restart forgets pending and accepted observations and permits removal before rediscovery`() {
        queue.set("level", key, pair)
        queue.clear()
        queue.apply("level") { _, _, _, _ -> error("Old session") }
        queue.remove("level", key)
        val observed = mutableListOf<Boolean>()
        queue.apply("level") { _, d, _, _ -> observed.add(d == null); true }
        queue.set("level", key, pair)
        queue.apply("level") { _, d, _, _ -> observed.add(d == null); true }
        assertEquals(listOf(true, false), observed)
    }

    @Test fun `mount identity participates in coalescing and stays on its own side`() {
        val ids = mutableListOf<Pair<Long?, Long?>>()
        fun drain() = queue.apply("level") { _, d, first, second ->
            assertEquals(100.0, d!!.b.position.x())
            ids.add(first to second)
            true
        }
        queue.set("level", key, pair, null, 20L)
        drain()
        queue.set("level", key, pair.copy(), null, 20L)
        drain()
        queue.set("level", key, pair, 20L, 30L)
        drain()
        queue.set("level", key, pair)
        drain()
        assertEquals(listOf(null to 20L, 20L to 30L, null to null), ids)
    }

    @Test fun `invalid aperture frames fail at submission`() {
        assertThrows(IllegalArgumentException::class.java) { a.copy(right = up) }
        assertThrows(IllegalArgumentException::class.java) { a.copy(center = Vec3(Double.NaN, 0.0, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { pair.copy(halfWidth = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { pair.copy(halfHeight = Double.POSITIVE_INFINITY) }
    }
}
