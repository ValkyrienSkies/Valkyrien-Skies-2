package org.valkyrienskies.mod.common.assembly

import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeldKinematicsTest {
    @Test fun changingMassCenterPreservesEveryRetainedBlockAndItsVelocity() {
        val oldModelCenter = Vector3d(28672000.5, 128.5, -28672000.5)
        val worldCenter = Vector3d(10.5, 70.5, -15.0)
        val oldVelocity = Vector3d(2.0, -1.0, 0.5)
        val omega = Vector3d(0.7, -2.0, 0.3)
        val rotation = Quaterniond().rotateXYZ(0.6, -0.9, 1.2)
        for (scale in listOf(0.5, 1.0, 3.0)) {
            val matrix = Matrix4d().translation(worldCenter).rotate(rotation).scale(scale)
                .translate(Vector3d(oldModelCenter).negate())
            for (offset in listOf(Vector3d(1.0, 0.0, 0.0), Vector3d(-12.0, 7.0, 3.0))) {
                val modelCenter = Vector3d(oldModelCenter).add(offset)
                val rebased = WeldKinematics.rebase(matrix, worldCenter, oldVelocity, omega, modelCenter)
                val newMatrix = Matrix4d().translation(rebased.position).rotate(rotation).scale(scale)
                    .translate(Vector3d(modelCenter).negate())
                for (blockOffset in listOf(Vector3d(), Vector3d(4.0, 9.0, -7.0))) {
                    val block = Vector3d(oldModelCenter).add(blockOffset)
                    val oldPosition = matrix.transformPosition(block, Vector3d())
                    val newPosition = newMatrix.transformPosition(block, Vector3d())
                    assertEquals(0.0, oldPosition.distance(newPosition), 1e-7)
                    val oldPointVelocity = Vector3d(oldVelocity).add(Vector3d(omega).cross(Vector3d(oldPosition).sub(worldCenter)))
                    val newPointVelocity = Vector3d(rebased.velocity).add(Vector3d(omega).cross(Vector3d(newPosition).sub(rebased.position)))
                    assertEquals(0.0, oldPointVelocity.distance(newPointVelocity), 1e-6)
                }
            }
        }
    }
}
