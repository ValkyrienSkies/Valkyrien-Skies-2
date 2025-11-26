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
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(value = SpeakerPosition.class, priority = 2000)
public abstract class MixinSpeakerPosition {
    @Shadow
    public abstract Level level();

    @Redirect(
        method = "withinDistance",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    public double withinDistance$distanceToSqr(final Vec3 position, final Vec3 other) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(this.level(), position);
        if (ship != null) {
            return VSGameUtilsKt.squaredDistanceBetweenInclShips(this.level(), position.x, position.y, position.z, other.x, other.y, other.z);
        }
        return position.distanceToSqr(other);
    }
}
