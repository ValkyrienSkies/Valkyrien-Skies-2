package org.valkyrienskies.mod.feature.ship_water_pockets

import it.unimi.dsi.fastutil.ints.IntArrayList
import java.util.BitSet
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.FloodWriteAddDisposition
import org.valkyrienskies.mod.common.air_pockets.MIN_OPENING_CONDUCTANCE
import org.valkyrienskies.mod.common.air_pockets.ShipPocketState
import org.valkyrienskies.mod.common.air_pockets.isFloodAddFrontierReady
import org.valkyrienskies.mod.common.air_pockets.processQueuedAddIndices

class ShipWaterPocketFloodWriteFrontierTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun higherCellWaitsForLowerMaterializedFrontier() {
        val state = linearVerticalState(sizeY = 3)
        state.materializedWater.set(0)
        val addedThisFlush = BitSet()

        assertTrue(isFloodAddFrontierReady(state, 1, addedThisFlush) { false })
        assertFalse(isFloodAddFrontierReady(state, 2, addedThisFlush) { false })
    }

    @Test
    fun cellAdjacentToSubmergedExteriorOpeningCanSeedFlooding() {
        val state = ShipPocketState(
            sizeX = 1,
            sizeY = 2,
            sizeZ = 1,
            open = fullBitSet(2),
            simulationDomain = bitSetOf(0),
            outsideVoid = bitSetOf(1),
            faceCondXP = ShortArray(2),
            faceCondYP = shortArrayOf(MIN_OPENING_CONDUCTANCE.toShort(), 0),
            faceCondZP = ShortArray(2),
        )

        assertTrue(isFloodAddFrontierReady(state, 0, BitSet()) { it == 1 })
        assertFalse(isFloodAddFrontierReady(state, 0, BitSet()) { false })
    }

    @Test
    fun deferredQueuedCellAppliesOnLaterFlushOnceFrontierAdvances() {
        val state = linearVerticalState(sizeY = 3)
        state.materializedWater.set(0)
        state.queuedFloodAdds.set(1)
        state.queuedFloodAdds.set(2)
        state.queuedFloodAddOrder = IntArrayList().apply {
            add(2)
            add(1)
        }

        val addedFirstFlush = BitSet()
        val firstApplied = processQueuedAddIndices(state, budget = 1) { idx ->
            if (!isFloodAddFrontierReady(state, idx, addedFirstFlush) { false }) {
                FloodWriteAddDisposition.DEFERRED
            } else {
                addedFirstFlush.set(idx)
                state.materializedWater.set(idx)
                FloodWriteAddDisposition.APPLIED
            }
        }

        assertEquals(1, firstApplied.first)
        assertTrue(state.materializedWater.get(1))
        assertTrue(state.queuedFloodAdds.get(2))

        val addedSecondFlush = BitSet()
        val secondApplied = processQueuedAddIndices(state, budget = 1) { idx ->
            if (!isFloodAddFrontierReady(state, idx, addedSecondFlush) { false }) {
                FloodWriteAddDisposition.DEFERRED
            } else {
                addedSecondFlush.set(idx)
                state.materializedWater.set(idx)
                FloodWriteAddDisposition.APPLIED
            }
        }

        assertEquals(1, secondApplied.first)
        assertTrue(state.materializedWater.get(2))
        assertTrue(state.queuedFloodAdds.isEmpty)
    }

    private fun linearVerticalState(sizeY: Int): ShipPocketState {
        val volume = sizeY
        val faceCondYP = ShortArray(volume)
        for (y in 0 until sizeY - 1) {
            faceCondYP[y] = MIN_OPENING_CONDUCTANCE.toShort()
        }
        return ShipPocketState(
            sizeX = 1,
            sizeY = sizeY,
            sizeZ = 1,
            open = fullBitSet(volume),
            simulationDomain = fullBitSet(volume),
            outsideVoid = BitSet(),
            faceCondXP = ShortArray(volume),
            faceCondYP = faceCondYP,
            faceCondZP = ShortArray(volume),
        )
    }

    private fun bitSetOf(vararg indices: Int): BitSet {
        return BitSet().apply {
            for (idx in indices) set(idx)
        }
    }

    private fun fullBitSet(size: Int): BitSet {
        return BitSet(size).apply { set(0, size) }
    }
}
