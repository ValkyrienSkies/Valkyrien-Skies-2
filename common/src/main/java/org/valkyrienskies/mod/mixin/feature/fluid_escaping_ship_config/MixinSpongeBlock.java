package org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpongeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.RetainedFluidBlockInteractions;

@Mixin(SpongeBlock.class)
public abstract class MixinSpongeBlock {
    @Inject(method = "tryAbsorbWater", at = @At("TAIL"))
    private void vs$absorbRetainedWater(final Level level, final BlockPos pos, final CallbackInfo ci) {
        RetainedFluidBlockInteractions.requestSponge(level, pos);
    }
}
