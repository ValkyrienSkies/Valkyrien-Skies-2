package org.valkyrienskies.mod.common.block

import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.BlockHitResult
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.util.toDoubles

object TestChairBlock :
    HorizontalDirectionalBlock(
        Properties.of(Material.METAL).strength(5.0f, 1200.0f).sound(SoundType.ANVIL)
    ) {
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
    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        blockHitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val seatEntity = ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE.create(level)!!.apply {
            val seatEntityPos: Vector3dc = Vector3d(pos.x + .5, pos.y + .5, pos.z + .5)
            inShipPosition = seatEntityPos
            val ship = level.getShipManagingPos(pos)
            if (ship != null) {
                val posInWorld = ship.shipTransform.shipToWorldMatrix.transformPosition(seatEntityPos, Vector3d())
                moveTo(posInWorld.x, posInWorld.y, posInWorld.z)
            } else {
                moveTo(seatEntityPos.x(), seatEntityPos.y(), seatEntityPos.z())
            }
            lookAt(EntityAnchorArgument.Anchor.EYES, state.getValue(FACING).normal.toDoubles().add(position()))
            isController = true
        }

        level.addFreshEntity(seatEntity)
        player.startRiding(seatEntity)
        return InteractionResult.CONSUME
    }
}
