package org.valkyrienskies.mod.feature.ship_water_pockets

import it.unimi.dsi.fastutil.ints.IntArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.PendingFloodComponentOrder
import org.valkyrienskies.mod.common.air_pockets.mergeOrderedFloodComponentAdds

class ShipWaterPocketFloodOrderingTest {

    @Test
    fun mergeOrderedFloodComponentAddsInterleavesEqualWeightStarts() {
        val merged = mergeOrderedFloodComponentAdds(
            listOf(
                component(1, 1, 2, 3),
                component(1, 10, 11, 12),
            ),
        )

        assertEquals(listOf(1, 10, 2, 11, 3, 12), asList(merged))
    }

    @Test
    fun mergeOrderedFloodComponentAddsKeepsSmallerComponentActiveDuringWeightedCycles() {
        val merged = mergeOrderedFloodComponentAdds(
            listOf(
                component(3, 1, 2, 3, 4, 5),
                component(1, 10, 11, 12),
            ),
        )

        assertEquals(listOf(1, 10, 2, 3, 4, 11, 5, 12), asList(merged))
    }

    @Test
    fun mergeOrderedFloodComponentAddsPreservesSingleComponentOrder() {
        val merged = mergeOrderedFloodComponentAdds(
            listOf(component(4, 7, 4, 9, 12)),
        )

        assertEquals(listOf(7, 4, 9, 12), asList(merged))
    }

    private fun component(weight: Int, vararg ordered: Int): PendingFloodComponentOrder {
        val indices = IntArrayList(ordered.size)
        for (idx in ordered) {
            indices.add(idx)
        }
        return PendingFloodComponentOrder(indices, weight)
    }

    private fun asList(indices: IntArrayList): List<Int> {
        return (0 until indices.size).map { indices.getInt(it) }
    }
}
