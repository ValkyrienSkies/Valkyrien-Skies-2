package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.client.sound.SpeakerSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.audio.VelocityTickableSoundInstance;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(value = SpeakerSound.class, priority = 2000)
public abstract class MixinSpeakerSound extends AbstractSoundInstance implements VelocityTickableSoundInstance {
    @Unique
    private Vector3dc position = null;
    @Unique
    private Vector3dc lastPosition = null;
    @Unique
    private Vector3dc velocity = new Vector3d();

    protected MixinSpeakerSound() {
        super((ResourceLocation) (null), null, null);
    }

    @Override
    public double getX() {
        return this.position == null ? this.x : this.position.x();
    }

    @Override
    public double getY() {
        return this.position == null ? this.y : this.position.y();
    }

    @Override
    public double getZ() {
        return this.position == null ? this.z : this.position.z();
    }

    @Override
    public Vector3dc getVelocity() {
        return this.velocity;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void tick(final CallbackInfo ci) {
        this.position = VSGameUtilsKt.toWorldCoordinates(Minecraft.getInstance().level, new Vector3d(this.x, this.y, this.z));
        if (this.lastPosition != null) {
            this.velocity = this.position.sub(this.lastPosition, new Vector3d());
        }
        this.lastPosition = this.position;
    }
}
