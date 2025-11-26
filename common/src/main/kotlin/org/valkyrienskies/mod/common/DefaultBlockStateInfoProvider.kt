package org.valkyrienskies.mod.common

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.config.VSGameConfig

object DefaultBlockStateInfoProvider : BlockStateInfoProvider {
    override val priority: Int
        get() = Int.MIN_VALUE

    override fun getBlockStateMass(blockState: BlockState): Double {
        if (blockState.isAir) return 0.0
        return VSGameConfig.SERVER.defaultBlockDensity;
    }

    override fun getBlockStateElasticity(blockState: BlockState): Double = VSGameConfig.SERVER.defaultBlockElasticity

    override fun getBlockStateFriction(blockState: BlockState): Double = VSGameConfig.SERVER.defaultBlockFriction

    override fun getBlockStateHardness(blockState: BlockState): Double = VSGameConfig.SERVER.defaultBlockHardness

    override fun getBlockStateType(blockState: BlockState): VsiBlockType {
        if (blockState.isAir) return vsCore.blockTypes.air

        val block = blockState.block
        if (block is LiquidBlock)
            return if (block == Blocks.LAVA) vsCore.blockTypes.lava else vsCore.blockTypes.water
        return if (blockState.isSolid) vsCore.blockTypes.solid else vsCore.blockTypes.air
    }
}
