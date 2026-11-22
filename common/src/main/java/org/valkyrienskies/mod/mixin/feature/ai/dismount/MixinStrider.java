package org.valkyrienskies.mod.mixin.feature.ai.dismount;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.util.ShipAwareDismount;

// Ship-aware passenger dismount for striders — mirrors vanilla's 5-angle (yRot, ±22.5°, ±45°) candidate search but validates against shipyard cells.
@Mixin(Strider.class)
public abstract class MixinStrider {

    @Inject(method = "getDismountLocationForPassenger", at = @At("HEAD"), cancellable = true)
    private void vs$shipAwareDismount(final LivingEntity passenger, final CallbackInfoReturnable<Vec3> cir) {
        final Strider mount = (Strider) (Object) this;
        final Ship ship = VSGameUtilsKt.getEnclosingShip(mount);
        if (ship == null) return;
        final Level level = mount.level();

        final float yaw = passenger.getYRot();
        final float[] angleOffsets = { 0.0F, -22.5F, 22.5F, -45.0F, 45.0F };
        final double startY = mount.getBoundingBox().minY;
        final List<Vec3> candidates = new ArrayList<>(angleOffsets.length);
        for (final float off : angleOffsets) {
            final Vec3 escape = ShipAwareDismount.horizontalEscape(
                mount.getBbWidth(), passenger.getBbWidth(), yaw + off
            );
            candidates.add(new Vec3(mount.getX() + escape.x, startY, mount.getZ() + escape.z));
        }

        final Vec3 result = ShipAwareDismount.tryShipyardSpots(level, passenger, ship, candidates, 1);
        cir.setReturnValue(result != null ? result : mount.position());
    }
}
