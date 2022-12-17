package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(SpeakerPosition.class)
public abstract class MixinSpeakerPosition {
    @Shadow
    public abstract Level level();

    @Inject(method = "position", at = @At("RETURN"), remap = false, cancellable = true)
    public void ValkyrienSkies2$position(final CallbackInfoReturnable<Vec3> cir) {
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
    public double ValkyrienSkies$distanceToSqr(final Vec3 instance, final Vec3 d) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level(), instance);
        if (ship != null) {
            return VSGameUtilsKt.squaredDistanceBetweenInclShips(level(), instance.x, instance.y, instance.z, d.x, d.y,
                d.z);
        }

        return instance.distanceToSqr(d);
    }
}
