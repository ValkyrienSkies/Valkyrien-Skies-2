package org.valkyrienskies.mod.mixin.feature.sound.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Listener;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.audio.VelocityTickableSoundInstance;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.mixinducks.com.mojang.blaze3d.audio.HasOpenALVelocity;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @Shadow
    protected abstract float calculateVolume(SoundInstance sound);

    @Shadow
    protected abstract float calculatePitch(SoundInstance sound);

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

        return get.call(instance, obj);
    }

    @SuppressWarnings("unused")
    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Listener;setListenerPosition(Lnet/minecraft/world/phys/Vec3;)V"
        ),
        method = "*"
    )
    private void injectListenerVelocity(final Listener listener, final Vec3 position,
        final Operation<Void> setListenerPosition) {
        final Player player = Minecraft.getInstance().player;
        final ClientLevel level = Minecraft.getInstance().level;
        ((HasOpenALVelocity) listener).setVelocity(new Vector3d());

        if (level != null && player != null) {
            final ClientShip mounted = (ClientShip) VSGameUtilsKt.getShipMountedTo(player);
            if (mounted != null) {
                ((HasOpenALVelocity) listener).setVelocity(mounted.getVelocity());
            } else {
                final EntityDraggingInformation dragInfo = ((IEntityDraggingInformationProvider) player).getDraggingInformation();
                if (dragInfo.isEntityBeingDraggedByAShip()) {
                    final Vector3dc playerVel = dragInfo.getAddedMovementLastTick();
                    ((HasOpenALVelocity) listener).setVelocity(playerVel);
                }
            }
        }

        setListenerPosition.call(listener, position);
    }
}
