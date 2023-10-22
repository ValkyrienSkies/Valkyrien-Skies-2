package org.valkyrienskies.mod.mixin.feature.sound.client;

import com.mojang.blaze3d.audio.Channel;
import org.joml.Vector3dc;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.mixinducks.com.mojang.blaze3d.audio.HasOpenALVelocity;

@Mixin(Channel.class)
public class MixinChannel implements HasOpenALVelocity {

    @Shadow
    @Final
    private int source;

    public void setVelocity(final Vector3dc velocity) {
        AL10.alSource3f(this.source, AL10.AL_VELOCITY, (float) velocity.x(), (float) velocity.y(),
            (float) velocity.z());
    }

}
