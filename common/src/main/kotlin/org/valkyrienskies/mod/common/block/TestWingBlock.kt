package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.DOWN
import net.minecraft.core.Direction.EAST
import net.minecraft.core.Direction.NORTH
import net.minecraft.core.Direction.SOUTH
import net.minecraft.core.Direction.UP
import net.minecraft.core.Direction.WEST
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.valkyrienskies.core.api.ships.Wing
import org.valkyrienskies.mod.common.util.toJOMLD

object TestWingBlock :
    DirectionalBlock(
        Properties.of().mapColor(MapColor.METAL).strength(10.0f, 1200.0f).sound(SoundType.WOOL).requiresCorrectToolForDrops()
    ), WingBlock {

    private val EAST_AABB = box(4.0, 0.0, 0.0, 12.0, 16.0, 16.0)
    private val WEST_AABB = box(4.0, 0.0, 0.0, 12.0, 16.0, 16.0)
    private val SOUTH_AABB = box(0.0, 0.0, 4.0, 16.0, 16.0, 12.0)
    private val NORTH_AABB = box(0.0, 0.0, 4.0, 16.0, 16.0, 12.0)
    private val UP_AABB =  box(0.0, 4.0, 0.0, 16.0, 12.0, 16.0)
    private val DOWN_AABB = box(0.0, 4.0, 0.0, 16.0, 12.0, 16.0)

    init {
        registerDefaultState(this.stateDefinition.any().setValue(FACING, UP))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue(FACING, ctx.nearestLookingDirection.opposite)
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(
        state: BlockState, blockGetter: BlockGetter, blockPos: BlockPos, collisionContext: CollisionContext
    ): VoxelShape {
        when (state.getValue(FACING)) {
            DOWN -> {
                return DOWN_AABB
            }
            NORTH -> {
                return NORTH_AABB
            }
            SOUTH -> {
                return SOUTH_AABB
            }
            WEST -> {
                return WEST_AABB
            }
            EAST -> {
                return EAST_AABB
            }
            UP -> {
                return UP_AABB
            }
            else -> {
                // This should be impossible, but have this here just in case
                return UP_AABB
            }
        }
    }

    override fun getWing(level: Level?, pos: BlockPos?, blockState: BlockState): Wing {
        val wingPower = 150.0
        val wingDrag = 30.0
        val wingBreakingForce = null
        val wingCamberAttackAngleBias = Math.toRadians(10.0)
        return Wing(
            blockState.getValue(FACING).normal.toJOMLD(), wingPower, wingDrag, wingBreakingForce,
            wingCamberAttackAngleBias
        )
    }
}
