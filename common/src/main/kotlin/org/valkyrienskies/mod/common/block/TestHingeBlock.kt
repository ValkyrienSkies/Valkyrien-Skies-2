package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity

object TestHingeBlock :
    HorizontalDirectionalBlock(
        Properties.of(Material.METAL).strength(10.0f, 1200.0f).sound(SoundType.METAL)
    ), EntityBlock {
    private val SEAT_AABB: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)

    init {
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        return defaultBlockState()
            .setValue(FACING, ctx.horizontalDirection.opposite)
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(
        state: BlockState, level: BlockGetter?, pos: BlockPos?, context: CollisionContext?
    ): VoxelShape = SEAT_AABB

    @Deprecated("Deprecated in Java")
    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        blockHitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS

        return InteractionResult.CONSUME
    }

    override fun newBlockEntity(blockPos: BlockPos, blockState: BlockState): TestHingeBlockEntity =
        TestHingeBlockEntity(blockPos, blockState)

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = BlockEntityTicker { _, _, _, blockEntity ->
        if (level.isClientSide) return@BlockEntityTicker
        if (blockEntity is TestHingeBlockEntity) {
            blockEntity.tick()
        }
    }
}
