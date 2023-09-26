package org.valkyrienskies.mod.mixin.feature.sound.client;

import com.mojang.blaze3d.audio.Listener;
import org.joml.Vector3dc;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.mixinducks.com.mojang.blaze3d.audio.HasOpenALVelocity;

@Mixin(Listener.class)
public class MixinListener implements HasOpenALVelocity {

    public void setVelocity(final Vector3dc velocity) {
        AL10.alListener3f(AL10.AL_VELOCITY, (float) velocity.x(), (float) velocity.y(), (float) velocity.z());
    }
}
