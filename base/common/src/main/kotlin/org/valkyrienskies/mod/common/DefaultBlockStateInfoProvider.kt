package org.valkyrienskies.mod.common

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.core.apigame.physics.blockstates.VsBlockState
import org.valkyrienskies.core.apigame.world.chunks.BlockType
object DefaultBlockStateInfoProvider : BlockStateInfoProvider {
    override val priority: Int
        get() = Int.MIN_VALUE

    override val solidBlockStates: List<SolidState>
        get() = TODO("Not yet implemented")
    override val liquidBlockStates: List<LiquidState>
        get() = TODO("Not yet implemented")
    override val blockStateData: List<VsBlockState>
        get() = TODO("Not yet implemented")

    override fun getBlockStateMass(blockState: BlockState): Double {
        if (blockState.isAir) return 0.0
        // By default make blocks weight 1000 kg
        return 1000.0
    }

    override fun getBlockStateType(blockState: BlockState): BlockType {
        if (blockState.isAir) return vsCore.blockTypes.air

        val blockMaterial = blockState.material
        if (blockMaterial.isLiquid)
            return if (blockMaterial == Material.LAVA) vsCore.blockTypes.lava else vsCore.blockTypes.water
        return if (blockMaterial.isSolid) vsCore.blockTypes.solid else vsCore.blockTypes.air
    }


}
