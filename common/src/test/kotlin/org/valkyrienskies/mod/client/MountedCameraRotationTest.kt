package org.valkyrienskies.mod.client

import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class MountedCameraRotationTest {
    private val mount = Any()
    private val state = MountedCameraRotation()

    @Test
    fun `mouse yaw and pitch use the inverted ships axes`() {
        val ship = Quaterniond().rotateZ(Math.PI)
        state.update(mount, 1, ship, 0.0f, 0.0f)
        state.turn(100.0, 80.0)
        assertEquals(-15.0f, state.worldYaw, 1e-4f)
        assertEquals(-12.0f, state.worldPitch, 1e-4f)
        assertVector(view(15.0, 12.0), ship.transformInverse(worldView()))
    }

    @Test
    fun `mouse controls are unchanged in ship coordinates at arbitrary orientations`() {
        for (ship in listOf(
            Quaterniond(), Quaterniond().rotateX(Math.PI / 2), Quaterniond().rotateZ(Math.PI),
            Quaterniond().rotationXYZ(0.8, -1.2, 2.6)
        )) {
            state.clear()
            state.update(mount, 1, Quaterniond(), 25.0f, -20.0f)
            state.update(mount, 1, ship, state.worldYaw, state.worldPitch)
            state.turn(80.0, 40.0)
            assertVector(view(37.0, -14.0), ship.transformInverse(worldView()))
            val before = Quaterniond(state.rotation)
            val previousForward = state.rotation.transform(Vector3d(0.0, 0.0, 1.0))
            state.turn(10.0, 0.0)
            val movement = worldView().sub(previousForward)
            val cameraLeft = before.transform(Vector3d(1.0, 0.0, 0.0))
            assertTrue(movement.dot(cameraLeft) < 0.0)
            assertCameraMatchesAim()
        }
    }

    @Test
    fun `mount preserves aim and subsequent ship rotation carries it`() {
        val initial = Quaterniond().rotationXYZ(0.3, 0.5, 0.8)
        state.update(mount, 1, initial, 42.0f, -18.0f)
        assertVector(view(42.0, -18.0), worldView())
        val localDirection = initial.transformInverse(worldView())
        val rotated = Quaterniond().rotationXYZ(1.3, -0.6, 2.8)
        state.update(mount, 1, rotated, state.worldYaw, state.worldPitch)
        assertVector(rotated.transform(localDirection), worldView())
        assertCameraMatchesAim()
    }

    @Test
    fun `camera crosses world poles without losing its ship relative up`() {
        state.update(mount, 1, Quaterniond(), 0.0f, 0.0f)
        for (degrees in 0..360) {
            val ship = Quaterniond().rotateX(Math.toRadians(degrees.toDouble()))
            state.update(mount, 1, ship, state.worldYaw, state.worldPitch)
            assertVector(ship.transform(Vector3d(0.0, 1.0, 0.0)), state.rotation.transform(Vector3d(0.0, 1.0, 0.0)))
            assertVector(ship.transform(Vector3d(0.0, 0.0, 1.0)), worldView())
            assertCameraMatchesAim()
        }
    }

    @Test
    fun `pitch limit is relative to ship and yaw remains controllable at the pole`() {
        val ship = Quaterniond().rotationXYZ(0.4, 0.7, 1.3)
        state.update(mount, 1, Quaterniond(), 0.0f, 0.0f)
        state.update(mount, 1, ship, state.worldYaw, state.worldPitch)
        state.turn(0.0, 1000.0)
        assertVector(Vector3d(0.0, -1.0, 0.0), ship.transformInverse(worldView()))
        state.turn(200.0, -100.0)
        assertVector(view(30.0, 75.0), ship.transformInverse(worldView()))
        state.turn(0.0, -2000.0)
        assertVector(Vector3d(0.0, 1.0, 0.0), ship.transformInverse(worldView()))
    }

    @Test
    fun `tick and render transforms do not accumulate rotation or alter local aim`() {
        state.update(mount, 1, Quaterniond(), 30.0f, 20.0f)
        val tick = Quaterniond().rotationXYZ(0.4, 0.6, 1.8)
        val render = Quaterniond().rotationXYZ(0.3, 0.5, 1.7)
        repeat(100) {
            for (ship in listOf(tick, render)) {
                state.update(mount, 1, ship, state.worldYaw, state.worldPitch)
                assertVector(view(30.0, 20.0), ship.transformInverse(worldView()))
            }
        }
    }

    @Test
    fun `external rotation corrections become the new local aim`() {
        val ship = Quaterniond().rotationXYZ(0.4, 0.6, 1.8)
        state.update(mount, 1, ship, 30.0f, 20.0f)
        state.update(mount, 1, ship, -65.0f, -25.0f)
        assertVector(view(-65.0, -25.0), worldView())
        val expectedLocal = ship.transformInverse(worldView())
        state.update(mount, 1, Quaterniond(), state.worldYaw, state.worldPitch)
        assertVector(expectedLocal, worldView())
    }

    @Test
    fun `dismount and switching seats reset the local frame without changing aim`() {
        state.update(mount, 1, Quaterniond().rotateZ(Math.PI), 25.0f, 15.0f)
        state.turn(100.0, 20.0)
        val dismountDirection = worldView()
        state.clear()
        assertFalse(state.isMounted)
        assertVector(dismountDirection, worldView())
        state.update(mount, 2, Quaterniond().rotateX(0.8), state.worldYaw, state.worldPitch)
        assertVector(dismountDirection, worldView())
        state.update(Any(), 2, Quaterniond().rotateY(1.8), state.worldYaw, state.worldPitch)
        assertVector(dismountDirection, worldView())
    }

    private fun worldView() = view(state.worldYaw.toDouble(), state.worldPitch.toDouble())

    private fun assertCameraMatchesAim() {
        assertVector(worldView(), state.rotation.transform(Vector3d(0.0, 0.0, 1.0)))
    }

    private fun view(yaw: Double, pitch: Double): Vector3d {
        val y = Math.toRadians(yaw)
        val p = Math.toRadians(pitch)
        return Vector3d(-sin(y) * cos(p), -sin(p), cos(y) * cos(p))
    }

    private fun assertVector(expected: Vector3d, actual: Vector3d) {
        assertTrue(expected.distance(actual) < 1e-5, "expected $expected, actual $actual")
    }
}
