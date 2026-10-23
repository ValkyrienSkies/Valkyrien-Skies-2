package org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources;

@Mixin(LiquidBlock.class)
public class MixinLiquidBlock {
    @Inject(method = "pickupBlock", at = @At("HEAD"), cancellable = true)
    private void beforePickup(final LevelAccessor level, final BlockPos pos, final BlockState state,
                              final CallbackInfoReturnable<ItemStack> cir) {
        if (FractionalFluidSources.owns(level, pos)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
