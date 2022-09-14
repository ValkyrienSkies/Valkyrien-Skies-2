package org.valkyrienskies.mod.mixin.client.resources.sounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.client.audio.SimpleSoundInstanceOnShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SimpleSoundInstance.class)
public class MixinSimpleSoundInstance {

    @Inject(
        at = @At("HEAD"),
        method = "forRecord",
        cancellable = true
    )
    private static void forRecord(final SoundEvent sound, final double x, final double y, final double z,
        final CallbackInfoReturnable<SimpleSoundInstance> cir) {

        final Ship ship = VSGameUtilsKt.getShipManagingPos(Minecraft.getInstance().level, x, y, z);
        if (ship != null) {
            cir.setReturnValue(new SimpleSoundInstanceOnShip(
                sound, SoundSource.RECORDS, 4.0F, 1.0F, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, ship));
        }
    }

}
