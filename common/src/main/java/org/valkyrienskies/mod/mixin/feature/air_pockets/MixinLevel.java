package org.valkyrienskies.mod.mixin.feature.air_pockets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;

@Mixin(Level.class)
public abstract class MixinLevel {

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void valkyrienair$redirectFirePlacementToShipyardInAirPockets(final BlockPos pos, final BlockState state,
        final int flags, final int recursionLeft, final CallbackInfoReturnable<Boolean> cir) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;

        final Level level = Level.class.cast(this);
        if (level.isClientSide) return;
        if (ShipWaterPocketManager.isApplyingInternalUpdates()) return;
        if (VSGameUtilsKt.isBlockInShipyard(level, pos)) return;

        if (!state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)) return;

        final BlockPos shipPos = ShipWaterPocketManager.getShipBlockPosForWorldPosInShipAirPocket(level, pos);
        if (shipPos == null) return;
        if (shipPos.equals(pos)) return;
        if (!VSGameUtilsKt.isBlockInShipyard(level, shipPos)) return;

        final BlockState existing = level.getBlockState(shipPos);
        if (!existing.isAir() && !existing.is(state.getBlock())) {
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        final boolean ok = level.setBlock(shipPos, state, flags, recursionLeft);
        cir.setReturnValue(ok);
        cir.cancel();
    }

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void valkyrienair$blockShipyardWaterFromLeakingIntoWorldWater(final BlockPos pos, final BlockState state,
        final int flags, final int recursionLeft, final CallbackInfoReturnable<Boolean> cir) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;

        final Level level = Level.class.cast(this);
        if (level.isClientSide) return;
        if (ShipWaterPocketManager.isApplyingInternalUpdates()) return;
        if (!VSGameUtilsKt.isBlockInShipyard(level, pos)) return;

        final BlockState existing = level.getBlockState(pos);
        final var fluidState = state.getFluidState();
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) &&
            state.getValue(BlockStateProperties.WATERLOGGED) &&
            existing.hasProperty(BlockStateProperties.WATERLOGGED) &&
            !existing.getValue(BlockStateProperties.WATERLOGGED)
        ) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
            if (ship != null && ShipWaterPocketManager.shouldBlockShipyardWaterPlacement(level, ship.getId(), pos)) {
                if (ShipWaterPocketManager.shouldAllowDirectExternalShipyardFluidPlacement(
                    level,
                    ship.getId(),
                    pos,
                    fluidState
                )) {
                    return;
                }
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }
        }

        if (fluidState.isEmpty()) return;
        if (!(state.getBlock() instanceof LiquidBlock)) return;

        // Allow vanilla/modded "fluid-in-block" transitions (e.g. breaking/replacing waterlogged blocks) to convert
        // into a liquid block at the same position. Blocking these makes waterlogged blocks effectively unbreakable
        // while submerged because the replacement liquid setBlock() gets cancelled.
        final FluidState existingFluid = existing.getFluidState();
        if (!(existing.getBlock() instanceof LiquidBlock) && !existingFluid.isEmpty()) {
            if (existing.getBlock() instanceof BucketPickup || existing.getBlock() instanceof LiquidBlockContainer) {
                return;
            }
            if (!existing.hasProperty(BlockStateProperties.WATERLOGGED) || existing.getValue(BlockStateProperties.WATERLOGGED)) {
                return;
            }
        }

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) return;

        if (ShipWaterPocketManager.shouldBlockShipyardWaterPlacement(level, ship.getId(), pos)) {
            if (ShipWaterPocketManager.shouldAllowDirectExternalShipyardFluidPlacement(
                level,
                ship.getId(),
                pos,
                fluidState
            )) {
                return;
            }
            if (ShipWaterPocketManager.shouldAllowImmediateFragileShipyardFloodPlacement(
                level,
                ship.getId(),
                pos,
                fluidState.getType(),
                existing
            )) {
                return;
            }
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("TAIL")
    )
    private void vs$markShipWaterPocketDirty(final BlockPos pos, final BlockState state, final int flags,
        final int recursionLeft, final CallbackInfoReturnable<Boolean> cir) {
        // Dirty tracking for block changes is handled in MixinLevelChunk where we can compare old/new state
        // and filter out non-structural liquid churn that causes geometry invalidation spam.
    }

    @Inject(
        method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void valkyrienair$overrideFluidStateForModCompat(final BlockPos pos,
        final CallbackInfoReturnable<FluidState> cir) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;

        final Level level = Level.class.cast(this);
        if (ShipWaterPocketManager.isBypassingFluidOverrides()) return;

        final FluidState original = cir.getReturnValue();
        final FluidState overridden = ShipWaterPocketManager.overrideWaterFluidState(level, pos, original);
        if (overridden != original) {
            cir.setReturnValue(overridden);
        }
    }

    @Inject(
        method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void valkyrienair$overrideBlockStateForModCompat(final BlockPos pos,
        final CallbackInfoReturnable<BlockState> cir) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;

        final Level level = Level.class.cast(this);
        if (ShipWaterPocketManager.isBypassingFluidOverrides()) return;
        if (VSGameUtilsKt.isBlockInShipyard(level, pos)) return;

        final BlockState original = cir.getReturnValue();
        final FluidState originalFluid = original.getFluidState();
        if (originalFluid.isEmpty()) return;

        if (ShipWaterPocketManager.isWorldPosInShipWorldFluidSuppressionZone(level, pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
