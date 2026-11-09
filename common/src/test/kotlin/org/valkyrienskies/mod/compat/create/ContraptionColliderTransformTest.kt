package org.valkyrienskies.mod.compat.create

import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3i
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContraptionColliderTransformTest {
    @Test
    fun voxelCentersMatchCreateForRotatedAndScaledParents() {
        val anchor = Vector3d(12.0, -3.0, 5.0)
        val center = Vector3d(0.5)
        for (axis in listOf(Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 1.0))) {
            for (angle in listOf(0.0, Math.PI / 2, Math.PI, 0.37)) {
                for (scale in listOf(0.5, 1.0, 2.0)) {
                    val rotation = Quaterniond().rotationAxis(angle, axis.x, axis.y, axis.z)
                    val parentRotation = Quaterniond().rotateXYZ(0.2, -0.4, 0.7)
                    for (index in listOf(Vector3d(), Vector3d(4.0, -2.0, 3.0))) {
                        // Create: anchor + c + R(localCorner + c - c).
                        val expected = rotation.transform(Vector3d(index)).add(center).add(anchor)
                        // Native: pivot + R(index + managerOffset + childOffset).
                        val actual = rotation.transform(Vector3d(index).add(center).add(ContraptionColliderTransform.voxelOffset()))
                            .add(ContraptionColliderTransform.pivot(anchor))
                        parentRotation.transform(expected.mul(scale))
                        parentRotation.transform(actual.mul(scale))
                        assertEquals(0.0, expected.distance(actual), 1e-10)
                    }
                }
            }
        }
    }

    @Test
    fun debugBoxUsesFullVoxelBounds() {
        assertEquals(Vector3d(5.0, 3.0, 7.0), ContraptionColliderTransform.boxLengths(Vector3i(-2, 0, -3), Vector3i(2, 2, 3)))
    }
}
