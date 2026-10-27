package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import org.joml.Quaterniond
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WeldRotationTest {
    @Test
    fun allRotationsPreserveHandednessAndExactBlockOffsets() {
        assertEquals(24, WeldRotation.ALL.toSet().size)
        val p = BlockPos(4, -7, 12)
        for (rotation in WeldRotation.ALL) {
            val q = rotation.quaternion()
            val actual = rotation.transform(p.x, p.y, p.z)
            val expected = q.transform(Vector3d(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()))
            assertEquals(0.0, expected.distance(Vector3d(actual.x.toDouble(), actual.y.toDouble(), actual.z.toDouble())), 1e-10)
            assertEquals(209, actual.x * actual.x + actual.y * actual.y + actual.z * actual.z)
        }
    }

    @Test
    fun allFacePairsMeetWithoutRoundingOrOverlappingSelectedBlocks() {
        val source = BlockPos(28672010, 130, -28672030)
        val target = BlockPos(28676010, 70, -28676030)
        for (from in Direction.values()) for (to in Direction.values()) {
            val rotations = WeldRotation.candidates(from, to, Quaterniond())
            assertEquals(4, rotations.size)
            for (rotation in rotations) {
                assertEquals(to.opposite, rotation.transform(from))
                assertEquals(target.relative(to), rotation.destination(source, source, target, to))
                val sourceFaceCenter = Vector3d(from.stepX * 0.5, from.stepY * 0.5, from.stepZ * 0.5)
                val faceInTarget = rotation.quaternion().transform(sourceFaceCenter)
                    .add(to.stepX.toDouble(), to.stepY.toDouble(), to.stepZ.toDouble())
                assertEquals(0.0, Vector3d(to.stepX * 0.5, to.stepY * 0.5, to.stepZ * 0.5).distance(faceInTarget), 1e-10)
            }
        }
    }

    @Test
    fun closestExistingTwistIsPreferred() {
        for (rotation in WeldRotation.ALL) {
            val targetFace = rotation.transform(Direction.NORTH).opposite
            assertEquals(rotation, WeldRotation.candidates(Direction.NORTH, targetFace, rotation.quaternion()).first())
        }
    }
}
