package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

object TestSphereBlock : Block(Properties.of(Material.STONE)) {
    @Deprecated("Deprecated in Java")
    override fun getVisualShape(
        blockState: BlockState, blockGetter: BlockGetter, blockPos: BlockPos, collisionContext: CollisionContext
    ): VoxelShape {
        return Shapes.empty()
    }

    @Deprecated("Deprecated in Java")
    override fun getShadeBrightness(blockState: BlockState, blockGetter: BlockGetter, blockPos: BlockPos): Float {
        return 1.0f
    }

    override fun propagatesSkylightDown(blockState: BlockState, blockGetter: BlockGetter, blockPos: BlockPos): Boolean {
        return true
    }
}
