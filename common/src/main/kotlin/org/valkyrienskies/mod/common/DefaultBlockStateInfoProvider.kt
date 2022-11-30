package org.valkyrienskies.mod.common

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import org.valkyrienskies.core.api.world.chunks.BlockType

object DefaultBlockStateInfoProvider : BlockStateInfoProvider {
    override val priority: Int
        get() = Int.MIN_VALUE

    override fun getBlockStateMass(blockState: BlockState): Double {
        if (blockState.isAir) return 0.0
        return 100.0
    }

    override fun getBlockStateType(blockState: BlockState): BlockType {
        if (blockState.isAir) return vsCore.blockTypes.air

        val blockMaterial = blockState.material
        if (blockMaterial.isLiquid)
            return if (blockMaterial == Material.LAVA) vsCore.blockTypes.lava else vsCore.blockTypes.water
        return if (blockMaterial.isSolid) vsCore.blockTypes.solid else vsCore.blockTypes.air
    }
}
