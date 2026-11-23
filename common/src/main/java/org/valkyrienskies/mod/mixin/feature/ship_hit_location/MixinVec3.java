package org.valkyrienskies.mod.mixin.feature.ship_hit_location;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ShipHitVec3;

// The other operand order of ShipHitVec3's arithmetic: a point on the hit's ship minus (or towards) the hit.
@Mixin(Vec3.class)
public abstract class MixinVec3 {
    @Shadow
    @Final
    public double x;
    @Shadow
    @Final
    public double y;
    @Shadow
    @Final
    public double z;

    @Inject(method = "subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void vs$minusShipHit(final Vec3 vec, final CallbackInfoReturnable<Vec3> cir) {
        final Vec3 local = vs$localOf(vec);
        if (local != null) cir.setReturnValue(new Vec3(x - local.x, y - local.y, z - local.z));
    }

    @Inject(method = "vectorTo", at = @At("HEAD"), cancellable = true)
    private void vs$towardsShipHit(final Vec3 vec, final CallbackInfoReturnable<Vec3> cir) {
        final Vec3 local = vs$localOf(vec);
        if (local != null) cir.setReturnValue(new Vec3(local.x - x, local.y - y, local.z - z));
    }

    private Vec3 vs$localOf(final Vec3 vec) {
        if (!(vec instanceof final ShipHitVec3 hit) || (Object) this instanceof ShipHitVec3) return null;
        return hit.isOnSameShip(x, y, z) ? hit.getLocal() : null;
    }
}
