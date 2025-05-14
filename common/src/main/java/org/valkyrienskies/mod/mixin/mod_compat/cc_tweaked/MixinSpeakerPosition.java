package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(SpeakerPosition.class)
public abstract class MixinSpeakerPosition {
    @Shadow
    public abstract Level level();

    @Inject(method = "position", at = @At("RETURN"), remap = false, cancellable = true)
    public void position(final CallbackInfoReturnable<Vec3> cir) {
        final Vec3 pos = cir.getReturnValue();
        final Ship ship = VSGameUtilsKt.getShipObjectManagingPos(level(), pos.x, pos.y, pos.z);
        if (ship != null) {
            cir.setReturnValue(VSGameUtilsKt.toWorldCoordinates(level(), pos));
        }
    }

    @Redirect(
        method = "withinDistance",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    public double withinDistance$distanceToSqr(final Vec3 position, final Vec3 other) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level(), position);
        if (ship != null) {
            return VSGameUtilsKt.squaredDistanceBetweenInclShips(level(), position.x, position.y, position.z, other.x, other.y, other.z);
        }
        return position.distanceToSqr(other);
    }
}
