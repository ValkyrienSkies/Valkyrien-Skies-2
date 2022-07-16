package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Container
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK
import org.valkyrienskies.core.api.Ship
import org.valkyrienskies.mod.api.ShipBlockEntity

private val AIR = Blocks.AIR.defaultBlockState()

// the from and the to can be local or global
fun relocateBlock(fromChunk: LevelChunk, from: BlockPos, toChunk: LevelChunk, to: BlockPos, toShip: Ship) {
    val state = fromChunk.getBlockState(from)
    val entity = fromChunk.getBlockEntity(from)

    val tag = entity?.let {
        val tag = CompoundTag()
        it.save(tag)
        tag.putInt("x", to.x)
        tag.putInt("y", to.y)
        tag.putInt("z", to.z)

        // so that it won't drop its contents
        if (it is Container) {
            it.clearContent()
        }

        tag
    }

    val level = toChunk.level

    fromChunk.setBlockState(from, AIR, false)
    toChunk.setBlockState(to, state, false) // TODO should isMoving be false?
    level.sendBlockUpdated(from, state, AIR, 11)

    tag?.let {
        val be = toChunk.getBlockEntity(to, CHECK)!!
        if (be is ShipBlockEntity)
            be.ship = toShip

        be.load(state, it)
    }
}

fun Level.relocateBlock(from: BlockPos, to: BlockPos, toShip: Ship) =
    relocateBlock(getChunkAt(from), from, getChunkAt(to), to, toShip)
