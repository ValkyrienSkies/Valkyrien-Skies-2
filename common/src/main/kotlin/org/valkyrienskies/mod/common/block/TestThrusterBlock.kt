package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.blockentity.TestThrusterBlockEntity
import org.valkyrienskies.mod.common.getLoadedShipManagingPos

object TestThrusterBlock : DirectionalBlock(Properties.of().strength(10.0f, 1200.0f).sound(SoundType.METAL)), EntityBlock {

    init {
        this.registerDefaultState(this.stateDefinition.any().setValue(BlockStateProperties.POWERED, false).setValue(FACING, Direction.NORTH))
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState? {
        return this.defaultBlockState().setValue(
            BlockStateProperties.POWERED, blockPlaceContext.level.hasNeighborSignal(blockPlaceContext.clickedPos)
        ).setValue(
            FACING, if (blockPlaceContext.player != null && blockPlaceContext.player!!.isCrouching) blockPlaceContext.nearestLookingDirection.opposite else blockPlaceContext.horizontalDirection
        )
    }

    override fun neighborChanged(
        blockState: BlockState, level: Level, blockPos: BlockPos, block: Block, blockPos2: BlockPos, bl: Boolean
    ) {
        if (!level.isClientSide) {
            val previouslyPowered = blockState.getValue<Boolean>(BlockStateProperties.POWERED)
            if (previouslyPowered != level.hasNeighborSignal(blockPos)) {
                level.setBlock(blockPos, blockState.cycle<Boolean>(BlockStateProperties.POWERED) as BlockState, 2)
                //level.scheduleTick(blockPos, this, 2)
            }
            if (level is ServerLevel) {
                if (level.getBlockEntity(blockPos) is TestThrusterBlockEntity) {
                    val blockEntity = level.getBlockEntity(blockPos) as TestThrusterBlockEntity
                    blockEntity.isActive = blockState.getValue(BlockStateProperties.POWERED)
                }
            }
        }
    }

    @OptIn(GameTickOnly::class)
    override fun tick(blockState: BlockState, serverLevel: ServerLevel, blockPos: BlockPos, randomSource: RandomSource) {

    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder
            .add(BlockStateProperties.POWERED)
            .add(FACING)
    }

    override fun newBlockEntity(
        blockPos: BlockPos, blockState: BlockState
    ): BlockEntity? {
        return TestThrusterBlockEntity(blockPos, blockState)
    }
}
