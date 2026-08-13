package org.valkyrienskies.mod.common.fluid

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawal
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawalResult
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.getShipManagingPos

object FluidSourceWithdrawalHandler {
    private const val FULL_VOXEL_FILL = 255

    @JvmStatic
    fun apply(
        level: ServerLevel?,
        withdrawal: VsiFluidSourceWithdrawal
    ): VsiFluidSourceWithdrawalResult {
        if (level == null) {
            return VsiFluidSourceWithdrawalResult.RETRY_LATER
        }

        val pos = BlockPos(
            withdrawal.sourcePositionX,
            withdrawal.sourcePositionY,
            withdrawal.sourcePositionZ
        )
        if (!level.hasChunkAt(pos)) {
            return VsiFluidSourceWithdrawalResult.RETRY_LATER
        }

        val state = level.getBlockState(pos)
        validateSource(
            withdrawal,
            level.getShipManagingPos(pos)?.id,
            MassDatapackResolver.getLiquidStateId(state)
        )?.let { return it }

        if (state.hasProperty(BlockStateProperties.WATERLOGGED) &&
            state.getValue(BlockStateProperties.WATERLOGGED)
        ) {
            return if (
                level.setBlock(
                    pos,
                    state.setValue(BlockStateProperties.WATERLOGGED, false),
                    Block.UPDATE_ALL
                )
            ) {
                VsiFluidSourceWithdrawalResult.APPLIED
            } else {
                VsiFluidSourceWithdrawalResult.RETRY_LATER
            }
        }

        val block = state.block
        if (block is BucketPickup) {
            val pickedUp = block.pickupBlock(level, pos, state)
            if (!pickedUp.isEmpty) {
                return VsiFluidSourceWithdrawalResult.APPLIED
            }
        }

        if (block is LiquidBlock && !state.fluidState.isEmpty) {
            return if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                VsiFluidSourceWithdrawalResult.APPLIED
            } else {
                VsiFluidSourceWithdrawalResult.RETRY_LATER
            }
        }

        return VsiFluidSourceWithdrawalResult.UNSUPPORTED_SOURCE
    }

    internal fun validateSource(
        withdrawal: VsiFluidSourceWithdrawal,
        currentOwnerBodyId: ShipId?,
        currentFluidId: Int?
    ): VsiFluidSourceWithdrawalResult? {
        val ownerMatches = if (withdrawal.sourceIsWorldBody) {
            currentOwnerBodyId == null
        } else {
            currentOwnerBodyId == withdrawal.sourceBodyId
        }
        if (!ownerMatches || currentFluidId != withdrawal.fluidId) {
            return VsiFluidSourceWithdrawalResult.STALE_SOURCE
        }
        if (withdrawal.fillAmount != FULL_VOXEL_FILL) {
            return VsiFluidSourceWithdrawalResult.UNSUPPORTED_SOURCE
        }
        return null
    }
}
