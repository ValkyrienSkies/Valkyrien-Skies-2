package org.valkyrienskies.mod.common.networking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClientPlayerShipMotionControllerTest {

    @Test
    fun emitsUpdateWhileMountedToShipEntity() {
        val controller = ClientPlayerShipMotionController()

        val packet = controller.nextPacket(PlayerShipMotionSnapshot(42L, 1.0, 2.0, 3.0, 0.5))

        assertNotNull(packet)
        assertEquals(42L, packet!!.shipID)
        assertEquals(1.0, packet.x, 1e-9)
        assertEquals(2.0, packet.y, 1e-9)
        assertEquals(3.0, packet.z, 1e-9)
        assertEquals(0.5, packet.yRot, 1e-9)
    }

    @Test
    fun emitsUpdateWhileDraggedByShip() {
        val controller = ClientPlayerShipMotionController()

        val packet = controller.nextPacket(PlayerShipMotionSnapshot(7L, -4.0, 5.0, 6.0, -0.25))

        assertNotNull(packet)
        assertEquals(7L, packet!!.shipID)
        assertEquals(-4.0, packet.x, 1e-9)
        assertEquals(5.0, packet.y, 1e-9)
        assertEquals(6.0, packet.z, 1e-9)
        assertEquals(-0.25, packet.yRot, 1e-9)
    }

    @Test
    fun emitsSingleClearWhenShipMotionStops() {
        val controller = ClientPlayerShipMotionController()

        controller.nextPacket(PlayerShipMotionSnapshot(9L, 1.0, 1.0, 1.0, 0.0))
        val clearPacket = controller.nextPacket(null)
        val duplicateClear = controller.nextPacket(null)

        assertNotNull(clearPacket)
        assertEquals(-1L, clearPacket!!.shipID)
        assertEquals(0.0, clearPacket.x, 1e-9)
        assertEquals(0.0, clearPacket.y, 1e-9)
        assertEquals(0.0, clearPacket.z, 1e-9)
        assertEquals(0.0, clearPacket.yRot, 1e-9)
        assertNull(duplicateClear)
    }

    @Test
    fun keepsSendingUpdatesAcrossMultipleShipMotionTicks() {
        val controller = ClientPlayerShipMotionController()

        val firstPacket = controller.nextPacket(PlayerShipMotionSnapshot(12L, 1.0, 2.0, 3.0, 0.1))
        val secondPacket = controller.nextPacket(PlayerShipMotionSnapshot(12L, 4.0, 5.0, 6.0, 0.2))

        assertNotNull(firstPacket)
        assertNotNull(secondPacket)
        assertEquals(12L, secondPacket!!.shipID)
        assertEquals(4.0, secondPacket.x, 1e-9)
        assertEquals(5.0, secondPacket.y, 1e-9)
        assertEquals(6.0, secondPacket.z, 1e-9)
        assertEquals(0.2, secondPacket.yRot, 1e-9)
    }
}
