package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackNodeLocation.class)
public abstract class MixinTrackNodeLocation extends Vec3i {
    public MixinTrackNodeLocation(int i, int j, int k) {
        super(i, j, k);
    }

    @Inject(
        method = "<init>(DDD)V",
        at = @At("RETURN")
    )
    private void removePrecisionError(final double x, final double y, final double z, final CallbackInfo ci) {
        this.setX((int) Math.round(x * 2));
        this.setZ((int) Math.round(z * 2));
    }
}
