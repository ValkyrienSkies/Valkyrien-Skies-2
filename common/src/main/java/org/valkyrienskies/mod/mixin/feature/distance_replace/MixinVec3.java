package org.valkyrienskies.mod.mixin.feature.distance_replace;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ShipAwareDistance;

// closerThan(Position, double) routes through distanceToSqr(DDD).
@Mixin(Vec3.class)
public class MixinVec3 {

    @Shadow
    @Final
    public double x;
    @Shadow
    @Final
    public double y;
    @Shadow
    @Final
    public double z;

    @Inject(method = "distanceTo", at = @At("HEAD"), cancellable = true)
    private void vs$distanceTo(final Vec3 vec, final CallbackInfoReturnable<Double> cir) {
        final double d = ShipAwareDistance.distanceSqr(x, y, z, vec.x, vec.y, vec.z);
        if (!Double.isNaN(d)) cir.setReturnValue(Math.sqrt(d));
    }

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", at = @At("HEAD"), cancellable = true)
    private void vs$distanceToSqr(final Vec3 vec, final CallbackInfoReturnable<Double> cir) {
        final double d = ShipAwareDistance.distanceSqr(x, y, z, vec.x, vec.y, vec.z);
        if (!Double.isNaN(d)) cir.setReturnValue(d);
    }

    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void vs$distanceToSqr(final double px, final double py, final double pz,
        final CallbackInfoReturnable<Double> cir) {
        final double d = ShipAwareDistance.distanceSqr(x, y, z, px, py, pz);
        if (!Double.isNaN(d)) cir.setReturnValue(d);
    }
}
