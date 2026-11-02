package org.valkyrienskies.mod.common.assembly

import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WeldPullControllerTest {
    @Test fun distantBodiesApproachGraduallyAndSettleAcrossPhysicsTickRates() {
        for (delta in listOf(1.0 / 20.0, 1.0 / 60.0, 1.0 / 120.0)) {
            val position = Vector3d(-10.0, -4.0, 2.0)
            val velocity = Vector3d()
            val goal = WeldPullController.Goal(Vector3d(), Quaterniond(), Vector3d(), Vector3d())
            repeat((12 / delta).toInt()) { step ->
                val command = WeldPullController.command(WeldPullController.Pose(position, Quaterniond(), velocity, Vector3d()), goal, 1.0)
                assertTrue(command.acceleration.length() <= 10.0 + 1e-10)
                velocity.add(Vector3d(command.acceleration).mul(delta))
                position.add(Vector3d(velocity).mul(delta))
                assertTrue(velocity.length() <= 3.01)
                if (step == 0) assertTrue(position.distance(Vector3d(-10.0, -4.0, 2.0)) < 0.03)
            }
            assertTrue(WeldPullController.command(WeldPullController.Pose(position, Quaterniond(), velocity, Vector3d()), goal, 1.0).aligned(0.02))
        }
    }

    @Test fun oppositeQuaternionSignsProduceTheSameShortestRotation() {
        val source = WeldPullController.Pose(Vector3d(), Quaterniond(), Vector3d(), Vector3d())
        val rotation = Quaterniond().rotateY(Math.PI - 0.01)
        val a = WeldPullController.command(source, WeldPullController.Goal(Vector3d(), rotation, Vector3d(), Vector3d()), 2.0)
        val b = WeldPullController.command(source, WeldPullController.Goal(Vector3d(), Quaterniond(rotation).mul(-1.0), Vector3d(), Vector3d()), 2.0)
        assertEquals(0.0, a.angularAcceleration.distance(b.angularAcceleration), 1e-10)
        assertTrue(a.angularAcceleration.y > 0.0)
    }

    @Test fun longBeamCannotMergeWithOnlyItsCenterAligned() {
        val source = WeldPullController.Pose(Vector3d(), Quaterniond().rotateY(0.01), Vector3d(), Vector3d())
        val goal = WeldPullController.Goal(Vector3d(), Quaterniond(), Vector3d(), Vector3d())
        val result = WeldPullController.command(source, goal, 30.0)
        assertFalse(result.aligned(0.02))
        assertTrue(result.displacement > 0.29)
        assertTrue(result.angularAcceleration.length() <= 10.0 / 30.0 + 1e-10)
    }

    @Test fun goalFollowsTheSelectedPointOnARotatingAndScaledTarget() {
        val target = WeldPullController.Pose(Vector3d(10.0, 70.0, -8.0), Quaterniond().rotateY(0.7), Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 0.5, 0.0))
        val modelCenter = Vector3d(28672000.5, 128.5, -28672000.5)
        val offset = Vector3d(3.0, 0.0, -2.0)
        val transform = Matrix4d().translation(target.position).rotate(target.rotation).scale(2.0).translate(Vector3d(modelCenter).negate())
        val relative = Quaterniond().rotateX(Math.PI / 2)
        val result = WeldPullController.goal(target, transform, Vector3d(modelCenter).add(offset), relative)
        val worldOffset = target.rotation.transform(Vector3d(offset).mul(2.0))
        assertEquals(0.0, result.position.distance(Vector3d(target.position).add(worldOffset)), 1e-7)
        assertEquals(0.0, result.velocity.distance(Vector3d(target.velocity).add(Vector3d(target.omega).cross(worldOffset))), 1e-7)
        assertEquals(0.0, result.rotation.angle() - Quaterniond(target.rotation).mul(relative).angle(), 1e-10)
    }
}
