package org.valkyrienskies.mod.common.networking

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.game.VecDeltaCodec
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.EntityDraggingInformation
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider

class ClientShipMovementTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val info = EntityDraggingInformation()
    private val level = mockk<ClientLevel>()
    private val world = mockk<VsiClientShipWorld>(relaxed = true)
    private val entity = mockk<Entity>(relaxed = true, moreInterfaces = arrayOf(IEntityDraggingInformationProvider::class))
    private val ship = mockk<ClientShip>(relaxed = true)
    private val codec = VecDeltaCodec()
    private val packet = PacketEntityShipMotion(1, 2L, 10.0, 4.0, 5.0, 0.1, 0.2, 0.3,
        0.6, 35.0, false, 100.0, 4.0, 5.0, 0.7, 12)

    @BeforeEach
    fun setup() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { level.shipObjectWorld } returns world
        every { level.getEntity(1) } returns entity
        every { world.allShips.getById(2L) } returns ship
        every { ship.id } returns 2L
        every { ship.shipToWorld } returns Matrix4d().translation(80.0, 0.0, 0.0)
        every { (entity as IEntityDraggingInformationProvider).draggingInformation } returns info
        every { entity.isControlledByLocalInstance } returns false
        every { entity.syncPacketPositionCodec(any(), any(), any()) } answers {
            codec.setBase(Vec3(firstArg(), secondArg(), thirdArg()))
        }
    }

    @AfterEach fun cleanup() = unmockkAll()

    @Test
    fun `attachment uses server codec baseline and keeps pitch in degrees`() {
        assertTrue(ClientShipMovement.apply(level, packet))
        assertEquals(Vec3(100.0, 4.0, 5.0), codec.decode(0, 0, 0))
        assertEquals(35.0, info.lerpPitchOnShip)
        assertEquals(12, info.ticksSinceStoodOnShip)
        assertTrue(info.usesShipInterpolation)
        verify { entity.setOnGround(false) }
        verify(exactly = 0) { entity.lerpTo(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { ship.renderTransform }
    }

    @Test
    fun `world handoff clears old frame and decodes following vanilla delta correctly`() {
        ClientShipMovement.apply(level, packet)
        val detached = packet.copy(shipID = -1L, worldX = 105.0, yRot = 45.0, headYaw = 60.0)
        assertTrue(ClientShipMovement.apply(level, detached))
        assertNull(info.lastShipStoodOn)
        assertNull(info.lerpPositionOnShip)
        assertFalse(info.usesShipInterpolation)
        val serverCodec = VecDeltaCodec()
        serverCodec.setBase(Vec3(105.0, 4.0, 5.0))
        val next = Vec3(105.5, 4.0, 5.0)
        assertEquals(next, codec.decode(serverCodec.encodeX(next), serverCodec.encodeY(next), serverCodec.encodeZ(next)))
        verify { entity.lerpTo(105.0, 4.0, 5.0, 45f, 35f, 3, true) }
        verify { entity.lerpHeadTo(60f, 3) }
    }

    @Test
    fun `unloaded ship cannot half apply attachment state`() {
        info.lastShipStoodOn = 9L
        every { world.allShips.getById(2L) } returns null
        assertFalse(ClientShipMovement.apply(level, packet))
        assertEquals(9L, info.lastShipStoodOn)
        verify(exactly = 0) { entity.syncPacketPositionCodec(any(), any(), any()) }
    }

    @Test
    fun `locally controlled vehicles retain local movement ownership`() {
        every { entity.isControlledByLocalInstance } returns true
        assertTrue(ClientShipMovement.apply(level, packet))
        assertNull(info.lastShipStoodOn)
        verify(exactly = 0) { entity.setDeltaMovement(any<Double>(), any<Double>(), any<Double>()) }
    }

    @Test
    fun `network receipt cannot mutate entities before the client executor runs`() {
        val minecraft = mockk<net.minecraft.client.Minecraft>()
        val connection = mockk<net.minecraft.client.multiplayer.ClientPacketListener>()
        every { minecraft.connection } returns connection
        val task = slot<Runnable>()
        every { minecraft.execute(capture(task)) } just Runs
        mockkObject(ClientShipMovement)
        every { ClientShipMovement.receive(packet) } just Runs
        ClientShipMovement.enqueue(minecraft, packet)
        verify(exactly = 0) { ClientShipMovement.receive(any()) }
        task.captured.run()
        verify(exactly = 1) { ClientShipMovement.receive(packet) }
        ClientShipMovement.enqueue(minecraft, packet)
        every { minecraft.connection } returns null
        task.captured.run()
        verify(exactly = 1) { ClientShipMovement.receive(packet) }
    }
}
