package org.valkyrienskies.mod.common.fluid

import com.fasterxml.jackson.databind.ObjectMapper
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.networking.FluidParcelState
import org.valkyrienskies.mod.common.networking.FluidParcelUpdate
import org.valkyrienskies.mod.common.networking.PacketFluidParcel
import org.valkyrienskies.mod.common.networking.PacketFluidParcelBatch
import java.util.UUID

class FluidParcelVisualsTest {
    private val id = UUID(0, 1)
    private fun state(x: Double = 0.0, volume: Double = 0.125) =
        FluidParcelState("minecraft:water", x, 20.0, -30000000.25, 10.0, -5.0, 0.0, volume)
    private fun packet(revision: Long, state: FluidParcelState? = state()) = PacketFluidParcel(id, "minecraft:overworld", revision, state)

    @Test
    fun `mesh encloses actual volume under motion stretching and texture subdivision`() {
        for (amount in listOf(1e-300, 1e-15, 1.0 / 255, 0.125, 0.5, 1.0, 8.0, 64.0)) {
            for (velocity in listOf(Vector3d(), Vector3d(0.0, -64.0, 0.0), Vector3d(13.0, 2.0, -24.0))) {
                val faces = FluidParcelGeometry.faces(amount, velocity)
                fun volume(polygons: List<List<Vector3d>>) = polygons.sumOf { face ->
                    (1 until face.size - 1).sumOf { face[0].dot(Vector3d(face[it]).cross(face[it + 1])) / 6 }
                }
                assertEquals(amount, volume(faces), amount * 1e-12)
                val tiles = faces.flatMap(FluidParcelGeometry::tiles)
                assertEquals(amount, volume(tiles.map { it.points() }), amount * 1e-12)
                for (tile in tiles) for (point in tile.points()) {
                    assertEquals(1.0, tile.normal().length(), 1e-12)
                    assertTrue((point[tile.u()] - tile.originU()) / tile.size() in -1e-12..1.000000000001)
                    assertTrue((point[tile.v()] - tile.originV()) / tile.size() in -1e-12..1.000000000001)
                    if (amount <= 8) assertEquals(1.0, tile.size())
                }
            }
        }
    }

    @Test
    fun `updates interpolate movement but apply changed volume immediately and never extrapolate`() {
        val cache = FluidParcelVisuals()
        cache.accept(packet(1), 0)
        cache.accept(packet(2, state(10.0, 0.01)), 100_000_000)
        val middle = cache.samples(150_000_000).single()
        assertEquals(5.0, middle.position.x, 1e-12)
        assertEquals(0.01, middle.state.volume)
        assertEquals(10.0, cache.samples(1_000_000_000).single().position.x)
        cache.accept(packet(1, state(99.0)), 1_000_000_000)
        assertEquals(10.0, cache.samples(1_000_000_000).single().position.x)
    }

    @Test
    fun `removal stale updates expiration and clear cannot leave ghost parcels`() {
        val cache = FluidParcelVisuals()
        cache.accept(packet(1), 0)
        cache.accept(packet(3, null), 1)
        cache.accept(packet(2), 2)
        assertTrue(cache.samples(3).isEmpty())
        cache.accept(packet(4), 4)
        assertEquals(1, cache.samples(5).size)
        assertTrue(cache.samples(10_000_000_005).isEmpty())
        cache.accept(packet(5), 11_000_000_000)
        cache.clear()
        assertTrue(cache.samples(11_000_000_001).isEmpty())
    }

    @Test
    fun `bounded cache recycles retired slots and rejects malformed quantities`() {
        val cache = FluidParcelVisuals(1)
        for (volume in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) cache.accept(packet(1, state(volume = volume)), 0)
        assertTrue(cache.samples(0).isEmpty())
        cache.accept(packet(1), 0)
        cache.accept(packet(1).copy(id = UUID(0, 2)), 0)
        assertEquals(id, cache.samples(0).single().id)
        cache.accept(packet(2, null), 1)
        cache.accept(packet(1).copy(id = UUID(0, 2)), 2)
        assertEquals(UUID(0, 2), cache.samples(2).single().id)
    }

    @Test
    fun `paused rendering freezes interpolation and expiry time`() {
        val clock = FluidParcelClock()
        val cache = FluidParcelVisuals()
        cache.accept(packet(1), clock.sample(0, false))
        cache.accept(packet(2, state(10.0)), clock.sample(100_000_000, false))
        val paused = clock.sample(50_000_000_000, true)
        assertEquals(100_000_000L, paused)
        assertEquals(0.0, cache.samples(paused).single().position.x)
        assertEquals(5.0, cache.samples(clock.sample(50_050_000_000, false)).single().position.x, 1e-12)
        clock.reset()
        assertEquals(0L, clock.sample(60_000_000_000, false))
    }

    @Test
    fun `packet codec preserves exact double quantity positions identity and removals`() {
        val mapper = Class.forName("org.valkyrienskies.core.impl.util.serialization.VSJacksonUtil").let {
            it.getMethod("getPacketMapper").invoke(it.getField("INSTANCE").get(null)) as ObjectMapper
        }
        for (packet in listOf(packet(42, state(30000000.125, 1e-15)), packet(43, null))) {
            val bytes = mapper.writeValueAsBytes(packet)
            assertEquals(packet, mapper.readValue(bytes, PacketFluidParcel::class.java))
            assertTrue(bytes.size < 2048)
        }
    }

    @Test
    fun `one parcel batch preserves exact mixed fluid updates and removals`() {
        val mapper = Class.forName("org.valkyrienskies.core.impl.util.serialization.VSJacksonUtil").let {
            it.getMethod("getPacketMapper").invoke(it.getField("INSTANCE").get(null)) as ObjectMapper
        }
        val expected = listOf(
            packet(42, state(30000000.125, 1e-300)),
            packet(43, null),
            packet(1, state().copy(fluid = "minecraft:lava")).copy(id = UUID(0, 2)),
        )
        val batch = PacketFluidParcelBatch(expected.first().dimension,
            expected.map { FluidParcelUpdate(it.id, it.revision, it.state) })
        assertEquals(expected, mapper.readValue(mapper.writeValueAsBytes(batch), PacketFluidParcelBatch::class.java).packets())
        assertThrows(IllegalArgumentException::class.java) {
            batch.copy(updates = List(PacketFluidParcelBatch.MAX_UPDATES + 1) { batch.updates.first() })
        }
    }
}
