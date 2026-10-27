package org.valkyrienskies.mod.common.debug

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.fasterxml.jackson.databind.ObjectMapper
import org.valkyrienskies.core.internal.physics.VsiDebugContact
import org.valkyrienskies.core.internal.physics.VsiDebugForce
import org.valkyrienskies.core.internal.physics.VsiDebugJoint
import org.valkyrienskies.core.internal.physics.VsiDebugShip
import org.valkyrienskies.core.internal.physics.VsiDebugVector
import org.valkyrienskies.mod.common.networking.DebugShipInfo
import org.valkyrienskies.mod.common.networking.PacketPhysicsDebug

class PhysicsDebugPacketTest {
    // Use the core packet mapper without a compile dependency on the core implementation.
    private val mapper: ObjectMapper = Class.forName("org.valkyrienskies.core.impl.util.serialization.VSJacksonUtil").let {
        it.getMethod("getPacketMapper").invoke(it.getField("INSTANCE").get(null)) as ObjectMapper
    }
    private val position = VsiDebugVector(30000000.125, 20.0, -30000000.25)
    private val force = VsiDebugVector(1.0, 2.0, 3.0)

    private fun packet(): PacketPhysicsDebug {
        val ship = VsiDebugShip(42, "test", true, false, 100.0, position,
            force, force, force, force, force, force, force, force,
            listOf(VsiDebugForce(position, force)), 1, force)
        return PacketPhysicsDebug(DebugRendererMode.FULL, "test", listOf(DebugShipInfo(ship, 200, true, 20)),
            listOf(VsiDebugContact("test", 42, 0, position, force, -0.01f)),
            listOf(VsiDebugJoint(4, "test", null, 42, "FIXED", position, position, null, position)),
            listOf(position), true, 123)
    }

    @Test
    fun `packet preserves physics data and fragment metadata`() {
        for (mode in DebugRendererMode.values()) {
            val expected = packet().copy(mode = mode)
            val bytes = mapper.writeValueAsBytes(expected)
            val decoded = mapper.readValue(bytes, PacketPhysicsDebug::class.java)
            assertEquals(expected, decoded)
        }
    }

    @Test
    fun `bounded packet fits the Minecraft payload limit`() {
        val base = packet()
        val ship = base.ships.single()
        val full = base.copy(
            ships = List(64) { ship.copy(physics = ship.physics.copy(
                id = it.toLong(), forces = List(16) { VsiDebugForce(position, force) }, forceCount = 16)) },
            contacts = List(256) { base.contacts.single() },
            joints = List(256) { base.joints.single() },
        )
        assertTrue(mapper.writeValueAsBytes(full).size < 900000)
    }

    @Test
    fun `saved metadata preserves creation time and parent ID`() {
        val metadata = ShipDebugMetadata(1234, true, 20)
        val decoded = mapper.readValue(mapper.writeValueAsBytes(metadata), ShipDebugMetadata::class.java)
        assertEquals(metadata.createdGameTime, decoded.createdGameTime)
        assertEquals(metadata.creationTimeKnown, decoded.creationTimeKnown)
        assertEquals(metadata.parentShipId, decoded.parentShipId)
    }
}
