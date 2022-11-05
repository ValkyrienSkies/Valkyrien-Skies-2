package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.NORTH
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.Material
import org.valkyrienskies.mod.client.ClipPlane
import org.valkyrienskies.mod.client.ClipPlanesHandler
import org.valkyrienskies.mod.common.util.toJOMLD

object ClippingTestBlock : HorizontalDirectionalBlock(
    Properties.of(Material.METAL)
        .strength(5.0f, 1200.0f)
        .sound(SoundType.ANVIL)
) {
    init {
        registerDefaultState(
            this.stateDefinition.any().setValue(FACING, NORTH)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, ctx.horizontalDirection.opposite)

    // TODO doesn't get called on client.................... why?
    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        if (!level.isClientSide) return
        
        ClipPlanesHandler.clipPlanes.add(ClipPlane(pos.toJOMLD(), state.getValue(FACING).normal.toJOMLD(), 3.0, 3.0))
    }
}
