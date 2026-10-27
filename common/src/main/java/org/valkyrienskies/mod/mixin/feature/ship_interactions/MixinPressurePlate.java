package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ShipInteractions;

@Mixin({PressurePlateBlock.class, WeightedPressurePlateBlock.class})
public class MixinPressurePlate {
    @Inject(method = "getSignalStrength", at = @At("RETURN"), cancellable = true)
    private void vs$shipSignal(final Level level, final BlockPos pos,
        final CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.max(cir.getReturnValueI(), ShipInteractions.plateSignal(level, pos)));
    }
}
