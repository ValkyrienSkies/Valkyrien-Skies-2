package org.valkyrienskies.mod.common.blockentity

import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.util.pollUntilEmpty
import org.valkyrienskies.mod.common.util.ITestTickable
import java.util.concurrent.ConcurrentLinkedQueue

@OptIn(VsBeta::class)
object DebugPhysicsTickables {
    val toTick = HashSet<ITestTickable>()
    val toRemove = ConcurrentLinkedQueue<ITestTickable>()
    val toAdd = ConcurrentLinkedQueue<ITestTickable>()

    fun add(testTickable: ITestTickable) {
        toAdd.add(testTickable)
    }
    fun remove(testTickable: ITestTickable) {
        toRemove.add(testTickable)
    }

    fun physTick(physLevel: PhysLevel, delta: Double) {
        toAdd.pollUntilEmpty { tickable -> toTick.add(tickable) }

        toRemove.pollUntilEmpty { tickable -> toTick.remove(tickable) }

        toTick.forEach { if (it.matchesDimension(physLevel.dimension)) it.physTick(physLevel, delta) }
    }
}
