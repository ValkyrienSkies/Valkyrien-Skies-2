package org.valkyrienskies.mod.forge.mixin.feature.sculk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.forge.common.ShipVibrationUser;

/**
 * Same as {@link MixinSculkSensorBlock} for the entities that tick a {@link VibrationSystem}.
 */
@Mixin({Warden.class, Allay.class})
public abstract class MixinVibrationSystemEntities {
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Ticker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$Data;Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;)V"
        )
    )
    private void tickWithShipUser(final Level level, final VibrationSystem.Data data, final VibrationSystem.User user,
        final Operation<Void> original) {
        original.call(level, data, ShipVibrationUser.wrap(level, Entity.class.cast(this).blockPosition(), user));
    }
}
