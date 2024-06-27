package org.valkyrienskies.mod.common

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.apigame.world.chunks.BlockType

object DefaultBlockStateInfoProvider : BlockStateInfoProvider {
    override val priority: Int
        get() = Int.MIN_VALUE

    override fun getBlockStateMass(blockState: BlockState): Double {
        if (blockState.isAir) return 0.0
        // By default make blocks weight 1000 kg
        return 1000.0
    }

    override fun getBlockStateType(blockState: BlockState): BlockType {
        if (blockState.isAir) return vsCore.blockTypes.air

        val block = blockState.block
        if (block is LiquidBlock)
            return if (block == Blocks.LAVA) vsCore.blockTypes.lava else vsCore.blockTypes.water
        return if (blockState.material.isSolid) vsCore.blockTypes.solid else vsCore.blockTypes.air
    }
}
