package org.valkyrienskies.mod.mixin.feature.ai.piglin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetAwayFrom;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Consumer of POI-backed memory walk-away behaviors (piglin avoidRepellent reads NEAREST_REPELLENT, hoglin similarly). The synthetic lambda in SetWalkTargetAwayFrom.pos converts the memory BlockPos to a Vec3 via Vec3::atBottomCenterOf and runs world-frame distance/away-pos math; for ship-mounted POIs the result is shipyard coords and the behavior never engages. Project to world via shipToWorld for ship-claimed BlockPos inputs; the entity-overload (input is Entity, not BlockPos) is skipped by the instanceof guard.
@Mixin(SetWalkTargetAwayFrom.class)
public abstract class MixinSetWalkTargetAwayFrom {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "method_47089",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"
        )
    )
    private static Object vs$projectShipPoiVec3ToWorld(
        final Function<Object, Object> function, final Object input,
        final Operation<Object> original,
        @Local(argsOnly = true) final ServerLevel level
    ) {
        final Object result = original.call(function, input);
        if (!(result instanceof Vec3 vec) || !(input instanceof BlockPos blockPos)) return result;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, blockPos);
        if (ship == null) return result;
        final Vector3d worldVec = ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(vec.x, vec.y, vec.z), VS$OUT.get()
        );
        return new Vec3(worldVec.x, worldVec.y, worldVec.z);
    }
}
