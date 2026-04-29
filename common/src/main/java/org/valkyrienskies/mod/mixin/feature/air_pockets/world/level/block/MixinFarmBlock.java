package org.valkyrienskies.mod.mixin.feature.air_pockets.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;

@Mixin(FarmBlock.class)
public abstract class MixinFarmBlock {

    @org.spongepowered.asm.mixin.Unique
    private static final FluidState vs$EMPTY = Fluids.EMPTY.defaultFluidState();

    @Inject(
        method = "isNearWater(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void valkyrienair$ignoreWorldWaterMoistureInShipAirPockets(final LevelReader levelReader,
        final BlockPos pos, final CallbackInfoReturnable<Boolean> cir) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;
        if (!(levelReader instanceof final Level level)) return;

        if (!ShipWaterPocketManager.isWorldPosInShipAirPocket(level, pos)) return;

        final BlockPos from = pos.offset(-4, 0, -4);
        final BlockPos to = pos.offset(4, 1, 4);

        for (final BlockPos checkPos : BlockPos.betweenClosed(from, to)) {
            final FluidState shipFluidOnly =
                ShipWaterPocketManager.overrideWaterFluidState(level, checkPos, vs$EMPTY);
            if (!shipFluidOnly.isEmpty() && shipFluidOnly.is(FluidTags.WATER)) {
                cir.setReturnValue(true);
                return;
            }
        }

        cir.setReturnValue(false);
    }
}

