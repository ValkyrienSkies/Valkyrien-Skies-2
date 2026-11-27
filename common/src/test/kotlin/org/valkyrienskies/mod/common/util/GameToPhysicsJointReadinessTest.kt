package org.valkyrienskies.mod.common.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.bodies.PhysVsBody
import org.valkyrienskies.core.internal.joints.VSJoint
import org.valkyrienskies.core.internal.world.VsiPhysLevel

class GameToPhysicsJointReadinessTest {
    @Test fun `joint survives body creation later than fixed delay`() {
        val physics = mockk<VsiPhysLevel>(relaxed = true)
        val joint = mockk<VSJoint>()
        val body = mockk<PhysVsBody>()
        every { joint.shipId0 } returns null
        every { joint.shipId1 } returns 3L
        var ready = false
        every { physics.getBodyById(3L) } answers { if (ready) body else null }
        every { physics.addJoint(joint) } answers { if (ready) 9 else -1 }
        every { physics.getAllPhysShips() } returns emptySet()
        val results = mutableListOf<Int>()
        val adapter = GameToPhysicsAdapter()
        adapter.addJointWhenBodiesReady(joint, delay = 4, bodyWaitTicks = 30) { results.add(it) }
        repeat(8) { adapter.physTick(physics, 1.0 / 60.0) }
        verify(exactly = 0) { physics.addJoint(any()) }
        ready = true
        repeat(4) { adapter.physTick(physics, 1.0 / 60.0) }
        assertEquals(listOf(9), results)
    }

    private fun fixture(): Triple<GameToPhysicsAdapter, VsiPhysLevel, VSJoint> {
        val adapter = GameToPhysicsAdapter()
        val physics = mockk<VsiPhysLevel>(relaxed = true)
        every { physics.getAllPhysShips() } returns emptySet()
        val joint = mockk<VSJoint>()
        every { joint.shipId0 } returns 3L
        every { joint.shipId1 } returns 4L
        return Triple(adapter, physics, joint)
    }

    @Test fun `waits for both bodies and publishes indexes before callback`() {
        val (adapter, physics, joint) = fixture()
        val body = mockk<PhysVsBody>()
        every { physics.getBodyById(3L) } returns body
        every { physics.getBodyById(4L) } returns null
        val byId = mutableMapOf<Int, VSJoint>()
        every { physics.getAllJoints() } answers { byId }
        every { physics.addJoint(joint) } answers { byId[9] = joint; 9 }
        val results = mutableListOf<Int>()
        adapter.addJointWhenBodiesReady(joint) {
            assertEquals(joint, adapter.getJointById(it))
            results.add(it)
        }
        adapter.physTick(physics, 0.016)
        verify(exactly = 0) { physics.addJoint(any()) }
        every { physics.getBodyById(4L) } returns body
        repeat(3) { adapter.physTick(physics, 0.016) }
        assertEquals(listOf(9), results)
        verify(exactly = 1) { physics.addJoint(joint) }
    }

    @Test fun `missing body wait is bounded and reports failure once`() {
        val (adapter, physics, joint) = fixture()
        every { physics.getBodyById(any()) } returns null
        val results = mutableListOf<Int>()
        adapter.addJointWhenBodiesReady(joint, bodyWaitTicks = 2) { results.add(it) }
        repeat(2) { adapter.physTick(physics, 0.016) }
        assertEquals(emptyList<Int>(), results)
        repeat(5) { adapter.physTick(physics, 0.016) }
        assertEquals(listOf(-1), results)
        verify(exactly = 0) { physics.addJoint(any()) }
    }

    @Test fun `permanent insertion failure is not retried`() {
        val (adapter, physics, joint) = fixture()
        every { physics.getBodyById(any()) } returns mockk<PhysVsBody>()
        every { physics.addJoint(joint) } returns -1
        val results = mutableListOf<Int>()
        adapter.addJointWhenBodiesReady(joint) { results.add(it) }
        repeat(5) { adapter.physTick(physics, 0.016) }
        assertEquals(listOf(-1), results)
        verify(exactly = 1) { physics.addJoint(joint) }
    }

    @Test fun `cancellation before readiness prevents late joint`() {
        val (adapter, physics, joint) = fixture()
        every { physics.getBodyById(any()) } returns null
        val results = mutableListOf<Int>()
        val cancel = adapter.addJointWhenBodiesReady(joint) { results.add(it) }
        adapter.physTick(physics, 0.016)
        cancel(); cancel()
        every { physics.getBodyById(any()) } returns mockk<PhysVsBody>()
        repeat(3) { adapter.physTick(physics, 0.016) }
        assertEquals(emptyList<Int>(), results)
        verify(exactly = 0) { physics.addJoint(any()) }
    }

    @Test fun `cancellation after insertion removes created joint once`() {
        val (adapter, physics, joint) = fixture()
        every { physics.getBodyById(any()) } returns mockk<PhysVsBody>()
        every { physics.addJoint(joint) } returns 9
        val results = mutableListOf<Int>()
        val cancel = adapter.addJointWhenBodiesReady(joint) { results.add(it) }
        adapter.physTick(physics, 0.016)
        cancel(); cancel()
        repeat(3) { adapter.physTick(physics, 0.016) }
        assertEquals(listOf(9), results)
        verify(exactly = 1) { physics.removeJoint(9) }
    }

    @Test fun `existing callers retain immediate failure semantics`() {
        val (adapter, physics, joint) = fixture()
        every { physics.addJoint(joint) } returns -1
        val results = mutableListOf<Int>()
        adapter.addJoint(joint) { results.add(it) }
        adapter.physTick(physics, 0.016)
        assertEquals(listOf(-1), results)
        verify(exactly = 0) { physics.getBodyById(any()) }
    }

    @Test fun `cancellation during insertion removes result without callback`() {
        val (adapter, physics, joint) = fixture()
        every { physics.getBodyById(any()) } returns mockk<PhysVsBody>()
        lateinit var cancel: () -> Unit
        every { physics.addJoint(joint) } answers { cancel(); 9 }
        val results = mutableListOf<Int>()
        cancel = adapter.addJointWhenBodiesReady(joint) { results.add(it) }
        repeat(3) { adapter.physTick(physics, 0.016) }
        assertEquals(emptyList<Int>(), results)
        verify(exactly = 1) { physics.removeJoint(9) }
    }
}
