package org.valkyrienskies.mod.common.fluid

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidTopologySnapshot
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel

internal fun maskTopology(vararg cells: Triple<Int, Int, Int>) = FluidTopologyClientCache.CachedSnapshot(
    VsiFluidTopologySnapshot(
        bodyId = 7, dimensionId = "minecraft:overworld", streamSequence = 3,
        nativePublicationId = 2, sourceRevision = 11, enabled = true, valid = true,
        totalDomainVoxelCount = cells.size,
        voxels = cells.map { VsiFluidTopologyVoxel(it.first, it.second, it.third, 1) },
    )
)

internal fun maskFlooding(vararg cells: VsiFluidFloodedVoxel) =
    FloodedFluidClientCache.CachedSnapshot(floodingSnapshot(*cells))

class FluidOcclusionMaskDataTest {
    @Test
    fun `all fill amounts preserve exactly their dry fraction`() {
        val topology = maskTopology(*(0..256).map { Triple(it - 20, 0, 0) }.toTypedArray())
        val flooding = maskFlooding(*(1..255).map { fill ->
            VsiFluidFloodedVoxel(fill - 20, 0, 0, 17, fill, 0)
        }.toTypedArray())
        val data = FluidOcclusionMaskData.build(topology, flooding)!!
        assertEquals(254, data.partialCount)
        val records = partialRecords(data)
        assertEquals((1..254).map { (it.toLong() shl 8) or it.toLong() }, records)
        for (index in 0..256) {
            val expected = when (index) { 0, 256 -> 1; 255 -> 0; else -> 2 }
            assertEquals(expected, stateAt(data, index), "classification at $index")
            val fill = topology.getDryCellFillAmount(index - 20, 0, 0, flooding)
            assertEquals(if (index == 255) -1 else if (index == 256) 0 else index, fill)
        }
        assertEquals(-1, topology.getDryCellFillAmount(1000, 0, 0, flooding))
    }

    @Test
    fun `binary masks keep one bit per cell and holes remain unmasked`() {
        val data = FluidOcclusionMaskData.build(maskTopology(Triple(-1, 0, 0), Triple(35, 0, 0)), null)!!
        assertEquals(0, data.partialCount)
        assertEquals(1, stateAt(data, 0))
        assertEquals(1, stateAt(data, 36))
        for (index in 1..35) assertEquals(0, stateAt(data, index))
        assertEquals(FluidOcclusionMaskData.TEXTURE_WIDTH * 4, data.bytes.capacity())
    }

    @Test
    fun `large sparse indices sort as unsigned records without expanding to byte per voxel`() {
        val data = largeSparseMask()
        assertEquals(2, data.partialCount)
        assertEquals(listOf((0x800000L shl 8) or 1L, (0xffffffL shl 8) or 254L), partialRecords(data))
        assertEquals(2, stateAt(data, 0x800000))
        assertEquals(2, stateAt(data, 0xffffff))
        assertEquals(0, stateAt(data, 0xfffffe))
        assertEquals(4 * 1024 * 1024 + 16 * 1024, data.bytes.capacity())
    }

    @Test
    fun `full wet empty and disabled domains contain no mask`() {
        assertNull(FluidOcclusionMaskData.build(maskTopology(), null))
        assertNull(FluidOcclusionMaskData.build(maskTopology(Triple(0, 0, 0)),
            maskFlooding(VsiFluidFloodedVoxel(0, 0, 0, 17, 255, 0))))
        val disabled = FluidTopologyClientCache.CachedSnapshot(topologySnapshot().copy(enabled = false))
        assertNull(FluidOcclusionMaskData.build(disabled, null))
    }

    @Test
    fun `oversized and overflowing bounds are rejected before allocation`() {
        assertThrows(IllegalArgumentException::class.java) {
            FluidOcclusionMaskData.build(maskTopology(Triple(Int.MIN_VALUE, 0, 0), Triple(Int.MAX_VALUE, 0, 0)), null)
        }
    }

    companion object {
        internal fun largeSparseMask(): FluidOcclusionMaskData = FluidOcclusionMaskData.build(
            maskTopology(Triple(255, 255, 255), Triple(0, 0, 0), Triple(0, 0, 128)),
            maskFlooding(VsiFluidFloodedVoxel(255, 255, 255, 17, 254, 0),
                VsiFluidFloodedVoxel(0, 0, 128, 17, 1, 0)),
        )!!

        private fun word(data: FluidOcclusionMaskData, offset: Int): Long = (0..3).fold(0L) { result, b ->
            result or ((data.bytes.get(offset + b).toLong() and 255) shl (b * 8))
        }

        private fun stateAt(data: FluidOcclusionMaskData, index: Int): Int {
            val bits = if (data.partialCount == 0) 1 else 2
            val bit = index * bits
            return ((data.bytes.get(bit / 8).toInt() and 255) ushr (bit % 8)) and ((1 shl bits) - 1)
        }

        private fun partialRecords(data: FluidOcclusionMaskData): List<Long> {
            val words = (data.sizeX * data.sizeY * data.sizeZ * 2 + 31) / 32
            return (0 until data.partialCount).map { word(data, (words + it) * 4) }
        }
    }
}
