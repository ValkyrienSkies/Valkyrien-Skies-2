package org.valkyrienskies.mod.mixin.feature.submarines;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;

@Mixin(WaterFluid.class)
public class MixinWaterFluid {

    @Inject(
        method = "animateTick",
        at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
        cancellable = true
    )
    private void preAnimateTick(Level level, BlockPos blockPos, FluidState fluidState, RandomSource randomSource,
        CallbackInfo ci) {
        if (ShipWaterPocketManager.isWorldPosInShipAirPocket(level, blockPos)) {
            ci.cancel();
        }
    }
}
