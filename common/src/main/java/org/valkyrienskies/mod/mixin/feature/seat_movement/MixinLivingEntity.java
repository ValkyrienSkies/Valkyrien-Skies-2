package org.valkyrienskies.mod.mixin.feature.seat_movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

/**
 * A rider of a ship seat is placed by the seat at the end of every tick, so any self-applied movement in between is
 * thrown away again — which shows up as the rider jittering towards the held movement key. Skip the movement half of
 * travel entirely while seated; the animation update at its tail still runs.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @WrapOperation(
        method = "travel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isControlledByLocalInstance()Z"
        )
    )
    private boolean vs$dontMoveWhileSeatedOnShip(final LivingEntity self,
        final Operation<Boolean> isControlledByLocalInstance) {
        if (self.getVehicle() instanceof ShipMountingEntity) {
            return false;
        }
        return isControlledByLocalInstance.call(self);
    }
}
