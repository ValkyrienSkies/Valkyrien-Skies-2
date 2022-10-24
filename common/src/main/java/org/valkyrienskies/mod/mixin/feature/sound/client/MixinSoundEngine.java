package org.valkyrienskies.mod.mixin.feature.sound.client;

import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.client.audio.VelocityTickableSoundInstance;
import org.valkyrienskies.mod.mixinducks.com.mojang.blaze3d.audio.ChannelDuck;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @Shadow
    protected abstract float calculateVolume(SoundInstance sound);

    @Shadow
    protected abstract float calculatePitch(SoundInstance sound);

    // Applies the velocity provided by a VelocityTickableSoundInstance
    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
            ordinal = 0
        ),
        method = "tickNonPaused"
    )
    private Object redirectGet(final Map instance, final Object obj) {
        if (obj instanceof VelocityTickableSoundInstance) {
            final VelocityTickableSoundInstance soundInstance = (VelocityTickableSoundInstance) obj;
            final ChannelAccess.ChannelHandle handle = (ChannelAccess.ChannelHandle) instance.get(soundInstance);
            final float f = calculateVolume(soundInstance);
            final float g = calculatePitch(soundInstance);
            final Vec3 vec3 = new Vec3(soundInstance.getX(), soundInstance.getY(), soundInstance.getZ());
            final Vector3dc velocity = soundInstance.getVelocity();

            handle.execute(channel -> {
                channel.setVolume(f);
                channel.setPitch(g);
                channel.setSelfPosition(vec3);
                ((ChannelDuck) channel).setVelocity(velocity);
            });
            return null;
        }

        return instance.get(obj);
    }
}
