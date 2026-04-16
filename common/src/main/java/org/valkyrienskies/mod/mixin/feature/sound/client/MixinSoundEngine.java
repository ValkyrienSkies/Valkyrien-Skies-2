package org.valkyrienskies.mod.mixin.feature.sound.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Listener;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.audio.VelocityTickableSoundInstance;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.client.player.LocalPlayerDuck;
import org.valkyrienskies.mod.mixinducks.com.mojang.blaze3d.audio.HasOpenALVelocity;

/**
 * Quick explanation for whoever wishes to take on the task of fixing this + the other sound mixins for audio doppler:
 *
 * This current implementation works, with a catch- for some reason I cannot discern, audio pitch and volume increases depending on your
 * positive axis position relative to the ship. If you can fix this, please do, and uncomment the setVelocity methods when you do <3
 * - Potato
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {
    @Unique
    private static final double SCALE_SCALER = 1.0 / 20;

    @Shadow
    protected abstract float calculateVolume(SoundInstance sound);

    @Shadow
    protected abstract float calculatePitch(SoundInstance sound);

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/resources/sounds/TickableSoundInstance;tick()V"
        ),
        method = "tickNonPaused"
    )
    private void tickSoundInstance(final TickableSoundInstance soundInstance, final Operation<Void> ticker) {
        ticker.call(soundInstance);
        if (soundInstance instanceof final VelocityTickableSoundInstance velTicker) {
            velTicker.updateVelocity();
        }
    }

    // Applies the velocity provided by a VelocityTickableSoundInstance
    @SuppressWarnings("unused")
    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
            ordinal = 0
        ),
        method = "tickNonPaused"
    )
    private Object redirectGet(final Map<?, ?> instance, final Object obj, final Operation<Object> get) {
        final Object handle0 = get.call(instance, obj);
        if (
            !VSGameConfig.CLIENT.getSoundEffect().getEnableDopplerEffect() ||
            !(obj instanceof final VelocityTickableSoundInstance soundInstance) ||
            !(handle0 instanceof final ChannelAccess.ChannelHandle handle)
        ) {
            return handle0;
        }

        final float volume = this.calculateVolume(soundInstance);
        final float pitch = this.calculatePitch(soundInstance);
        final Vec3 vec3 = new Vec3(soundInstance.getX(), soundInstance.getY(), soundInstance.getZ());
        final Vector3dc velocity = soundInstance.getVelocity();
        final double scale = VSGameConfig.CLIENT.getSoundEffect().getDopplerEffectScale();

        handle.execute(channel -> {
            channel.setVolume(volume);
            channel.setPitch(pitch);
            channel.setSelfPosition(vec3);
            ((HasOpenALVelocity) channel).setVelocity(
                scale == SCALE_SCALER
                    ? velocity
                    : new Vector3d(velocity).mul(scale / SCALE_SCALER)
            );
        });
        return null;
    }

    @SuppressWarnings("unused")
    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Listener;setListenerPosition(Lnet/minecraft/world/phys/Vec3;)V"
        ),
        method = "*"
    )
    private void injectListenerVelocity(final Listener listener, final Vec3 position, final Operation<Void> setListenerPosition) {
        if (VSGameConfig.CLIENT.getSoundEffect().getEnableDopplerEffect()) {
            final LocalPlayer player = Minecraft.getInstance().player;
            final Vector3dc velocity = player == null ? new Vector3d() : ((LocalPlayerDuck) (player)).vs$getVelocity();
            final double scale = VSGameConfig.CLIENT.getSoundEffect().getDopplerEffectScale();
            ((HasOpenALVelocity) listener).setVelocity(
                scale == SCALE_SCALER
                    ? velocity
                    : new Vector3d(velocity).mul(scale / SCALE_SCALER)
            );
        }
        setListenerPosition.call(listener, position);
    }
}
