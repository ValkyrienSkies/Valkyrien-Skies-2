package org.valkyrienskies.mod.common

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.core.game.VSBlockType.AIR
import org.valkyrienskies.core.game.VSBlockType.LAVA
import org.valkyrienskies.core.game.VSBlockType.SOLID
import org.valkyrienskies.core.game.VSBlockType.WATER

object DefaultBlockStateInfoProvider : BlockStateInfoProvider {
    override val priority: Int
        get() = Int.MIN_VALUE

    override fun getBlockStateMass(blockState: BlockState): Double {
        if (blockState.isAir) return 0.0
        return 100.0
    }

    override fun getBlockStateType(blockState: BlockState): VSBlockType {
        if (blockState.isAir) return AIR

        val blockMaterial = blockState.material
        if (blockMaterial.isLiquid)
            return if (blockMaterial == Material.LAVA) LAVA else WATER
        return if (blockMaterial.isSolid) SOLID else AIR
    }
}
