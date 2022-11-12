package org.valkyrienskies.mod.util

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Container
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.Rotation.NONE
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.valkyrienskies.core.api.ServerShip
import org.valkyrienskies.mod.api.ShipBlockEntity

private val AIR = Blocks.AIR.defaultBlockState()

/**
 * Relocate block
 *
 * @param fromChunk
 * @param from coordinate (can be local or global coord)
 * @param toChunk
 * @param to coordinate (can be local or global coord)
 * @param toShip should be set when you're relocating to a ship
 * @param rotation Rotation.NONE is no change in direction, Rotation.CLOCKWISE_90 is 90 degrees clockwise, etc.
 */
fun relocateBlock(
    fromChunk: LevelChunk, from: BlockPos, toChunk: LevelChunk, to: BlockPos, doUpdate: Boolean, toShip: ServerShip?,
    rotation: Rotation = NONE
) {
    val oldState = fromChunk.getBlockState(from)
    var state = fromChunk.getBlockState(from)
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

    state = state.rotate(rotation)

    val level = toChunk.level

    fromChunk.setBlockState(from, AIR, false)
    toChunk.setBlockState(to, state, false)

    if (doUpdate) {
        updateBlock(level, from, to, state)
    }

    tag?.let {
        val be = level.getBlockEntity(to)!!
        if (be is ShipBlockEntity) be.ship = toShip

        be.load(state, it)
    }
}

/**
 * Update block after relocate
 *
 * @param level
 * @param fromPos old position coordinate
 * @param toPos new position coordinate
 * @param toState new blockstate at toPos
 */
fun updateBlock(level: Level, fromPos: BlockPos, toPos: BlockPos, toState: BlockState) {

    // 75 = flag 1 (block update) & flag 2 (send to clients) + flag 8 (force rerenders) + flag 64 (block is moving)
    val flags = 75

    // updateNeighbourShapes recurses through nearby blocks, recursionLeft is the limit
    val recursionLeft = 511

    level.setBlocksDirty(fromPos, toState, AIR)
    level.sendBlockUpdated(fromPos, toState, AIR, flags)
    // This handles the update for neighboring blocks in worldspace
    AIR.updateNeighbourShapes(level, fromPos, flags, recursionLeft)
    AIR.updateIndirectNeighbourShapes(level, fromPos, flags, recursionLeft)
    // This updates lighting for blocks in worldspace
    level.chunkSource.lightEngine.checkBlock(fromPos)

    level.setBlocksDirty(toPos, AIR, toState)
    level.sendBlockUpdated(toPos, AIR, toState, flags)
    // This handles the update for neighboring blocks in shipspace (ladders, redstone)
    toState.updateNeighbourShapes(level, toPos, flags, recursionLeft)
    toState.updateIndirectNeighbourShapes(level, toPos, flags, recursionLeft)
    // This updates lighting for blocks in shipspace
    level.chunkSource.lightEngine.checkBlock(toPos)
}

/**
 * Relocate block
 *
 * @param from coordinate (can be local or global coord)
 * @param to coordinate (can be local or global coord)
 * @param doUpdate update blocks after moving
 * @param toShip should be set when you're relocating to a ship
 * @param rotation Rotation.NONE is no change in direction, Rotation.CLOCKWISE_90 is 90 degrees clockwise, etc.
 */
fun Level.relocateBlock(from: BlockPos, to: BlockPos, doUpdate: Boolean, toShip: ServerShip?, rotation: Rotation) =
    relocateBlock(getChunkAt(from), from, getChunkAt(to), to, doUpdate, toShip, rotation)
