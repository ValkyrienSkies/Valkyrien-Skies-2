package org.valkyrienskies.mod.common

import net.minecraft.block.BlockState
import net.minecraft.block.FluidBlock
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.core.game.VSBlockType.AIR
import org.valkyrienskies.core.game.VSBlockType.SOLID
import org.valkyrienskies.core.game.VSBlockType.WATER

object BlockStateInfoProvider {
    private fun getBlockStateMass(blockState: BlockState): Double {
        if (blockState.isAir) return 0.0
        return 100.0
    }

    private fun getBlockStateType(blockState: BlockState): VSBlockType {
        return when {
            blockState.isAir -> {
                AIR
            }
            blockState.block is FluidBlock -> {
                WATER
            }
            else -> {
                SOLID
            }
        }
    }

    fun onSetBlock(world: World, blockPos: BlockPos, prevBlockState: BlockState, newBlockState: BlockState) {
        val shipObjectWorld = world.shipObjectWorld

        val prevBlockMass = getBlockStateMass(prevBlockState)
        val prevBlockType = getBlockStateType(prevBlockState)

        val newBlockMass = getBlockStateMass(newBlockState)
        val newBlockType = getBlockStateType(newBlockState)

        shipObjectWorld.onSetBlock(
            blockPos.x, blockPos.y, blockPos.z, prevBlockType, newBlockType, prevBlockMass, newBlockMass
        )
    }
}
