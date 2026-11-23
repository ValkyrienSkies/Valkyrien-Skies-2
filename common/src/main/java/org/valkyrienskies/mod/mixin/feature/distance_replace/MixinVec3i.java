package org.valkyrienskies.mod.mixin.feature.distance_replace;

import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ShipAwareDistance;

// distSqr/closerThan route through distToLowCornerSqr; distToCenterSqr(Position)/closerToCenterThan through
// distToCenterSqr(DDD). BlockPos inherits all of these.
@Mixin(Vec3i.class)
public abstract class MixinVec3i {

    @Shadow
    public abstract int getX();

    @Shadow
    public abstract int getY();

    @Shadow
    public abstract int getZ();

    @Inject(method = "distToCenterSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void vs$distToCenterSqr(final double px, final double py, final double pz,
        final CallbackInfoReturnable<Double> cir) {
        final double d = ShipAwareDistance.distanceSqr(getX() + 0.5, getY() + 0.5, getZ() + 0.5, px, py, pz);
        if (!Double.isNaN(d)) cir.setReturnValue(d);
    }

    @Inject(method = "distToLowCornerSqr", at = @At("HEAD"), cancellable = true)
    private void vs$distToLowCornerSqr(final double px, final double py, final double pz,
        final CallbackInfoReturnable<Double> cir) {
        final double d = ShipAwareDistance.distanceSqr(getX(), getY(), getZ(), px, py, pz);
        if (!Double.isNaN(d)) cir.setReturnValue(d);
    }

    @Inject(method = "distManhattan", at = @At("HEAD"), cancellable = true)
    private void vs$distManhattan(final Vec3i other, final CallbackInfoReturnable<Integer> cir) {
        final int d = ShipAwareDistance.distManhattan(getX(), getY(), getZ(), other.getX(), other.getY(), other.getZ());
        if (d != Integer.MIN_VALUE) cir.setReturnValue(d);
    }
}
