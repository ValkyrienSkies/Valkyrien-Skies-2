package org.valkyrienskies.mod.mixin.feature.ai.iron_golem;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// POI scan returns ship POIs in shipyard coords; Vec3.atBottomCenterOf then feeds those to LandRandomPos.getPosTowards as if world coords — project to world if the POI is ship-claimed.
@Mixin(GolemRandomStrollInVillageGoal.class)
public abstract class MixinGolemRandomStrollInVillageGoal {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "getPositionTowardsPoi",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 vs$projectShipPoiToWorld(final Vec3i pos, final Operation<Vec3> original) {
        final Vec3 vanilla = original.call(pos);
        if (!(pos instanceof BlockPos blockPos)) return vanilla;
        final PathfinderMob mob = ((RandomStrollGoalAccessor) this).vs$getMob();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(mob.level(), blockPos);
        if (ship == null) return vanilla;
        final Vector3d in = VS$IN.get().set(vanilla.x, vanilla.y, vanilla.z);
        final Vector3d out = ship.getTransform().getShipToWorld().transformPosition(in, VS$OUT.get());
        return new Vec3(out.x, out.y, out.z);
    }
}
