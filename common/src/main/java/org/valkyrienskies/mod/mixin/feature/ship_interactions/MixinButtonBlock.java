package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.ShipButtons;

@Mixin(ButtonBlock.class)
public abstract class MixinButtonBlock {
    @Inject(method = "checkPressed", at = @At("HEAD"), cancellable = true)
    private void vs$keepShipButtonPressed(final BlockState state, final Level level, final BlockPos pos,
                                         final CallbackInfo ci) {
        if (ShipButtons.isHeld(level, pos)) {
            ci.cancel();
        }
    }
}
