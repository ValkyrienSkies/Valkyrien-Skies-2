package org.valkyrienskies.mod.mixin.feature.ship_hit_location;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.ShipHitVec3;

// A hit on a shipyard block with a world-space location gets a ShipHitVec3 location, so arithmetic with its block pos
// works in shipyard coordinates.
@Mixin(BlockHitResult.class)
public abstract class MixinBlockHitResult {
    @Inject(
        method = "<init>(ZLnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Z)V",
        at = @At("RETURN")
    )
    private void vs$shipHitLocation(final boolean miss, final Vec3 location, final Direction direction,
        final BlockPos pos, final boolean inside, final CallbackInfo ci) {
        if (miss || location instanceof ShipHitVec3) return;
        if (!ShipHitVec3.inShipyard(pos.getX(), pos.getZ()) || ShipHitVec3.inShipyard(location.x, location.z)) return;
        ((HitResult) (Object) this).location = ShipHitVec3.lazy(location, pos);
    }
}
