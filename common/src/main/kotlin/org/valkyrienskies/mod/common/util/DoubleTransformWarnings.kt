package org.valkyrienskies.mod.common.util

import org.apache.logging.log4j.LogManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Warns, once per call site, when a VS transform helper is given a point already in the target space and leaves it
 * alone. Raw use of a ship's matrices is reported by vs-core instead.
 */
object DoubleTransformWarnings {
    private val LOGGER = LogManager.getLogger("[Valkyrien Skies] Double transform")
    private val loggedCallSites: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val helpers = listOf(
        DoubleTransformWarnings::class.java.name,
        "org.valkyrienskies.mod.api.ValkyrienSkies.",
        "org.valkyrienskies.mod.common.VSGameUtilsKt",
        "org.valkyrienskies.mod.common.CompatUtil"
    )

    /** Total no-ops, logged or not. */
    @JvmField
    val count = AtomicInteger()

    @JvmStatic
    fun warn(toWorld: Boolean, x: Double, y: Double, z: Double) {
        count.incrementAndGet()
        if (loggedCallSites.size >= 256) return
        val frames = StackWalker.getInstance().walk { stream ->
            stream.map { "${it.className}.${it.methodName}:${it.lineNumber}" }
                .filter { frame -> helpers.none { frame.startsWith(it) } }
                .limit(6)
                .toList()
        }
        if (frames.isEmpty() || !loggedCallSites.add(frames[0])) return
        val what = if (toWorld) "ship-to-world of a point already in the world" else "world-to-ship of a point already in the shipyard"
        LOGGER.warn("Skipped {} ({}, {}, {}) at:\n    {}", what, x, y, z, frames.joinToString("\n    "))
    }
}
