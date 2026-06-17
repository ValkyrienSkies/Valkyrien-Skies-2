package org.valkyrienskies.mod.mixin.feature.ai.mob_restriction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Project Mob.restrictCenter / pos to world before the distance check when either lives in a shipyard chunk; vanilla's BlockPos.distSqr would compare shipyard-vs-world and always fail.
@Mixin(Mob.class)
public abstract class MixinMobRestriction {

    @Shadow
    private BlockPos restrictCenter;

    @Shadow
    private float restrictRadius;

    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER_IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$CENTER_OUT = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$POS_IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$POS_OUT = ThreadLocal.withInitial(Vector3d::new);

    @Inject(
        method = "isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vs$shipAwareDistanceCheck(
        final BlockPos pos, final CallbackInfoReturnable<Boolean> cir
    ) {
        if (restrictRadius == -1.0f || restrictCenter == null) return;
        final Level level = ((Mob) (Object) this).level();
        final Ship centerShip = VSGameUtilsKt.getShipManagingPos(level, restrictCenter);
        final Ship posShip = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (centerShip == null && posShip == null) return;
        final Vector3d centerWorld = vs$projectCellCenterToWorld(
            centerShip, restrictCenter, VS$CENTER_IN.get(), VS$CENTER_OUT.get());
        final Vector3d posWorld = vs$projectCellCenterToWorld(
            posShip, pos, VS$POS_IN.get(), VS$POS_OUT.get());
        final double dx = centerWorld.x - posWorld.x;
        final double dy = centerWorld.y - posWorld.y;
        final double dz = centerWorld.z - posWorld.z;
        cir.setReturnValue(dx * dx + dy * dy + dz * dz < (double) restrictRadius * restrictRadius);
    }

    @Unique
    private static Vector3d vs$projectCellCenterToWorld(
        final Ship ship, final BlockPos pos, final Vector3d in, final Vector3d out
    ) {
        in.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (ship == null) return out.set(in);
        return ship.getTransform().getShipToWorld().transformPosition(in, out);
    }
}
