package org.valkyrienskies.mod.mixin.feature.ai.dismount;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.util.ShipAwareDismount;

// Vanilla Boat.getDismountLocationForPassenger probes a single world cell beside the boat
// in the passenger's facing direction. For a boat resting on a ship's deck that cell is
// air (the deck is in shipyard frame), so the dismount drops the rider into the air.
@Mixin(Boat.class)
public abstract class MixinBoat {

    @Inject(method = "getDismountLocationForPassenger", at = @At("HEAD"), cancellable = true)
    private void vs$shipAwareDismount(final LivingEntity passenger, final CallbackInfoReturnable<Vec3> cir) {
        final Boat mount = (Boat) (Object) this;
        final Level level = mount.level();
        if (level == null) return;

        final Ship ship = VSGameUtilsKt.getEnclosingShip(mount);
        if (ship == null) return;

        // Vanilla Boat uses passenger.getYRot() (not mount yaw); the rider exits in the
        // direction they're looking. AbstractHorse is different — it uses mount yaw +/- 90.
        final Vec3 escape = vs$horizontalEscape(
            mount.getBbWidth() * Mth.SQRT_OF_TWO, passenger.getBbWidth(),
            passenger.getYRot()
        );
        final List<Vec3> candidates = List.of(new Vec3(
            mount.getX() + escape.x, mount.getBoundingBox().minY, mount.getZ() + escape.z
        ));

        final Vec3 result = ShipAwareDismount.tryShipyardSpots(level, passenger, ship, candidates, 1);
        cir.setReturnValue(result != null ? result : mount.position());
    }

    @Unique
    private static Vec3 vs$horizontalEscape(final double mountWidth, final double passengerWidth,
        final float yawDegrees) {
        final double half = (mountWidth + passengerWidth + 1.0E-7) / 2.0;
        final float radians = yawDegrees * (float) (Math.PI / 180.0);
        final float sx = -Mth.sin(radians);
        final float sz = Mth.cos(radians);
        final float maxAxis = Math.max(Math.abs(sx), Math.abs(sz));
        return new Vec3(sx * half / maxAxis, 0.0, sz * half / maxAxis);
    }
}
