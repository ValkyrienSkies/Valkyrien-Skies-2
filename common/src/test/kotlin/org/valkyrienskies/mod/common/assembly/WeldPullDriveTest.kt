package org.valkyrienskies.mod.common.assembly

import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WeldPullDriveTest {
    private class Target(
        val position: Vector3d = Vector3d(),
        val rotation: Quaterniond = Quaterniond(),
        val velocity: Vector3d = Vector3d(),
        val omega: Vector3d = Vector3d(),
    ) {
        fun pose() = WeldPullController.Pose(position, rotation, velocity, omega)
        fun toWorld() = Matrix4d().translation(position).rotate(rotation)
    }

    private fun drive(
        startCenter: Vector3d = Vector3d(),
        startRotation: Quaterniond = Quaterniond(),
        modelCenter: Vector3d = Vector3d(),
        relativeRotation: Quaterniond = Quaterniond(),
        distance: Double = 6.0,
        radius: Double = 1.0,
    ) = WeldPullDrive(1L, 2L, startCenter, startRotation, modelCenter, relativeRotation, distance, radius)

    /** Step until the drive completes and return how many steps (0.05 s each) it took. */
    private fun stepsToComplete(drive: WeldPullDrive, target: Target = Target()): Int {
        var steps = 0
        while (!drive.complete) {
            drive.step(target.pose(), target.toWorld())
            steps++
            assertTrue(steps <= 1000, "the drive never completed")
        }
        return steps
    }

    private fun angleBetween(a: Quaterniond, b: Quaterniond) =
        2.0 * Math.acos(Math.abs(a.dot(b)).coerceIn(0.0, 1.0))

    @Test fun startsAtTheStartPoseAndEndsExactlyOnTheDockingPose() {
        val startCenter = Vector3d(-6.0, 2.0, 1.0)
        val startRotation = Quaterniond().rotateY(1.2)
        val modelCenter = Vector3d(3.0, 0.5, -2.0)
        val relativeRotation = Quaterniond().rotateXYZ(0.3, -0.4, 0.2)
        val drive = drive(startCenter, startRotation, modelCenter, relativeRotation, distance = 10.0, radius = 2.0)
        val target = Target()

        val first = drive.step(target.pose(), target.toWorld())
        assertFalse(drive.complete)
        // One 0.05 s step of a multi-second pull is only a small move away from the start.
        assertTrue(first.position.distance(startCenter) < 0.5)
        assertTrue(first.position.distance(startCenter) > 0.0)

        var last = first
        while (!drive.complete) last = drive.step(target.pose(), target.toWorld())
        assertEquals(0.0, last.position.distance(modelCenter), 1e-9)
        assertEquals(0.0, angleBetween(last.rotation, relativeRotation), 1e-6)
    }

    @Test fun durationScalesWithDistanceAtThreeBlocksPerSecond() {
        // 6 blocks at 3 blocks/s is 2 s, or 40 steps of 0.05 s.
        assertEquals(40, stepsToComplete(drive(distance = 6.0)))
        assertEquals(20, stepsToComplete(drive(distance = 3.0)))
    }

    @Test fun rotationAddsArcLengthAtTheShipRadius() {
        // A quarter turn at radius 2 adds pi blocks of travel to 3 blocks of distance.
        val rotated = drive(relativeRotation = Quaterniond().rotateY(Math.PI / 2), distance = 3.0, radius = 2.0)
        assertEquals(41, stepsToComplete(rotated))
        val unrotated = drive(distance = 3.0, radius = 2.0)
        assertEquals(20, stepsToComplete(unrotated))
    }

    @Test fun durationIsClampedForTinyAndHugePulls() {
        assertEquals(5, stepsToComplete(drive(distance = 0.0)))
        assertEquals(200, stepsToComplete(drive(distance = 500.0)))
    }

    @Test fun oppositeQuaternionSignsAreTheSameRotation() {
        // q and -q are the same orientation, so they add no rotation time.
        val start = Quaterniond().rotateY(0.7)
        val flipped = Quaterniond(start).mul(-1.0)
        assertEquals(20, stepsToComplete(drive(startRotation = start, relativeRotation = flipped, distance = 3.0)))
    }

    @Test fun halfwayThroughThePullThePoseIsHalfwayAlongThePath() {
        val startCenter = Vector3d(-4.0, 0.0, 0.0)
        val modelCenter = Vector3d(4.0, 2.0, 0.0)
        val relativeRotation = Quaterniond().rotateY(1.0)
        // 2 s duration, so 20 steps is exactly half.
        val drive = drive(startCenter, Quaterniond(), modelCenter, relativeRotation, distance = 6.0, radius = 0.0)
        val target = Target()
        var goal = drive.step(target.pose(), target.toWorld())
        repeat(19) { goal = drive.step(target.pose(), target.toWorld()) }
        assertEquals(0.0, goal.position.distance(Vector3d(0.0, 1.0, 0.0)), 1e-9)
        assertEquals(0.5, angleBetween(goal.rotation, Quaterniond()), 1e-6)
    }

    @Test fun goalFollowsAMovingRotatingTarget() {
        val modelCenter = Vector3d(3.0, 0.0, 0.0)
        val drive = drive(startCenter = Vector3d(modelCenter), modelCenter = modelCenter, distance = 0.0)
        val target = Target(
            position = Vector3d(10.0, 5.0, -2.0),
            rotation = Quaterniond().rotateY(Math.PI / 2),
            velocity = Vector3d(1.0, 0.0, 0.0),
            omega = Vector3d(0.0, 0.5, 0.0),
        )
        val goal = drive.step(target.pose(), target.toWorld())
        val expected = target.toWorld().transformPosition(Vector3d(modelCenter))
        assertEquals(0.0, goal.position.distance(expected), 1e-9)
        assertEquals(0.0, angleBetween(goal.rotation, target.rotation), 1e-9)
        // The docking point moves with the target, including the spin about the target's center.
        val expectedVelocity = Vector3d(target.velocity)
            .add(Vector3d(target.omega).cross(Vector3d(expected).sub(target.position)))
        assertEquals(0.0, goal.velocity.distance(expectedVelocity), 1e-9)
        assertEquals(0.0, goal.omega.distance(target.omega), 1e-12)
    }

    @Test fun stepsAfterCompletionStayOnTheDockingPose() {
        val modelCenter = Vector3d(1.0, 2.0, 3.0)
        val drive = drive(startCenter = Vector3d(-5.0, 0.0, 0.0), modelCenter = modelCenter, distance = 6.0)
        val target = Target()
        stepsToComplete(drive, target)
        repeat(10) {
            val goal = drive.step(target.pose(), target.toWorld())
            assertTrue(drive.complete)
            assertEquals(0.0, goal.position.distance(modelCenter), 1e-9)
        }
    }
}
