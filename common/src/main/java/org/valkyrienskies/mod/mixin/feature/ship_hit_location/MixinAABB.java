package org.valkyrienskies.mod.mixin.feature.ship_hit_location;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ShipHitVec3;

// A box on a hit's ship tests the hit in shipyard coordinates (e.g. Create's edge boxes around the hit block).
@Mixin(AABB.class)
public abstract class MixinAABB {
    @Shadow @Final public double minX;
    @Shadow @Final public double minY;
    @Shadow @Final public double minZ;
    @Shadow @Final public double maxX;
    @Shadow @Final public double maxY;
    @Shadow @Final public double maxZ;

    @Shadow
    public abstract boolean contains(double x, double y, double z);

    @Shadow
    public abstract double distanceToSqr(Vec3 vec);

    @Inject(method = "contains(Lnet/minecraft/world/phys/Vec3;)Z", at = @At("HEAD"), cancellable = true)
    private void vs$containsShipHit(final Vec3 vec, final CallbackInfoReturnable<Boolean> cir) {
        final Vec3 local = vs$localOf(vec);
        if (local != null) cir.setReturnValue(contains(local.x, local.y, local.z));
    }

    @Inject(method = "distanceToSqr", at = @At("HEAD"), cancellable = true)
    private void vs$distanceToShipHit(final Vec3 vec, final CallbackInfoReturnable<Double> cir) {
        final Vec3 local = vs$localOf(vec);
        if (local != null) cir.setReturnValue(distanceToSqr(local));
    }

    private Vec3 vs$localOf(final Vec3 vec) {
        if (!(vec instanceof final ShipHitVec3 hit)) return null;
        if (!hit.isOnSameShip((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)) return null;
        return hit.getLocal();
    }
}
