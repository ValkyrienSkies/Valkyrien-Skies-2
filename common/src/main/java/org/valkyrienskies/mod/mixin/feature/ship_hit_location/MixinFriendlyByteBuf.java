package org.valkyrienskies.mod.mixin.feature.ship_hit_location;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipHitVec3;

// The location is written relative to the block pos, so a ship hit is sent in shipyard coordinates.
@Mixin(FriendlyByteBuf.class)
public abstract class MixinFriendlyByteBuf {
    @WrapOperation(
        method = "writeBlockHitResult",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 vs$shipyardLocation(final BlockHitResult hit, final Operation<Vec3> original) {
        final Vec3 location = original.call(hit);
        if (location instanceof final ShipHitVec3 shipHit && shipHit.getLocal() != null) return shipHit.getLocal();
        return location;
    }
}
