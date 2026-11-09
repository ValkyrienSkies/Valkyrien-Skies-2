package org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.fluid.RetainedFarmWater;
import org.valkyrienskies.mod.common.fluid.RetainedFluidBlockInteractions;

@Mixin(FarmBlock.class)
public abstract class MixinFarmBlock {
    @Inject(method = "isNearWater", at = @At("RETURN"), cancellable = true)
    private static void vs$hydrateFromRetainedWater(final LevelReader level, final BlockPos pos,
        final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && RetainedFarmWater.hydrates(level, pos)) cir.setReturnValue(true);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void vs$absorbShallowWater(final BlockState state, final ServerLevel level, final BlockPos pos,
        final RandomSource random, final CallbackInfo ci) {
        if (state.getValue(FarmBlock.MOISTURE) == 0 && RetainedFluidBlockInteractions.requestFarmland(level, pos))
            ci.cancel();
    }
}
