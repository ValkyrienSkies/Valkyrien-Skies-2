package org.valkyrienskies.mod.common.networking

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.entity.Entity
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.EntityDraggingInformation
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider

class ServerShipMovementTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val player = mockk<ServerPlayer>(relaxed = true, moreInterfaces = arrayOf(IEntityDraggingInformationProvider::class))
    private val connection = mockk<ServerGamePacketListenerImpl>(relaxed = true)
    private val level = mockk<ServerLevel>()
    private val world = mockk<VsiServerShipWorld>(relaxed = true)
    private val ship = mockk<ServerShip>(relaxed = true)
    private val info = EntityDraggingInformation()
    private val position = Vector3d()
    private val packet = PacketPlayerShipMotion(2L, 1.0, 4.0, 5.0, 0.0, 35f, false, 12, -1)

    @BeforeEach fun setup() {
        player.connection = connection
        connection.player = player
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { player.serverLevel() } returns level
        every { player.level() } returns level
        every { level.shipObjectWorld } returns world
        every { level.dimensionId } returns "test"
        every { ship.chunkClaimDimension } returns "test"
        every { ship.id } returns 2L
        every { ship.shipToWorld } returns Matrix4d().translation(100.0, 0.0, 0.0)
        every { ship.worldToShip } returns Matrix4d().translation(-100.0, 0.0, 0.0)
        every { ship.transform.shipToWorldRotation } returns Quaterniond().rotateY(Math.PI / 6)
        every { world.allShips.getById(2L) } returns ship
        every { connection.isAcceptingMessages } returns true
        every { player.isPassenger } returns false
        every { (player as IEntityDraggingInformationProvider).draggingInformation } returns info
        every { player.x } answers { position.x }
        every { player.y } answers { position.y }
        every { player.z } answers { position.z }
    }

    @AfterEach fun cleanup() = unmockkAll()

    @Test fun `successive updates each pass once through vanilla validation with real grounding`() {
        val received = mutableListOf<ServerboundMovePlayerPacket>()
        every { connection.handleMovePlayer(any()) } answers {
            assertTrue(info.handlingClientMovement)
            val move = firstArg<ServerboundMovePlayerPacket>()
            received += move
            position.set(move.getX(0.0), move.getY(0.0), move.getZ(0.0))
        }
        ServerShipMovement.handle(player, packet)
        ServerShipMovement.handle(player, packet.copy(x = 2.0))
        assertEquals(listOf(101.0, 102.0), received.map { it.getX(0.0) })
        assertTrue(received.none { it.isOnGround })
        assertEquals(35f, received.last().getXRot(0f))
        assertEquals(-30f, received.last().getYRot(0f), 1e-5f)
        assertEquals(2L, info.lastShipStoodOn)
        assertEquals(12, info.ticksSinceStoodOnShip)
        assertFalse(info.handlingClientMovement)
        verify(exactly = 0) { player.setPos(any<Double>(), any<Double>(), any<Double>()) }
    }

    @Test fun `rejected movement never forces position or records rejected local coordinates`() {
        ServerShipMovement.handle(player, packet)
        assertNull(info.lastShipStoodOn)
        assertNull(info.serverRelativePlayerPosition)
        assertFalse(info.handlingClientMovement)
        verify(exactly = 0) { player.setPos(any<Double>(), any<Double>(), any<Double>()) }
    }

    @Test fun `a supplied entity id cannot move another players vehicle`() {
        val vehicle = mockk<Entity>(relaxed = true)
        every { player.rootVehicle } returns vehicle
        every { vehicle.id } returns 17
        every { vehicle.controllingPassenger } returns mockk<ServerPlayer>()
        ServerShipMovement.handle(player, packet.copy(vehicleID = 17))
        verify(exactly = 0) { connection.handleMoveVehicle(any()) }
    }

    @Test fun `controlled vehicle movement is transformed and validated exactly once`() {
        val vehicle = mockk<Entity>(relaxed = true, moreInterfaces = arrayOf(IEntityDraggingInformationProvider::class))
        val vehicleInfo = EntityDraggingInformation()
        every { player.rootVehicle } returns vehicle
        every { vehicle.id } returns 17
        every { vehicle.controllingPassenger } returns player
        every { (vehicle as IEntityDraggingInformationProvider).draggingInformation } returns vehicleInfo
        every { vehicle.x } answers { position.x }
        every { vehicle.y } answers { position.y }
        every { vehicle.z } answers { position.z }
        val move = slot<ServerboundMoveVehiclePacket>()
        every { connection.handleMoveVehicle(capture(move)) } answers {
            assertTrue(vehicleInfo.handlingClientMovement)
            position.set(move.captured.x, move.captured.y, move.captured.z)
        }
        ServerShipMovement.handle(player, packet.copy(vehicleID = 17))
        assertEquals(101.0, move.captured.x)
        assertEquals(-30f, move.captured.yRot, 1e-5f)
        assertEquals(35f, move.captured.xRot)
        assertEquals(2L, vehicleInfo.lastShipStoodOn)
        assertFalse(vehicleInfo.handlingClientMovement)
        verify(exactly = 1) { connection.handleMoveVehicle(any()) }
        verify(exactly = 0) { connection.handleMovePlayer(any()) }
        verify(exactly = 0) { vehicle.setPos(any<Double>(), any<Double>(), any<Double>()) }
    }

    @Test fun `nonfinite and expired movement does not reach validation`() {
        for (invalid in listOf(packet.copy(x = Double.NaN), packet.copy(yRot = Double.POSITIVE_INFINITY),
            packet.copy(xRot = Float.NaN), packet.copy(ticksSinceContact = 25))) {
            ServerShipMovement.handle(player, invalid)
        }
        verify(exactly = 0) { connection.handleMovePlayer(any()) }
    }

    @Test fun `network receipt waits for the server executor and cannot update a respawned player`() {
        val server = mockk<net.minecraft.server.MinecraftServer>()
        ServerPlayer::class.java.getDeclaredField("server").apply { isAccessible = true }.set(player, server)
        val task = slot<Runnable>()
        every { server.execute(capture(task)) } just Runs
        ServerShipMovement.enqueue(player, packet)
        verify(exactly = 0) { connection.handleMovePlayer(any()) }
        task.captured.run()
        verify(exactly = 1) { connection.handleMovePlayer(any()) }
        ServerShipMovement.enqueue(player, packet)
        connection.player = mockk<ServerPlayer>()
        task.captured.run()
        verify(exactly = 1) { connection.handleMovePlayer(any()) }
    }
}
