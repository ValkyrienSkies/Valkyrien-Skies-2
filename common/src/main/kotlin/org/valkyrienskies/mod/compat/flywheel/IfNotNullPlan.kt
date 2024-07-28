package org.valkyrienskies.mod.compat.flywheel

import dev.engine_room.flywheel.api.task.Plan
import dev.engine_room.flywheel.api.task.TaskExecutor
import dev.engine_room.flywheel.lib.task.SimplyComposedPlan

class IfNotNullPlan<C: Any, V>(val nullable: () -> V?, val getPlan: (V) -> Plan<C>) : SimplyComposedPlan<C> {
    override fun execute(taskExecutor: TaskExecutor, context: C, onCompletion: Runnable) {
        val n = nullable()
        if (n != null) {
            getPlan(n).execute(taskExecutor, context, onCompletion)
        }
    }
}
