package org.valkyrienskies.mod.common.util

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EntityDraggingInformationTest {
    @Test
    fun `repeated contacts preserve a pending boarding impulse`() {
        val info = EntityDraggingInformation()
        info.lastShipStoodOn = 1L
        info.lastShipStoodOn = 1L
        assertTrue(info.shouldImpulseMovement)
        assertTrue(info.changedShipLastTick)
        info.finishTick()
        assertFalse(info.shouldImpulseMovement)
        assertFalse(info.changedShipLastTick)
    }

    @Test
    fun `changing ships clears every relative frame`() {
        val info = EntityDraggingInformation()
        info.lastShipStoodOn = 1L
        info.relativePositionOnShip = Vector3d(10_000_000.0, 64.0, 0.0)
        info.serverRelativePlayerPosition = info.relativePositionOnShip
        info.lerpPositionOnShip = info.relativePositionOnShip
        info.relativeYawOnShip = 1.0
        info.relativeHeadYawOnShip = 1.0
        info.serverRelativePlayerYaw = 1.0
        info.lerpSteps = 3
        info.headLerpSteps = 3
        info.lastShipStoodOn = 2L
        assertNull(info.bestRelativeEntityPosition())
        assertNull(info.lerpPositionOnShip)
        assertNull(info.relativeYawOnShip)
        assertNull(info.relativeHeadYawOnShip)
        assertNull(info.serverRelativePlayerYaw)
        assertEquals(0, info.lerpSteps)
        assertEquals(0, info.headLerpSteps)
    }

    @Test
    fun `detaching stops ship interpolation`() {
        val info = EntityDraggingInformation()
        info.lastShipStoodOn = 1L
        info.usesShipInterpolation = true
        info.lastShipStoodOn = null
        assertFalse(info.usesShipInterpolation)
        assertFalse(info.isEntityBeingDraggedByAShip())
        assertFalse(info.shouldImpulseMovement)
    }

    @Test
    fun `network updates retain contact age and never add a boarding impulse`() {
        val info = EntityDraggingInformation()
        info.applyNetworkShip(1L, 24)
        assertTrue(info.isEntityBeingDraggedByAShip())
        assertFalse(info.shouldImpulseMovement)
        info.finishTick()
        assertFalse(info.isEntityBeingDraggedByAShip())
        info.applyNetworkShip(1L, 25)
        assertFalse(info.isEntityBeingDraggedByAShip())
    }

    @Test
    fun `same frame update keeps interpolation targets`() {
        val info = EntityDraggingInformation()
        info.applyNetworkShip(1L, 0)
        val target = Vector3d(10.0, 20.0, 30.0)
        info.lerpPositionOnShip = target
        info.applyNetworkShip(1L, 1)
        assertSame(target, info.lerpPositionOnShip)
    }
}
