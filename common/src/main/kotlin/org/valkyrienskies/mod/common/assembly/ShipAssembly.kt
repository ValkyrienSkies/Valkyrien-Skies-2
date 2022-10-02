package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.LevelChunkSection
import org.valkyrienskies.core.datastructures.ChunkSet
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import java.util.SortedMap

fun createNewShipWithBlocks(
    centerBlock: BlockPos, blocks: SortedMap<SectionPos, ChunkSet>, level: ServerLevel
) {
    val ship = level.shipObjectWorld.createNewShipAtBlock(centerBlock.toJOML(), false, 1.0, level.dimensionId)
    ship.isStatic = true

    val middleChunkX = ship.chunkClaim.xMiddle
    val middleChunkZ = ship.chunkClaim.zMiddle

    val centerChunkX = centerBlock.x shr 4
    val centerChunkZ = centerBlock.z shr 4

    val deltaX = centerChunkX - middleChunkX
    val deltaZ = centerChunkZ - middleChunkZ

    blocks.forEach { (pos, blocks) ->
        println("Moving section: $pos")
        val sourceChunk = level.getChunk(pos.x, pos.z)
        val destChunk = level.getChunk(pos.x - deltaX, pos.z - deltaZ)

        val sourceSection = sourceChunk.sections[pos.y]
            ?: throw IllegalArgumentException("ChunkSet at $pos doesn't have a section that exists at ${pos.y}")

        var destSection = destChunk.sections[pos.y]

        if (destSection == null) {
            destSection = LevelChunkSection(pos.y shl 4)
            destChunk.sections[pos.y] = destSection
        }

        blocks.iterateSetBlocks { x, y, z ->
            println("Moving block $x, $y, $z, in section $pos")
            val localX = x and 15
            val localY = y and 15
            val localZ = z and 15

            val blockState = sourceSection.getBlockState(localX, localY, localZ)
            destSection.setBlockState(localX, localY, localZ, blockState, false)
        }

        val lights = level.chunkSource.lightEngine

        lights.updateSectionStatus(pos, false)
        lights.lightChunk(sourceChunk, false) // todo what does this flag do lol

        lights.updateSectionStatus(SectionPos.of(destChunk.pos.x, pos.y, destChunk.pos.z), false)
        lights.lightChunk(destChunk, false)
    }
}
