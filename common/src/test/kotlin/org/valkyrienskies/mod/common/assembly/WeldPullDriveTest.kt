package org.valkyrienskies.mod.common.assembly

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Matrix3d
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.bodies.properties.BodyKinematics
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.internal.world.VsiPhysLevel

class WeldPullDriveTest {
    private class Body(val mass: Double) {
        val position = Vector3d()
        val rotation = Quaterniond()
        val velocity = Vector3d()
        val omega = Vector3d()
        val force = Vector3d()
        val torque = Vector3d()
        var isStatic = false
        val ship = mockk<PhysShip>()
        init {
            val kinematics = mockk<BodyKinematics>()
            val transform = mockk<ShipTransform>()
            every { ship.kinematics } returns kinematics
            every { kinematics.position } answers { position }
            every { ship.transform } returns transform
            every { transform.rotation } answers { rotation }
            every { ship.velocity } answers { velocity }
            every { ship.angularVelocity } answers { omega }
            every { ship.isStatic } answers { isStatic }
            every { ship.mass } returns mass
            every { ship.momentOfInertia } returns Matrix3d().scale(mass)
            every { ship.shipToWorld } answers { Matrix4d().translation(position).rotate(rotation) }
            every { ship.applyWorldForceToBodyPos(any(), any()) } answers { force.add(firstArg<Vector3dc>()); Unit }
            every { ship.applyWorldTorque(any()) } answers { torque.add(firstArg<Vector3dc>()); Unit }
        }
        fun step(delta: Double, gravity: Vector3dc) {
            velocity.add(Vector3d(force).div(mass).add(gravity).mul(delta))
            position.add(Vector3d(velocity).mul(delta))
            omega.add(Vector3d(torque).div(mass).mul(delta))
            if (omega.lengthSquared() > 1e-20) {
                val axis = Vector3d(omega).normalize()
                rotation.premul(Quaterniond().rotationAxis(omega.length() * delta, axis.x, axis.y, axis.z)).normalize()
            }
            force.zero()
            torque.zero()
        }
    }

    private class Fixture(mass: Double = 100.0, radius: Double = 1.0) {
        val source = Body(mass)
        val target = Body(100.0)
        val gravity = Vector3d(0.0, -9.81, 0.0)
        val level = mockk<VsiPhysLevel>()
        val drive = WeldPullDrive("test", 1L, 2L, Vector3d(), Quaterniond(), radius, 0.02)
        init {
            every { level.dimension } returns "test"
            every { level.getShipById(1L) } returns target.ship
            every { level.getShipById(2L) } returns source.ship
            every { level.getGravity() } answers { gravity }
        }
        fun step(delta: Double = 1.0 / 60.0) {
            drive.tick(level, delta)
            source.step(delta, gravity)
        }
    }

    @Test fun physicalForcesPullAndRotateWithoutADistantPoseChange() {
        for (mass in listOf(1.0, 100000.0)) {
            val f = Fixture(mass)
            f.source.position.set(-8.0, -4.0, 3.0)
            f.source.rotation.rotateY(Math.PI)
            val initial = Vector3d(f.source.position)
            f.step()
            assertTrue(f.source.position.distance(initial) < 0.01)
            repeat(1000) { f.step() }
            assertTrue(f.drive.settledSeconds >= 0.25)
            assertTrue(f.source.position.length() < 0.02)
            assertTrue(f.source.rotation.angle() < 0.02)
            assertFalse(f.drive.invalid)
        }
    }

    @Test fun movingTargetIsFollowedAndRelativeMotionSettles() {
        val f = Fixture()
        f.source.position.set(-5.0, 1.0, 0.0)
        f.target.velocity.set(0.8, 0.2, -0.3)
        val delta = 1.0 / 60.0
        repeat(1000) {
            f.step(delta)
            f.target.position.add(Vector3d(f.target.velocity).mul(delta))
        }
        assertTrue(f.source.position.distance(f.target.position) < 0.02)
        assertTrue(f.source.velocity.distance(f.target.velocity) < 0.1)
        assertTrue(f.drive.settledSeconds >= 0.25)
    }

    @Test fun aRotatingTargetIsFollowedAtAnOffsetDockingPoint() {
        val f = Fixture(radius = 4.0)
        f.drive.modelCenter.set(3.0, 0.0, 0.0)
        f.source.position.set(-5.0, 2.0, 0.0)
        f.source.rotation.rotateXYZ(-0.9, 0.4, 1.0)
        f.target.rotation.rotateXYZ(0.4, -0.6, 0.2)
        f.target.omega.set(0.0, 0.25, 0.0)
        val delta = 1.0 / 60.0
        repeat(1200) {
            f.step(delta)
            f.target.rotation.premul(Quaterniond().rotationY(0.25 * delta)).normalize()
        }
        assertTrue(f.drive.settledSeconds >= 0.25)
        val goal = WeldPullController.goal(
            WeldPullController.Pose(f.target.position, f.target.rotation, f.target.velocity, f.target.omega),
            f.target.ship.shipToWorld, f.drive.modelCenter, f.drive.relativeRotation)
        assertTrue(WeldPullController.command(
            WeldPullController.Pose(f.source.position, f.source.rotation, f.source.velocity, f.source.omega), goal, 4.0).aligned(0.02))
    }

    @Test fun blockedShipNeverReportsReadyAndCancelStopsAllForces() {
        val f = Fixture()
        f.source.position.set(-2.0, 0.0, 0.0)
        repeat(300) {
            f.drive.tick(f.level, 1.0 / 60.0)
            f.source.force.zero()
            f.source.torque.zero()
        }
        assertEquals(Vector3d(-2.0, 0.0, 0.0), f.source.position)
        assertEquals(0.0, f.drive.settledSeconds)
        f.drive.active = false
        f.drive.tick(f.level, 1.0 / 60.0)
        verify(exactly = 300) { f.source.ship.applyWorldForceToBodyPos(any(), any()) }
        verify(exactly = 300) { f.source.ship.applyWorldTorque(any()) }
    }

    @Test fun missingOrStaticSourceCannotCompleteAStalePull() {
        val f = Fixture()
        repeat(30) { f.step() }
        assertTrue(f.drive.settledSeconds >= 0.25)
        f.source.isStatic = true
        f.drive.tick(f.level, 1.0 / 60.0)
        assertEquals(0.0, f.drive.settledSeconds)
        every { f.level.getShipById(2L) } returns null
        f.drive.tick(f.level, 1.0 / 60.0)
        assertEquals(0.0, f.drive.settledSeconds)
    }

    @Test fun changingGravityAndInvalidMassCannotEmitInvalidForces() {
        val f = Fixture()
        f.drive.gravityOverride = -3.0
        f.drive.tick(f.level, 1.0 / 60.0)
        assertEquals(300.0, f.source.force.y, 1e-10)
        val invalid = Fixture(Double.NaN)
        invalid.drive.tick(invalid.level, 1.0 / 60.0)
        assertTrue(invalid.drive.invalid)
        verify(exactly = 0) { invalid.source.ship.applyWorldForceToBodyPos(any(), any()) }
    }
}
