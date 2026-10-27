package org.valkyrienskies.mod.common.debug

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Keep one pending value so a slow task does not build a queue of old data. */
internal class LatestValueTask<T : Any>(
    private val execute: (Runnable) -> Unit,
    private val consume: (T) -> Unit,
) {
    private val pending = AtomicReference<T?>()
    private val scheduled = AtomicBoolean(false)

    fun offer(value: T) {
        pending.set(value)
        schedule()
    }

    fun clear() {
        pending.set(null)
    }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        try {
            execute(Runnable {
                try {
                    pending.getAndSet(null)?.let(consume)
                } finally {
                    scheduled.set(false)
                    if (pending.get() != null) schedule()
                }
            })
        } catch (exception: Exception) {
            scheduled.set(false)
            throw exception
        }
    }
}
