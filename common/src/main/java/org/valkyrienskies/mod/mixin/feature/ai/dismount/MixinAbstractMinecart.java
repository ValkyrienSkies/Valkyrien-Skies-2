package org.valkyrienskies.mod.mixin.feature.ai.dismount;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.util.ShipAwareDismount;

// Ship-aware dismount for minecarts. Vanilla queries world cells under the cart, which
// are air for a ship-mounted cart; project the same candidate offsets into the ship's
// frame so DismountHelper validates against the deck.
@Mixin(AbstractMinecart.class)
public abstract class MixinAbstractMinecart {

    // Vanilla AbstractMinecart$f_38067_: per-pose Y offsets searched relative to mount.blockY.
    // STANDING/CROUCHING use {0, +1, -1}; the helper iterates per-candidate not per-pose so
    // we use the union {0, +1, -1} for all candidates (a SWIMMING dismount on a minecart
    // ends up checking Y=-1 too, which vanilla skips — negligible in practice).
    private static final int[] VS$Y_OFFSETS = {0, 1, -1};

    @Inject(method = "getDismountLocationForPassenger", at = @At("HEAD"), cancellable = true)
    private void vs$shipAwareDismount(final LivingEntity passenger, final CallbackInfoReturnable<Vec3> cir) {
        final AbstractMinecart mount = (AbstractMinecart) (Object) this;
        final Level level = mount.level();
        if (level == null) return;

        final Ship ship = VSGameUtilsKt.getEnclosingShip(mount);
        if (ship == null) return;

        final Direction dir = mount.getMotionDirection();
        if (dir.getAxis() == Direction.Axis.Y) return;

        final int[][] offsets = DismountHelper.offsetsForDirection(dir);
        final BlockPos mountBlock = mount.blockPosition();
        final List<Vec3> candidates = new ArrayList<>(offsets.length * VS$Y_OFFSETS.length);
        for (final int yOff : VS$Y_OFFSETS) {
            for (final int[] off : offsets) {
                candidates.add(new Vec3(
                    mountBlock.getX() + off[0] + 0.5,
                    mountBlock.getY() + yOff,
                    mountBlock.getZ() + off[1] + 0.5
                ));
            }
        }

        final Vec3 result = ShipAwareDismount.tryShipyardSpots(level, passenger, ship, candidates, 0);
        cir.setReturnValue(result != null ? result : mount.position());
    }
}
