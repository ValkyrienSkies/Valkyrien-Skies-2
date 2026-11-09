package org.valkyrienskies.mod.mixin.feature.ai.door;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

// canContinueToUse keeps the goal alive while the mob is within 2 blocks of doorPos. For
// a ship-mounted door doorPos is shipyard and mob.position() is world — the cross-frame
// distance is enormous, the gate always rejects, and the goal stops one tick after
// engaging. Project doorPos to world before the comparison.
//
// Extends DoorInteractGoal so `this.mob` resolves through inheritance — @Shadow doesn't
// follow inheritance for parent-class fields.
@Mixin(BreakDoorGoal.class)
public abstract class MixinBreakDoorGoal extends DoorInteractGoal {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    // Required by extending DoorInteractGoal; never invoked at runtime.
    public MixinBreakDoorGoal(final Mob mob) {
        super(mob);
    }

    @WrapOperation(
        method = "canContinueToUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$canContinueProjected(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return original.call(instance, position, dist);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(this.mob.level(), instance);
        if (ship == null) return original.call(instance, position, dist);
        // Project cell center (toWorldCoordinates(level, BlockPos) projects the integer
        // corner; on rotated ships that can drift the projected cell out of the 2-block
        // radius even when the mob is right at the door).
        final Vector3d worldCenter = ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(instance.getX() + 0.5, instance.getY() + 0.5, instance.getZ() + 0.5),
            VS$OUT.get()
        );
        return original.call(
            BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z), position, dist
        );
    }
}
