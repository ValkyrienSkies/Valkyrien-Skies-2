package org.valkyrienskies.mod.forge.mixin.entity;

import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.entity.BoatShipWater;

@Mixin(Boat.class)
public abstract class MixinBoat {

    @Shadow private double waterLevel;
    @Shadow private double lastYd;

    @Inject(method = "getWaterLevelAbove", at = @At("HEAD"), cancellable = true)
    private void vs$getWaterLevelAbove(final CallbackInfoReturnable<Float> cir) {
        final Boat boat = (Boat) (Object) this;
        final Ship ship = BoatShipWater.parentShip(boat);
        if (ship == null) return;
        cir.setReturnValue(BoatShipWater.getWaterLevelAbove(boat, ship, this.lastYd,
            boat::canBoatInFluid));
    }

    @Inject(method = "checkInWater", at = @At("HEAD"), cancellable = true)
    private void vs$checkInWater(final CallbackInfoReturnable<Boolean> cir) {
        final Boat boat = (Boat) (Object) this;
        final Ship ship = BoatShipWater.parentShip(boat);
        if (ship == null) return;
        final BoatShipWater.CheckInWater r = BoatShipWater.checkInWater(boat, ship,
            boat::canBoatInFluid);
        this.waterLevel = r.waterLevel();
        cir.setReturnValue(r.inWater());
    }

    @Inject(method = "isUnderwater", at = @At("HEAD"), cancellable = true)
    private void vs$isUnderwater(final CallbackInfoReturnable<Boat.Status> cir) {
        final Boat boat = (Boat) (Object) this;
        final Ship ship = BoatShipWater.parentShip(boat);
        if (ship == null) return;
        cir.setReturnValue(BoatShipWater.isUnderwater(boat, ship, boat::canBoatInFluid));
    }

    @Inject(method = "getGroundFriction", at = @At("HEAD"), cancellable = true)
    private void vs$getGroundFriction(final CallbackInfoReturnable<Float> cir) {
        final Boat boat = (Boat) (Object) this;
        final Ship ship = BoatShipWater.parentShip(boat);
        if (ship == null) return;
        cir.setReturnValue(BoatShipWater.getGroundFriction(boat, ship));
    }
}
