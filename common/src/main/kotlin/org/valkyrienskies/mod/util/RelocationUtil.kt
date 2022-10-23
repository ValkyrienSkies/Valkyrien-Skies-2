package org.valkyrienskies.mod.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.NORTH
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Container
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK
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
 * @param direction Direction.NORTH is no change in direction, Direction.EAST is 90 degrees clockwise, etc.
 */
fun relocateBlock(
    fromChunk: LevelChunk, from: BlockPos, toChunk: LevelChunk, to: BlockPos, toShip: ServerShip?,
    direction: Direction = NORTH
) {
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

    // region BlockState rotation
    fun addDirection(direction1: Direction, direction2: Direction) =
        Direction.from2DDataValue((direction1.get2DDataValue() + direction2.get2DDataValue()) and 3)

    // TODO there are prob more relevant states that need to get modified
    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
        state.setValue(
            BlockStateProperties.HORIZONTAL_FACING,
            addDirection(state.getValue(BlockStateProperties.HORIZONTAL_FACING), direction)
        )
    } else if (state.hasProperty(BlockStateProperties.FACING)) {
        state.setValue(
            BlockStateProperties.FACING,
            addDirection(state.getValue(BlockStateProperties.FACING), direction)
        )
    } else if (state.hasProperty(BlockStateProperties.AXIS)) {
        state.setValue(
            BlockStateProperties.AXIS,
            if (direction.axis == Direction.Axis.X)
                if (state.getValue(BlockStateProperties.AXIS) == Direction.Axis.X)
                    Direction.Axis.Z
                else if (state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Z)
                    Direction.Axis.X
                else
                    Direction.Axis.Y
            else
                state.getValue(BlockStateProperties.AXIS)
        )
    } else if (state.hasProperty(BlockStateProperties.FACING_HOPPER)) {
        state.setValue(
            BlockStateProperties.FACING,
            addDirection(state.getValue(BlockStateProperties.FACING), direction)
        )
    }
    // endregion

    val level = toChunk.level

    // TODO review this set code, i might just need to use everywhere level#setBlock
    fromChunk.setBlockState(from, AIR, false)
    level.setBlock(to, state, 11)
    level.sendBlockUpdated(from, state, AIR, 11)
    level.chunkSource.lightEngine.checkBlock(from)

    tag?.let {
        val be = toChunk.getBlockEntity(to, CHECK)!!
        if (be is ShipBlockEntity)
            be.ship = toShip

        be.load(state, it)
    }
}

/**
 * Relocate block
 *
 * @param from coordinate (can be local or global coord)
 * @param to coordinate (can be local or global coord)
 * @param toShip should be set when you're relocating to a ship
 * @param direction Direction.NORTH is no change in direction, Direction.EAST is 90 degrees clockwise, etc.
 */
fun Level.relocateBlock(from: BlockPos, to: BlockPos, toShip: ServerShip?, direction: Direction) =
    relocateBlock(getChunkAt(from), from, getChunkAt(to), to, toShip, direction)
