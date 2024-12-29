package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.client.sound.SpeakerSound;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.client.audio.VelocityTickableSoundInstance;

@Pseudo
@Mixin(SpeakerSound.class)
public abstract class MixinSpeakerSound extends AbstractSoundInstance implements VelocityTickableSoundInstance {
    @Unique private SpeakerPosition speakerPosition;
    @Unique private Ship ship;

    protected MixinSpeakerSound(final ResourceLocation arg, final SoundSource arg2) {
        super(arg, arg2);
    }

    @Inject(
        method = "setPosition",
        at = @At("RETURN"),
        remap = false
    )
    private void isOnShip(final SpeakerPosition position, final CallbackInfo ci) {
        this.speakerPosition = position;
        this.ship = ValkyrienSkies.getShipManagingBlock(position.level(), position.position());
        if (this.ship != null) {
            final Vec3 worldPos = ValkyrienSkies.positionToWorld(speakerPosition.level(), speakerPosition.position());
            x = worldPos.x;
            y = worldPos.y;
            z = worldPos.z;
        }
    }

    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void updateWorldPos(final CallbackInfo ci) {
        if (this.ship != null) {
            final Vec3 worldPos = ValkyrienSkies.positionToWorld(speakerPosition.level(), speakerPosition.position());
            x = worldPos.x;
            y = worldPos.y;
            z = worldPos.z;
        }
    }

    @NotNull
    @Override
    public Vector3dc getVelocity() {
        return ship != null ? ship.getVelocity() : new Vector3d();
    }
}
