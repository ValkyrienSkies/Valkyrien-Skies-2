package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.ShipPathfindingUtils;

@Mixin(PathNavigationRegion.class)
public class MixinPathNavigationRegion {

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void vs$useShipBlocksForPathfinding(final BlockPos blockPos,
        final CallbackInfoReturnable<BlockState> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips() || !cir.getReturnValue().isAir()) {
            return;
        }

        final Level level = ((PathNavigationRegionAccessor) this).getLevel();
        if (level == null) {
            return;
        }

        final BlockPos shipPos = ShipPathfindingUtils.findExactShipBlock(level, blockPos);
        if (shipPos != null) {
            cir.setReturnValue(level.getBlockState(shipPos));
        }
    }

    @Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true)
    private void vs$useShipFluidsForPathfinding(final BlockPos blockPos,
        final CallbackInfoReturnable<FluidState> cir) {
        if (!VSGameConfig.SERVER.getAiOnShips() || !cir.getReturnValue().isEmpty()) {
            return;
        }

        final Level level = ((PathNavigationRegionAccessor) this).getLevel();
        if (level == null) {
            return;
        }

        final FluidState shipFluid = ShipPathfindingUtils.findExactShipFluid(level, blockPos);
        if (shipFluid != null && !shipFluid.isEmpty()) {
            cir.setReturnValue(shipFluid);
        }
    }
}
