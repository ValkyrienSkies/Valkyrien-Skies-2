package org.valkyrienskies.mod.mixin.feature.distance_replace;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.api.ValkyrienSkies;

/**
 * Replaces all distance checks to include ships
 *
 * @author ewoudje
 */
@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "distanceTo", at = @At("HEAD"), cancellable = true)
    private void preDistanceTo(final Entity entity, final CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(Mth.sqrt((float) valkyrienskies$self().distanceToSqr(entity)));
    }

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", at = @At("HEAD"), cancellable = true)
    private void preDistanceToSqr(final Vec3 vec3, final CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(ValkyrienSkies.shipAwareDistanceToSqr(valkyrienskies$self(), vec3.x, vec3.y, vec3.z));
    }

    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void preDistanceToSqr(final double x, final double y, final double z,
        final CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(ValkyrienSkies.shipAwareDistanceToSqr(valkyrienskies$self(), x, y, z));
    }

    @Unique
    private Entity valkyrienskies$self() {
        return Entity.class.cast(this);
    }
}
