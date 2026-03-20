package org.valkyrienskies.mod.common.util

import org.joml.Vector3d
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityDraggingInformationTest {

    @Test
    fun authoritativeStateTakesPriorityOverPredictedState() {
        val info = EntityDraggingInformation()

        info.lastShipStoodOn = 10L
        info.setAuthoritativeShipStoodOn(20L)

        assertEquals(10L, info.getPredictedShipStoodOn())
        assertEquals(20L, info.authoritativeShipStoodOn)
        assertEquals(20L, info.lastShipStoodOn)
        assertTrue(info.isEntityBeingDraggedByAShip())
    }

    @Test
    fun predictedStateTimesOutWithoutAuthoritativeShip() {
        val info = EntityDraggingInformation()

        info.lastShipStoodOn = 15L
        info.ticksSinceStoodOnShip = EntityDraggingInformation.TICKS_TO_DRAG_ENTITIES - 1
        assertTrue(info.isEntityBeingDraggedByAShip())

        info.ticksSinceStoodOnShip = EntityDraggingInformation.TICKS_TO_DRAG_ENTITIES
        assertFalse(info.isEntityBeingDraggedByAShip())
    }

    @Test
    fun clearingAuthoritativeStateFallsBackToPredictionAndResetsLerp() {
        val info = EntityDraggingInformation()

        info.lastShipStoodOn = 30L
        info.setAuthoritativeShipStoodOn(40L)
        info.relativePositionOnShip = Vector3d(1.0, 2.0, 3.0)
        info.relativeVelocityOnShip = Vector3d(4.0, 5.0, 6.0)
        info.lerpPositionOnShip = Vector3d(7.0, 8.0, 9.0)
        info.lerpYawOnShip = 1.2
        info.lerpHeadYawOnShip = 2.3
        info.lerpPitchOnShip = 3.4
        info.lerpSteps = 3
        info.headLerpSteps = 2

        info.clearAuthoritativeClientState()

        assertNull(info.authoritativeShipStoodOn)
        assertEquals(30L, info.lastShipStoodOn)
        assertNull(info.relativePositionOnShip)
        assertNull(info.relativeVelocityOnShip)
        assertNull(info.lerpPositionOnShip)
        assertNull(info.lerpYawOnShip)
        assertNull(info.lerpHeadYawOnShip)
        assertNull(info.lerpPitchOnShip)
        assertEquals(0, info.lerpSteps)
        assertEquals(0, info.headLerpSteps)
    }

    @Test
    fun clearingPredictedStateResetsImpulseAndMovementCache() {
        val info = EntityDraggingInformation()

        info.lastShipStoodOn = 99L
        info.addedMovementLastTick = Vector3d(1.0, 0.5, -0.25)
        info.addedYawRotLastTick = 12.0
        info.changedShipLastTick = true
        info.shouldImpulseMovement = true

        info.clearPredictedShipState()

        assertNull(info.getPredictedShipStoodOn())
        assertNull(info.lastShipStoodOn)
        assertEquals(0.0, info.addedMovementLastTick.length(), 1e-9)
        assertEquals(0.0, info.addedYawRotLastTick, 1e-9)
        assertFalse(info.changedShipLastTick)
        assertFalse(info.shouldImpulseMovement)
    }

    @Test
    fun authoritativeLerpStartReusesCurrentRelativeStateOnSameShip() {
        val info = EntityDraggingInformation()

        info.setAuthoritativeShipStoodOn(7L)
        info.relativePositionOnShip = Vector3d(1.0, 2.0, 3.0)
        info.relativeYawOnShip = 0.25
        info.relativeHeadYawOnShip = 0.5
        info.relativePitchOnShip = 0.75

        val startPosition = info.authoritativeRelativePositionForLerp(7L, Vector3d(10.0, 20.0, 30.0))

        assertEquals(1.0, startPosition.x(), 1e-9)
        assertEquals(2.0, startPosition.y(), 1e-9)
        assertEquals(3.0, startPosition.z(), 1e-9)
        assertEquals(0.25, info.authoritativeYawForLerp(7L, 1.25), 1e-9)
        assertEquals(0.5, info.authoritativeHeadYawForLerp(7L, 1.5), 1e-9)
        assertEquals(0.75, info.authoritativePitchForLerp(7L, 1.75), 1e-9)
    }

    @Test
    fun authoritativeLerpStartSnapsToPacketStateWhenShipChanges() {
        val info = EntityDraggingInformation()

        info.setAuthoritativeShipStoodOn(7L)
        info.relativePositionOnShip = Vector3d(1.0, 2.0, 3.0)
        info.relativeYawOnShip = 0.25
        info.relativeHeadYawOnShip = 0.5
        info.relativePitchOnShip = 0.75

        val startPosition = info.authoritativeRelativePositionForLerp(8L, Vector3d(10.0, 20.0, 30.0))

        assertEquals(10.0, startPosition.x(), 1e-9)
        assertEquals(20.0, startPosition.y(), 1e-9)
        assertEquals(30.0, startPosition.z(), 1e-9)
        assertEquals(1.25, info.authoritativeYawForLerp(8L, 1.25), 1e-9)
        assertEquals(1.5, info.authoritativeHeadYawForLerp(8L, 1.5), 1e-9)
        assertEquals(1.75, info.authoritativePitchForLerp(8L, 1.75), 1e-9)
    }

    @Test
    fun interpolatedRelativePositionBlendsPreviousAndCurrent() {
        val info = EntityDraggingInformation()

        info.relativePositionOnShip = Vector3d(1.0, 2.0, 3.0)
        info.relativePositionOnShip = Vector3d(5.0, 6.0, 7.0)

        val interpolated = info.interpolatedRelativeEntityPosition(0.25f)

        assertEquals(2.0, interpolated!!.x(), 1e-9)
        assertEquals(3.0, interpolated.y(), 1e-9)
        assertEquals(4.0, interpolated.z(), 1e-9)
    }

    @Test
    fun snapRelativeRenderPositionPreventsCrossShipInterpolation() {
        val info = EntityDraggingInformation()

        info.relativePositionOnShip = Vector3d(1.0, 2.0, 3.0)
        info.relativePositionOnShip = Vector3d(10.0, 20.0, 30.0)
        info.snapRelativeRenderPosition()

        val interpolated = info.interpolatedRelativeEntityPosition(0.25f)

        assertEquals(10.0, interpolated!!.x(), 1e-9)
        assertEquals(20.0, interpolated.y(), 1e-9)
        assertEquals(30.0, interpolated.z(), 1e-9)
    }
}
