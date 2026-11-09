package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import org.joml.Vector3dc
import kotlin.math.max

internal object SplitOwnership {
    data class Anchor(val position: Vector3dc, val grounded: Boolean)

    fun retainedComponent(components: List<Set<BlockPos>>, anchors: List<Anchor>): Int {
        require(components.isNotEmpty())
        val grounded = IntArray(components.size)
        val attached = IntArray(components.size)
        for (anchor in anchors) {
            if (!anchor.position.isFinite) continue
            val nearest = components.indices.minWithOrNull(
                compareBy<Int> { index -> components[index].minOf { distanceToBlock(anchor.position, it) } }
                    .thenByDescending { components[it].size }
                    .thenBy { it }
            )!!
            attached[nearest]++
            if (anchor.grounded) grounded[nearest]++
        }
        return components.indices.maxWithOrNull(
            compareBy<Int> { grounded[it] }.thenBy { attached[it] }.thenBy { components[it].size }
                .thenBy { -it }
        )!!
    }

    private fun distanceToBlock(point: Vector3dc, block: BlockPos): Double {
        fun axis(p: Double, b: Int) = max(0.0, max(b - p, p - (b + 1.0)))
        val x = axis(point.x(), block.x)
        val y = axis(point.y(), block.y)
        val z = axis(point.z(), block.z)
        return x * x + y * y + z * z
    }
}
