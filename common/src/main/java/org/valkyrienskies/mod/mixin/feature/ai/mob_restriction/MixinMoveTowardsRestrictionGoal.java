package org.valkyrienskies.mod.mixin.feature.ai.mob_restriction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Companion to MixinMobRestriction: project the restrictCenter through shipToWorld for the Vec3.atBottomCenterOf call inside MoveTowardsRestrictionGoal.canUse so DefaultRandomPos.getPosTowards aims at the world-rendered home position.
@Mixin(MoveTowardsRestrictionGoal.class)
public abstract class MixinMoveTowardsRestrictionGoal {

    @Shadow
    @Final
    private PathfinderMob mob;

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "canUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 vs$projectShipyardRestrictCenter(
        final Vec3i pos, final Operation<Vec3> original
    ) {
        final Vec3 vanilla = original.call(pos);
        if (!(pos instanceof BlockPos blockPos)) return vanilla;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(mob.level(), blockPos);
        if (ship == null) return vanilla;
        final Vector3d worldVec = ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(vanilla.x, vanilla.y, vanilla.z), VS$OUT.get()
        );
        return new Vec3(worldVec.x, worldVec.y, worldVec.z);
    }
}
