package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunkSection
import org.valkyrienskies.core.datastructures.DenseBlockPosSet
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML

fun createNewShipWithBlocks(
    centerBlock: BlockPos, blocks: DenseBlockPosSet, level: ServerLevel
) {
    val ship = level.shipObjectWorld.createNewShipAtBlock(centerBlock.toJOML(), false, 1.0, level.dimensionId)
    ship.isStatic = true

    val middleChunkX = ship.chunkClaim.xMiddle
    val middleChunkZ = ship.chunkClaim.zMiddle

    val centerChunkX = centerBlock.x shr 4
    val centerChunkZ = centerBlock.z shr 4

    val deltaX = centerChunkX - middleChunkX
    val deltaZ = centerChunkZ - middleChunkZ

    blocks.forEachChunk { chunkX, chunkY, chunkZ, chunk ->
        println("Moving section: $chunkX $chunkY $chunkZ")
        val sourceChunk = level.getChunk(chunkX, chunkZ)
        val destChunk = level.getChunk(chunkX - deltaX, chunkZ - deltaZ)

        val sourceSection = sourceChunk.sections[chunkY]
            ?: throw IllegalArgumentException(
                "Chunk at $chunkX $chunkY $chunkZ doesn't have a section that exists at $chunkY"
            )

        var destSection = destChunk.sections[chunkY]

        if (destSection == null) {
            destSection = LevelChunkSection(chunkY shl 4)
            destChunk.sections[chunkY] = destSection
        }

        chunk.forEach { x, y, z ->
            println("Moving block $x $y $z in section $chunkX $chunkY $chunkZ")

            val blockState = sourceSection.getBlockState(x, y, z)
            destSection.setBlockState(x, y, z, blockState, false)
            sourceSection.setBlockState(x, y, z, Blocks.AIR.defaultBlockState(), false)
        }

        val lights = level.chunkSource.lightEngine

        val pos = SectionPos.of(chunkX, chunkY, chunkZ)

        sourceChunk.markUnsaved()
        destChunk.markUnsaved()

        lights.updateSectionStatus(pos, false)
        lights.lightChunk(sourceChunk, false) // todo what does this flag do lol

        lights.updateSectionStatus(SectionPos.of(destChunk.pos.x, pos.y, destChunk.pos.z), false)
        lights.lightChunk(destChunk, false)
    }
}
