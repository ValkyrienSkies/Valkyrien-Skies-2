package org.valkyrienskies.mod.mixin.feature.sound.client;

import com.mojang.blaze3d.audio.Listener;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.audio.VelocityTickableSoundInstance;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.com.mojang.blaze3d.audio.HasOpenALVelocity;

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
        if (obj instanceof final VelocityTickableSoundInstance soundInstance) {
            final ChannelAccess.ChannelHandle handle = (ChannelAccess.ChannelHandle) instance.get(soundInstance);
            final float f = calculateVolume(soundInstance);
            final float g = calculatePitch(soundInstance);
            final Vec3 vec3 = new Vec3(soundInstance.getX(), soundInstance.getY(), soundInstance.getZ());
            final Vector3dc velocity = soundInstance.getVelocity();

            handle.execute(channel -> {
                channel.setVolume(f);
                channel.setPitch(g);
                channel.setSelfPosition(vec3);
                ((HasOpenALVelocity) channel).setVelocity(velocity);
            });
            return null;
        }

        return instance.get(obj);
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Listener;setListenerPosition(Lnet/minecraft/world/phys/Vec3;)V"
        ),
        method = "*"
    )
    private void injectListenerVelocity(final Listener listener, final Vec3 position) {
        final Player player = Minecraft.getInstance().player;
        final ClientLevel level = Minecraft.getInstance().level;
        if (level != null && player != null) {
            final ClientShip mounted = VSGameUtilsKt.getShipObjectEntityMountedTo(level, player);
            if (mounted != null) {
                ((HasOpenALVelocity) listener).setVelocity(mounted.getVelocity());
            }
        }

        listener.setListenerPosition(position);
    }
}
