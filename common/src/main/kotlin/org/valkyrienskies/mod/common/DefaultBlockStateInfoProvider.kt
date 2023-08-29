package org.valkyrienskies.mod.common

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import org.valkyrienskies.core.apigame.world.chunks.BlockType
import org.valkyrienskies.physics_api.Lod1BlockStateId
import org.valkyrienskies.physics_api.Lod1LiquidBlockStateId
import org.valkyrienskies.physics_api.Lod1SolidBlockStateId
import org.valkyrienskies.physics_api.voxel.Lod1LiquidBlockState
import org.valkyrienskies.physics_api.voxel.Lod1SolidBlockState

object DefaultBlockStateInfoProvider : BlockStateInfoProvider {
    override val priority: Int
        get() = Int.MIN_VALUE
    override val solidBlockStates: List<Lod1SolidBlockState>
        get() = TODO()
    override val liquidBlockStates: List<Lod1LiquidBlockState>
        get() = TODO()
    override val blockStateData: List<Triple<Lod1SolidBlockStateId, Lod1LiquidBlockStateId, Lod1BlockStateId>>
        get() = TODO()

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
