package org.valkyrienskies.mod.forge.mixin.feature.sculk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.forge.common.ShipVibrationUser;

/**
 * Same as {@link MixinSculkSensorBlock} for the sculk shrieker.
 */
@Mixin(SculkShriekerBlock.class)
public abstract class MixinSculkShriekerBlock {
    // The ticker lambda of getTicker (lambda$getTicker$3 in Mojang's names, m_279963_ in SRG).
    @WrapOperation(
        method = "method_42317",
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
