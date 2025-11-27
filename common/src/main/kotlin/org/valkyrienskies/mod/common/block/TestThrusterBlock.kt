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
import net.minecraft.world.level.block.RedstoneLampBlock.LIT
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import org.valkyrienskies.mod.common.blockentity.DebugPhysicsTickables
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.blockentity.TestThrusterBlockEntity
import org.valkyrienskies.mod.common.getLoadedShipManagingPos

object TestThrusterBlock : DirectionalBlock(Properties.of().strength(10.0f, 1200.0f).sound(SoundType.METAL)), EntityBlock {

    init {
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH))
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState? {
        return this.defaultBlockState().setValue(
            LIT, blockPlaceContext.level.hasNeighborSignal(blockPlaceContext.clickedPos)
        ).setValue(
            FACING, if (blockPlaceContext.player != null && blockPlaceContext.player!!.isCrouching) blockPlaceContext.nearestLookingDirection.opposite else blockPlaceContext.horizontalDirection
        )
    }

    override fun neighborChanged(
        blockState: BlockState, level: Level, blockPos: BlockPos, block: Block, blockPos2: BlockPos, bl: Boolean
    ) {
        if (!level.isClientSide) {
            val bl2 = blockState.getValue<Boolean?>(LIT) as Boolean
            if (bl2 != level.hasNeighborSignal(blockPos)) {
                if (bl2) {
                    level.scheduleTick(blockPos, this, 4)
                } else {
                    level.setBlock(blockPos, blockState.cycle<Boolean>(LIT) as BlockState, 2)
                }
            }
        }
    }

    override fun tick(blockState: BlockState, serverLevel: ServerLevel, blockPos: BlockPos, randomSource: RandomSource) {
        if (blockState.getValue<Boolean>(LIT) && !serverLevel.hasNeighborSignal(blockPos)) {
            serverLevel.setBlock(blockPos, blockState.cycle<Boolean>(LIT) as BlockState, 2)
        }
        if (serverLevel.getBlockEntity(blockPos) is TestThrusterBlockEntity) {
            val blockEntity = serverLevel.getBlockEntity(blockPos) as TestThrusterBlockEntity
            if (!blockEntity.added) {
                blockEntity.added = true
                DebugPhysicsTickables.add(blockEntity)
            }
            blockEntity.isActive = blockState.getValue<Boolean>(LIT) as Boolean
            blockEntity.shipMountedTo = serverLevel.getLoadedShipManagingPos(blockPos)?.id ?: -1L
        }
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder
            .add(LIT)
            .add(FACING)
    }

    override fun newBlockEntity(
        blockPos: BlockPos, blockState: BlockState
    ): BlockEntity? {
        return TestThrusterBlockEntity(blockPos, blockState)
    }
}
