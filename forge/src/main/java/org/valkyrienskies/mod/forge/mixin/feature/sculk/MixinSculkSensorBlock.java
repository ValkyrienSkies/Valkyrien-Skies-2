package org.valkyrienskies.mod.forge.mixin.feature.sculk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.forge.common.ShipVibrationUser;

/**
 * Forge's Mixin cannot inject into {@link VibrationSystem.Ticker}, so the vibration user of a sculk sensor on a ship
 * is converted to world space where the block ticks it. See {@link ShipVibrationUser}.
 */
@Mixin(SculkSensorBlock.class)
public abstract class MixinSculkSensorBlock {
    // The ticker lambda of getTicker (lambda$getTicker$1 in Mojang's names, m_279962_ in SRG); Loom names lambdas by
    // their intermediary name.
    @WrapOperation(
        method = "method_32905",
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
