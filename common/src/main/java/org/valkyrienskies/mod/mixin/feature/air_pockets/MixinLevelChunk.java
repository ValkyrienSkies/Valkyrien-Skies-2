package org.valkyrienskies.mod.mixin.feature.air_pockets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {

    @Shadow
    @Final
    private Level level;

    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void vs$markShipWaterPocketDirtyOnChunkSetBlock(final BlockPos pos, final BlockState state,
        final boolean isMoving, final CallbackInfoReturnable<BlockState> cir) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;
        if (level.isClientSide) return;
        if (ShipWaterPocketManager.isApplyingInternalUpdates()) return;

        final BlockState previousState = cir.getReturnValue();
        if (previousState == null || previousState.equals(state)) return;
        if (!VSGameUtilsKt.isBlockInShipyard(level, pos)) return;

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) return;

        final boolean geometryDirty = ShipWaterPocketManager.shouldMarkShipGeometryDirtyForBlockChange(
            level,
            pos,
            previousState,
            state
        );

        final FluidState placedFluid = state.getFluidState();
        final FluidState previousFluid = previousState.getFluidState();
        if (!placedFluid.isEmpty() && (previousFluid.isEmpty() || previousFluid.getType() != placedFluid.getType())) {
            ShipWaterPocketManager.onExternalShipFluidPlacement(level, ship.getId(), pos, placedFluid.getType());
        }
        if (geometryDirty) {
            ShipWaterPocketManager.markShipDirty(level, ship.getId());
        }
    }
}
