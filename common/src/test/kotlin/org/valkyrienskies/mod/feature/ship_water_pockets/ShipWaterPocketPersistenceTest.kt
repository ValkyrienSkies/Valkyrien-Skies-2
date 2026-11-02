package org.valkyrienskies.mod.feature.ship_water_pockets

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import net.minecraft.SharedConstants
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.MIN_OPENING_CONDUCTANCE
import org.valkyrienskies.mod.common.air_pockets.PersistedShipPocketState
import org.valkyrienskies.mod.common.air_pockets.ShipPocketSavedData
import java.util.BitSet

class ShipWaterPocketPersistenceTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun v3RoundTripPreservesOutsideVoid() {
        val volume = 2 * 2 * 1
        val open = bitSetOf(0, 1, 2, 3)
        val simulationDomain = bitSetOf(1, 2)
        val outsideVoid = bitSetOf(0, 3)
        val flooded = bitSetOf(1)
        val materialized = bitSetOf(2)
        val waterReachable = bitSetOf(1, 2)
        val unreachableVoid = bitSetOf(0, 3)
        val floodPlanes = Int2DoubleOpenHashMap().apply { put(7, 12.5) }

        val persisted = PersistedShipPocketState(
            minX = 10,
            minY = 20,
            minZ = 30,
            sizeX = 2,
            sizeY = 2,
            sizeZ = 1,
            open = open,
            exterior = outsideVoid.clone() as BitSet,
            strictInterior = simulationDomain.clone() as BitSet,
            simulationDomain = simulationDomain,
            outsideVoid = outsideVoid,
            interior = simulationDomain.clone() as BitSet,
            floodFluid = Fluids.WATER,
            flooded = flooded,
            materializedWater = materialized,
            waterReachable = waterReachable,
            unreachableVoid = unreachableVoid,
            faceCondXP = ShortArray(volume),
            faceCondYP = ShortArray(volume),
            faceCondZP = ShortArray(volume),
            voxelExteriorComponentMask = longArrayOf(0x01),
            voxelInteriorComponentMask = longArrayOf(0x02),
            voxelSimulationComponentMask = longArrayOf(0x04),
            floodPlaneByComponent = floodPlanes,
            geometryRevision = 11L,
            geometrySignature = 12L,
        )

        val loaded = persistAndLoad(persisted, shipId = 101L)
        assertEquals(outsideVoid, loaded.outsideVoid)
        assertFalse(loaded.requiresResave)
    }

    @Test
    fun v2LoadReconstructsOutsideVoidFromGeometry() {
        val sizeX = 3
        val sizeY = 3
        val sizeZ = 3
        val volume = sizeX * sizeY * sizeZ
        val boundaryIdx = indexOf(sizeX, sizeY, 0, 1, 1)
        val interiorIdx = indexOf(sizeX, sizeY, 1, 1, 1)

        val open = fullBitSet(volume)
        val simulationDomain = fullBitSet(volume).apply {
            clear(boundaryIdx)
            clear(interiorIdx)
        }
        val faceCondXP = ShortArray(volume)
        faceCondXP[boundaryIdx] = MIN_OPENING_CONDUCTANCE.toShort()

        val persisted = basePersistedState(
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            open = open,
            simulationDomain = simulationDomain,
            outsideVoid = BitSet(),
            faceCondXP = faceCondXP,
            faceCondYP = ShortArray(volume),
            faceCondZP = ShortArray(volume),
        )

        val loaded = persistAndLoad(persisted, shipId = 202L, formatVersion = 2)
        assertFalse(loaded.outsideVoid.isEmpty)
        assertTrue(loaded.outsideVoid.get(boundaryIdx))
        assertTrue(loaded.outsideVoid.get(interiorIdx))
        assertTrue(loaded.requiresResave)
    }

    @Test
    fun v2LoadFallsBackToOpenMinusSimulationDomainWhenConductanceIsInvalid() {
        val sizeX = 3
        val sizeY = 3
        val sizeZ = 3
        val volume = sizeX * sizeY * sizeZ
        val boundaryIdx = indexOf(sizeX, sizeY, 0, 1, 1)
        val interiorIdx = indexOf(sizeX, sizeY, 1, 1, 1)

        val open = fullBitSet(volume)
        val simulationDomain = fullBitSet(volume).apply {
            clear(boundaryIdx)
            clear(interiorIdx)
        }

        val persisted = basePersistedState(
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            open = open,
            simulationDomain = simulationDomain,
            outsideVoid = BitSet(),
            faceCondXP = ShortArray(1),
            faceCondYP = ShortArray(1),
            faceCondZP = ShortArray(1),
        )

        val loaded = persistAndLoad(persisted, shipId = 303L, formatVersion = 2)
        val expectedOutsideVoid = open.clone() as BitSet
        expectedOutsideVoid.andNot(simulationDomain)

        assertEquals(expectedOutsideVoid, loaded.outsideVoid)
        assertTrue(loaded.faceCondXP.isEmpty())
        assertTrue(loaded.faceCondYP.isEmpty())
        assertTrue(loaded.faceCondZP.isEmpty())
        assertTrue(loaded.requiresResave)
    }

    @Test
    fun normalizationClampsMasksAndEnforcesOutsideVoidDisjointFromSimulationDomain() {
        val volume = 2 * 2 * 1
        val open = bitSetOf(0, 1, 8)
        val simulationDomain = bitSetOf(1, 3, 9)
        val outsideVoid = bitSetOf(1, 2, 10)
        val flooded = bitSetOf(1, 3)
        val materialized = bitSetOf(2, 3)
        val waterReachable = bitSetOf(0, 2, 11)
        val unreachableVoid = bitSetOf(1, 3, 12)
        val strictInterior = bitSetOf(1, 7)
        val exterior = bitSetOf(0, 2, 6)

        val persisted = PersistedShipPocketState(
            minX = 0,
            minY = 0,
            minZ = 0,
            sizeX = 2,
            sizeY = 2,
            sizeZ = 1,
            open = open,
            exterior = exterior,
            strictInterior = strictInterior,
            simulationDomain = simulationDomain,
            outsideVoid = outsideVoid,
            interior = strictInterior.clone() as BitSet,
            floodFluid = Fluids.WATER,
            flooded = flooded,
            materializedWater = materialized,
            waterReachable = waterReachable,
            unreachableVoid = unreachableVoid,
            faceCondXP = ShortArray(volume),
            faceCondYP = ShortArray(volume),
            faceCondZP = ShortArray(volume),
            voxelExteriorComponentMask = LongArray(0),
            voxelInteriorComponentMask = LongArray(0),
            voxelSimulationComponentMask = LongArray(0),
            floodPlaneByComponent = Int2DoubleOpenHashMap(),
            geometryRevision = 0L,
            geometrySignature = 0L,
        )

        val loaded = persistAndLoad(persisted, shipId = 404L)
        assertTrue(loaded.requiresResave)

        assertFalse(hasBitsAtOrAbove(loaded.open, volume))
        assertFalse(hasBitsAtOrAbove(loaded.simulationDomain, volume))
        assertFalse(hasBitsAtOrAbove(loaded.outsideVoid, volume))
        assertFalse(hasBitsAtOrAbove(loaded.flooded, volume))
        assertFalse(hasBitsAtOrAbove(loaded.materializedWater, volume))

        assertTrue(isSubset(loaded.simulationDomain, loaded.open))
        assertTrue(isSubset(loaded.outsideVoid, loaded.open))
        assertFalse(loaded.outsideVoid.intersects(loaded.simulationDomain))
        assertTrue(isSubset(loaded.flooded, loaded.simulationDomain))
        assertTrue(isSubset(loaded.materializedWater, loaded.simulationDomain))
    }

    private fun persistAndLoad(
        persisted: PersistedShipPocketState,
        shipId: Long,
        formatVersion: Int? = null,
    ): PersistedShipPocketState {
        val data = ShipPocketSavedData.createEmpty()
        data.putState(shipId, persisted)

        val tag = CompoundTag()
        data.save(tag)
        if (formatVersion != null) {
            tag.putInt("format_version", formatVersion)
        }

        return ShipPocketSavedData.load(tag).getState(shipId)
            ?: error("Missing loaded state for shipId=$shipId")
    }

    private fun basePersistedState(
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
        open: BitSet,
        simulationDomain: BitSet,
        outsideVoid: BitSet,
        faceCondXP: ShortArray,
        faceCondYP: ShortArray,
        faceCondZP: ShortArray,
    ): PersistedShipPocketState {
        val strictInterior = simulationDomain.clone() as BitSet
        val exterior = open.clone() as BitSet
        exterior.andNot(simulationDomain)
        return PersistedShipPocketState(
            minX = 0,
            minY = 0,
            minZ = 0,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            open = open,
            exterior = exterior,
            strictInterior = strictInterior,
            simulationDomain = simulationDomain,
            outsideVoid = outsideVoid,
            interior = strictInterior.clone() as BitSet,
            floodFluid = Fluids.WATER,
            flooded = BitSet(),
            materializedWater = BitSet(),
            waterReachable = BitSet(),
            unreachableVoid = BitSet(),
            faceCondXP = faceCondXP,
            faceCondYP = faceCondYP,
            faceCondZP = faceCondZP,
            voxelExteriorComponentMask = LongArray(0),
            voxelInteriorComponentMask = LongArray(0),
            voxelSimulationComponentMask = LongArray(0),
            floodPlaneByComponent = Int2DoubleOpenHashMap(),
            geometryRevision = 0L,
            geometrySignature = 0L,
        )
    }

    private fun fullBitSet(size: Int): BitSet {
        return BitSet(size).apply { set(0, size) }
    }

    private fun bitSetOf(vararg indices: Int): BitSet {
        return BitSet().apply {
            for (idx in indices) {
                if (idx >= 0) set(idx)
            }
        }
    }

    private fun hasBitsAtOrAbove(bits: BitSet, boundExclusive: Int): Boolean {
        return bits.nextSetBit(boundExclusive) >= 0
    }

    private fun isSubset(subset: BitSet, superset: BitSet): Boolean {
        var idx = subset.nextSetBit(0)
        while (idx >= 0) {
            if (!superset.get(idx)) return false
            idx = subset.nextSetBit(idx + 1)
        }
        return true
    }

    private fun indexOf(sizeX: Int, sizeY: Int, x: Int, y: Int, z: Int): Int {
        return x + sizeX * (y + sizeY * z)
    }
}
