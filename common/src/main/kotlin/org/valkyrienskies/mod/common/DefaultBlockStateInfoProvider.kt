package org.valkyrienskies.mod.common

import net.minecraft.block.BlockState
import net.minecraft.block.FluidBlock
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.core.game.VSBlockType.AIR
import org.valkyrienskies.core.game.VSBlockType.SOLID
import org.valkyrienskies.core.game.VSBlockType.WATER
import org.valkyrienskies.mod.mixin.accessors.block.AbstractBlockAccessor

object DefaultBlockStateInfoProvider : BlockStateInfoProvider {
    override val priority: Int
        get() = Int.MIN_VALUE

    override fun getBlockStateMass(blockState: BlockState): Double {
        if (blockState.isAir) return 0.0
        return 100.0
    }

    override fun getBlockStateType(blockState: BlockState): VSBlockType {
        return when {
            blockState.isAir -> {
                AIR
            }
            !((blockState.block as AbstractBlockAccessor).isCollidable) -> {
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
}
