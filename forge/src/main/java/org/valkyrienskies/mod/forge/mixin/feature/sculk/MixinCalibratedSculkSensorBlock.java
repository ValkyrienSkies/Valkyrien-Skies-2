package org.valkyrienskies.mod.forge.mixin.feature.sculk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CalibratedSculkSensorBlock;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.forge.common.ShipVibrationUser;

/**
 * Same as {@link MixinSculkSensorBlock} for the calibrated sensor, which has its own ticker.
 */
@Mixin(CalibratedSculkSensorBlock.class)
public abstract class MixinCalibratedSculkSensorBlock {
    // The ticker lambda of getTicker (lambda$getTicker$0 in Mojang's names, m_288182_ in SRG).
    @WrapOperation(
        method = "method_49813",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Ticker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;)V"
        )
    )
    private static void tickWithShipUser(final Level level, final VibrationSystem.Data data,
        final VibrationSystem.User user, final Operation<Void> original, @Local(argsOnly = true) final BlockPos pos) {
        original.call(level, data, ShipVibrationUser.wrap(level, pos, user));
    }
}
