package org.valkyrienskies.mod.fabric.mixin.compat.cc_restitched;

import dan200.computercraft.client.sound.SpeakerSound;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SpeakerSound.class)
public abstract class MixinSpeakerSound extends AbstractSoundInstance {
    @Unique private SpeakerPosition speakerPosition;
    @Unique private boolean isOnShip;

    protected MixinSpeakerSound(ResourceLocation arg, SoundSource arg2) {
        super(arg, arg2);
    }

    @Inject(
            method = "setPosition",
            at = @At("RETURN"),
            remap = false
    )
    private void isOnShip(SpeakerPosition position, CallbackInfo ci) {
        this.speakerPosition = position;
        this.isOnShip = VSGameUtilsKt.getShipManagingPos(position.level(), position.position()) != null;
        if (this.isOnShip) {
            Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(speakerPosition.level(), speakerPosition.position());
            x = worldPos.x;
            y = worldPos.y;
            z = worldPos.z;
        }
    }

    @Inject(
            method = "tick",
            at = @At("HEAD")
    )
    private void updateWorldPos(CallbackInfo ci) {
        if (this.isOnShip) {
            Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(speakerPosition.level(), speakerPosition.position());
            x = worldPos.x;
            y = worldPos.y;
            z = worldPos.z;
        }
    }
}
