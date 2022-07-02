package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk

// the from and the to can be local or global
fun relocateBlock(fromChunk: LevelChunk, fromP: BlockPos, toChunk: LevelChunk, toP: BlockPos) {
    val from = fromP.localize()
    val to = toP.localize()

    val state = fromChunk.getBlockState(from)
    val entity = fromChunk.getBlockEntity(from)

    toChunk.setBlockState(to, state, false) // TODO should isMoving be false?
    entity?.let {
        it.setLevelAndPosition(toChunk.level, to)
        fromChunk.blockEntities.remove(from)
        toChunk.addBlockEntity(it)
    }
}

fun Level.relocateBlock(from: BlockPos, to: BlockPos) =
    relocateBlock(getChunkAt(from), from, getChunkAt(to), to)

fun BlockPos.localize() = BlockPos(x and 15, y, z and 15)
