package org.valkyrienskies.mod.mixin.feature.sound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.client.audio.SimpleSoundInstanceOnShip;

@Mixin(SimpleSoundInstance.class)
public class MixinSimpleSoundInstance {

    @Inject(
        at = @At("HEAD"),
        method = "forRecord",
        cancellable = true
    )
    private static void forRecord(final SoundEvent sound, final Vec3 pos,
        final CallbackInfoReturnable<SimpleSoundInstance> cir) {

        final Ship ship = ValkyrienSkies.getShipManagingBlock(Minecraft.getInstance().level, pos.x(), pos.y(), pos.z());
        if (ship != null) {
            cir.setReturnValue(new SimpleSoundInstanceOnShip(
                sound, SoundSource.RECORDS, 4.0F, 1.0F, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.LINEAR, pos.x(), pos.y(), pos.z(), ship));
        }
    }

}
