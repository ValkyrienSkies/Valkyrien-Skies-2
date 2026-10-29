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
import org.valkyrienskies.core.internal.physics.VsiFluidSourceObservation
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawalResult
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.dimensionId
import java.math.BigDecimal

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
        val liquidId = MassDatapackResolver.getLiquidStateId(state)
        val knownEmpty = liquidId == null && state.fluidState.isEmpty &&
            MassDatapackResolver.getBlockStateType(state) != null
        validateSource(
            withdrawal,
            level.getShipManagingPos(pos)?.id,
            liquidId,
            knownEmpty
        )?.let { return it }

        val sources = FractionalFluidSources.forLevel(level)
        val fluid = FractionalFluidSources.fluidName(state)
            ?: return VsiFluidSourceWithdrawalResult.UNSUPPORTED_SOURCE
        val available = sources?.amount(level.dimensionId, pos.x, pos.y, pos.z, fluid)
            ?: FractionalFluidSources.FULL_SOURCE
        val remaining = available.subtract(BigDecimal.valueOf(withdrawal.fillAmount.toLong()))
        if (remaining.signum() < 0) return VsiFluidSourceWithdrawalResult.STALE_SOURCE
        val waterlogged = state.hasProperty(BlockStateProperties.WATERLOGGED) &&
            state.getValue(BlockStateProperties.WATERLOGGED)
        if (remaining.signum() > 0) {
            if (!waterlogged && state.block !is LiquidBlock) return VsiFluidSourceWithdrawalResult.UNSUPPORTED_SOURCE
            return if (sources?.setAmount(level.dimensionId, pos.x, pos.y, pos.z, fluid, remaining) == true) {
                VsiFluidSourceWithdrawalResult.APPLIED
            } else VsiFluidSourceWithdrawalResult.RETRY_LATER
        }

        if (waterlogged) {
            return if (
                level.setBlock(
                    pos,
                    state.setValue(BlockStateProperties.WATERLOGGED, false),
                    Block.UPDATE_ALL
                )
            ) {
                sources?.reconcileFluid(level.dimensionId, pos.x, pos.y, pos.z, null)
                VsiFluidSourceWithdrawalResult.APPLIED
            } else {
                VsiFluidSourceWithdrawalResult.RETRY_LATER
            }
        }

        val block = state.block
        if (available < FractionalFluidSources.FULL_SOURCE && block is LiquidBlock) {
            return if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                sources?.reconcileFluid(level.dimensionId, pos.x, pos.y, pos.z, null)
                VsiFluidSourceWithdrawalResult.APPLIED
            } else VsiFluidSourceWithdrawalResult.RETRY_LATER
        }
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

    /** Read-only: a completed debit must not be retried merely to recover its voxel publication. */
    @JvmStatic
    fun observe(level: ServerLevel?, withdrawal: VsiFluidSourceWithdrawal): VsiFluidSourceObservation? =
        observe(level, withdrawal.sourcePositionX, withdrawal.sourcePositionY, withdrawal.sourcePositionZ)

    @JvmStatic
    fun observe(level: ServerLevel?, x: Int, y: Int, z: Int): VsiFluidSourceObservation? {
        if (level == null) return null
        val pos = BlockPos(x, y, z)
        if (!level.hasChunkAt(pos)) return null
        val state = level.getBlockState(pos)
        val blockType = MassDatapackResolver.getBlockStateType(state) ?: return null
        val sources = if (state.fluidState.isEmpty) null else
            FractionalFluidSources.forLevel(level)?.takeIf { it.size != 0 }
        val fluid = if (sources == null) null else FractionalFluidSources.fluidName(state)
        val amount = if (fluid == null) FULL_VOXEL_FILL else
            sources!!.sourceFillAmount(level.dimensionId, x, y, z, fluid)
        return VsiFluidSourceObservation(level.getShipManagingPos(pos)?.id, blockType, amount)
    }

    internal fun validateSource(
        withdrawal: VsiFluidSourceWithdrawal,
        currentOwnerBodyId: ShipId?,
        currentFluidId: Int?,
        knownEmpty: Boolean = false
    ): VsiFluidSourceWithdrawalResult? {
        val ownerMatches = if (withdrawal.sourceIsWorldBody) {
            currentOwnerBodyId == null
        } else {
            currentOwnerBodyId == withdrawal.sourceBodyId
        }
        if (!ownerMatches) return VsiFluidSourceWithdrawalResult.STALE_SOURCE
        // Null mappings alone can also mean an unsupported/unknown fluid.
        // Only a known block with an empty Minecraft fluid state proves removal.
        if (knownEmpty && currentFluidId == null) return VsiFluidSourceWithdrawalResult.EMPTY_SOURCE
        if (currentFluidId != withdrawal.fluidId) return VsiFluidSourceWithdrawalResult.STALE_SOURCE
        if (withdrawal.fillAmount !in 1..FULL_VOXEL_FILL) {
            return VsiFluidSourceWithdrawalResult.UNSUPPORTED_SOURCE
        }
        return null
    }
}
