package org.valkyrienskies.mod.common.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Refreshes the owner IDs published to the physics thread. The caller must resolve ownership on the
 * game thread, after assembly/loading has updated the ship world. Keep each listener and its owner
 * in one immutable snapshot so physics callbacks cannot observe a mismatched pair.
 */
internal fun <P, L> refreshPhysicsListenerOwners(
    listeners: ConcurrentHashMap<P, Pair<Long?, L>>,
    ownerAt: (P) -> Long?,
) {
    listeners.forEach { pos, registration ->
        val owner = ownerAt(pos)
        if (owner != registration.first) {
            // Do not resurrect a removed registration or overwrite a replacement listener.
            listeners.computeIfPresent(pos) { _, current ->
                if (current === registration) Pair(owner, registration.second) else current
            }
        }
    }
}
