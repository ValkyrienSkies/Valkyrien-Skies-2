package org.valkyrienskies.mod.mixin.feature.ai.dismount;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
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

// Ship-aware passenger dismount for ridden pigs — mirrors vanilla's motion-direction offsets but validates against shipyard cells. See ShipAwareDismount.
@Mixin(Pig.class)
public abstract class MixinPig {

    @Inject(method = "getDismountLocationForPassenger", at = @At("HEAD"), cancellable = true)
    private void vs$shipAwareDismount(final LivingEntity passenger, final CallbackInfoReturnable<Vec3> cir) {
        final Pig mount = (Pig) (Object) this;
        final Ship ship = VSGameUtilsKt.getEnclosingShip(mount);
        if (ship == null) return;

        final Direction dir = mount.getMotionDirection();
        // Vertical motion direction: defer to super (returns mount position, already on the deck).
        if (dir.getAxis() == Direction.Axis.Y) return;
        final Level level = mount.level();

        final int[][] offsets = DismountHelper.offsetsForDirection(dir);
        final BlockPos mountBlock = mount.blockPosition();
        final double startY = mount.getBoundingBox().minY;
        final List<Vec3> candidates = new ArrayList<>(offsets.length);
        for (final int[] off : offsets) {
            candidates.add(new Vec3(
                mountBlock.getX() + off[0] + 0.5,
                startY,
                mountBlock.getZ() + off[1] + 0.5
            ));
        }

        final Vec3 result = ShipAwareDismount.tryShipyardSpots(level, passenger, ship, candidates, 1);
        cir.setReturnValue(result != null ? result : mount.position());
    }
}
