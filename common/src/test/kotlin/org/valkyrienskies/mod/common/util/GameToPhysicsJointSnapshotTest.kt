package org.valkyrienskies.mod.common.util

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.joints.VSJoint
import org.valkyrienskies.core.internal.world.VsiPhysLevel

class GameToPhysicsJointSnapshotTest {
    @Test fun snapshotsOwnTheirIndexesAndConnectedShipTraversal() {
        val joint = mockk<VSJoint>()
        every { joint.shipId0 } returns 3L
        every { joint.shipId1 } returns 4L
        val ids = mutableSetOf(9)
        val byShip = mutableMapOf(3L to ids, 4L to ids)
        val byId = mutableMapOf(9 to joint)
        val physics = mockk<VsiPhysLevel>(relaxed = true)
        every { physics.getJointsByShipIds() } answers { byShip }
        every { physics.getAllJoints() } answers { byId }
        every { physics.getAllPhysShips() } returns emptySet()
        val adapter = GameToPhysicsAdapter()
        adapter.physTick(physics, 1.0 / 60.0)
        ids.clear()
        byShip.clear()
        byId.clear()
        assertEquals(setOf(9), adapter.getJointsFromShip(3))
        assertEquals(joint, adapter.getJointById(9))
        assertEquals(setOf(3L, 4L), adapter.getAllConnectedShips(3).toSet())
        adapter.physTick(physics, 1.0 / 60.0)
        assertEquals(emptyMap<Int, VSJoint>(), adapter.getAllJoints())
    }
}
