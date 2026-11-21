package org.valkyrienskies.mod.common.assembly

import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.api.world.properties.DimensionId

internal class WeldCollisionGuard(private val dimension: DimensionId, private val targetId: Long,
                                  private val sourceId: Long) {
    @Volatile var active = true
    private var disabled = false

    @OptIn(PhysTickOnly::class)
    fun tick(level: PhysLevel): Boolean {
        if (level.dimension != dimension) return false
        if (active) {
            if (!disabled) disabled = level.disableCollisionBetween(targetId, sourceId)
            return false
        }
        // Restore only a collision pair that this weld disabled.
        if (disabled) level.enableCollisionBetween(targetId, sourceId)
        return true
    }
}
