package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssemblyChunkCoverageTest {
    @Test
    fun `one source chunk can move into several destination chunks`() {
        val corner = BlockPos(4, 70, 4)
        val blocks = setOf(corner, corner.east(2), corner.south(2))
        val (source, destination) = ShipAssembler.chunksForMove(blocks, corner, BlockPos(-1, 64, -1))

        assertEquals(setOf(ChunkPos(0, 0)), source)
        assertEquals(setOf(ChunkPos(-1, -1), ChunkPos(0, -1), ChunkPos(-1, 0)), destination)
    }

    @Test
    fun `blocks from different source chunks can share a destination chunk`() {
        val corner = BlockPos(-17, 70, 15)
        val blocks = setOf(corner, corner.east(), corner.south())
        val (source, destination) = ShipAssembler.chunksForMove(blocks, corner, BlockPos(0, 64, 0))

        assertEquals(setOf(ChunkPos(-2, 0), ChunkPos(-1, 0), ChunkPos(-2, 1)), source)
        assertEquals(setOf(ChunkPos(0, 0)), destination)
    }
}
