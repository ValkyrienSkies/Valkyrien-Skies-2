package org.valkyrienskies.mod.mixin.feature.ship_pos_cache;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.mixinducks.world.OfShip;

@Mixin(Vec3i.class)
public class MixinVec3i implements OfShip {
    @Unique
    protected Ship valkyrienskies$shipCache;

    @Override
    public Ship getShip() {
        return valkyrienskies$shipCache;
    }

    @Override
    public void setShip(Ship ship) {
        valkyrienskies$shipCache = ship;
    }

    @Inject(method = {"setX", "setY", "setZ"}, at = @At("HEAD"))
    private void removeCachedOnSet(int i, CallbackInfoReturnable<Vec3i> cir) {
        valkyrienskies$shipCache = null;
    }

    @ModifyReturnValue(method = {
        "relative(Lnet/minecraft/core/Direction;I)Lnet/minecraft/core/Vec3i;",
        "relative(Lnet/minecraft/core/Direction$Axis;I)Lnet/minecraft/core/Vec3i;"
    }, at = @At("RETURN"))
    private Vec3i carryShipCacheToVec3i(Vec3i original) {
        if (original instanceof OfShip ship)
            ship.setShip(valkyrienskies$shipCache);
        return original;
    }
}
