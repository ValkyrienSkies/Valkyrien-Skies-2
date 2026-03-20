package org.valkyrienskies.mod.feature.ship_water_pockets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.computeEnclosedHeuristicFromGeometry
import org.valkyrienskies.mod.common.air_pockets.MIN_OPENING_CONDUCTANCE
import org.valkyrienskies.mod.common.air_pockets.computeOutsideVoidFromGeometry
import java.util.BitSet

class ShipWaterPocketDomainTest {
    @Test
    fun computeOutsideVoidFromGeometryIsDeterministicForHandBuiltGrid() {
        val sizeX = 3
        val sizeY = 3
        val sizeZ = 3
        val volume = sizeX * sizeY * sizeZ

        val boundaryIdx = indexOf(sizeX, sizeY, x = 0, y = 1, z = 1)
        val interiorIdx = indexOf(sizeX, sizeY, x = 1, y = 1, z = 1)

        val open = BitSet(volume).apply { set(0, volume) }
        val simulationDomain = BitSet(volume).apply {
            set(0, volume)
            clear(boundaryIdx)
            clear(interiorIdx)
        }

        val faceCondXP = ShortArray(volume)
        faceCondXP[boundaryIdx] = MIN_OPENING_CONDUCTANCE.toShort()
        val faceCondYP = ShortArray(volume)
        val faceCondZP = ShortArray(volume)

        val first = computeOutsideVoidFromGeometry(
            open = open,
            simulationDomain = simulationDomain,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            faceCondXP = faceCondXP,
            faceCondYP = faceCondYP,
            faceCondZP = faceCondZP,
        )
        val second = computeOutsideVoidFromGeometry(
            open = open,
            simulationDomain = simulationDomain,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            faceCondXP = faceCondXP,
            faceCondYP = faceCondYP,
            faceCondZP = faceCondZP,
        )

        val expected = BitSet(volume).apply {
            set(boundaryIdx)
            set(interiorIdx)
        }
        assertTrue(first.get(boundaryIdx))
        assertTrue(first.get(interiorIdx))
        assertEquals(expected, first)
        assertEquals(first, second)
    }

    @Test
    fun computeEnclosedHeuristicRejectsSideOpenCavity() {
        val sizeX = 5
        val sizeY = 5
        val sizeZ = 5
        val volume = sizeX * sizeY * sizeZ

        val open = BitSet(volume)
        // 1-block-wide tunnel that leaves the center cell open to +X boundary.
        open.set(indexOf(sizeX, sizeY, x = 1, y = 2, z = 2))
        open.set(indexOf(sizeX, sizeY, x = 2, y = 2, z = 2))
        open.set(indexOf(sizeX, sizeY, x = 3, y = 2, z = 2))
        open.set(indexOf(sizeX, sizeY, x = 4, y = 2, z = 2))

        val faceCondXP = ShortArray(volume)
        val faceCondYP = ShortArray(volume)
        val faceCondZP = ShortArray(volume)

        faceCondXP[indexOf(sizeX, sizeY, x = 1, y = 2, z = 2)] = MIN_OPENING_CONDUCTANCE.toShort()
        faceCondXP[indexOf(sizeX, sizeY, x = 2, y = 2, z = 2)] = MIN_OPENING_CONDUCTANCE.toShort()
        faceCondXP[indexOf(sizeX, sizeY, x = 3, y = 2, z = 2)] = MIN_OPENING_CONDUCTANCE.toShort()

        val enclosed = computeEnclosedHeuristicFromGeometry(
            open = open,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            faceCondXP = faceCondXP,
            faceCondYP = faceCondYP,
            faceCondZP = faceCondZP,
            passCondThreshold = MIN_OPENING_CONDUCTANCE,
        )

        val sideOpenCenter = indexOf(sizeX, sizeY, x = 1, y = 2, z = 2)
        assertFalse(enclosed.get(sideOpenCenter))
    }

    private fun indexOf(sizeX: Int, sizeY: Int, x: Int, y: Int, z: Int): Int {
        return x + sizeX * (y + sizeY * z)
    }
}
