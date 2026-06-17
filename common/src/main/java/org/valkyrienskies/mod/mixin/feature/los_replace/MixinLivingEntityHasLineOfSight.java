package org.valkyrienskies.mod.mixin.feature.los_replace;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Project shipyard-positioned eye Vec3s in hasLineOfSight to world so the 128-block distance gate sees the real distance and clip_replace.MixinLevel routes through clipIncludeShips with both endpoints in the same frame.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityHasLineOfSight {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @ModifyExpressionValue(
        method = "hasLineOfSight",
        at = @At(value = "NEW", target = "(DDD)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 vs$projectEyeToWorldIfShipyard(final Vec3 originalVec) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(
            ((LivingEntity) (Object) this).level(),
            originalVec.x, originalVec.y, originalVec.z
        );
        if (ship == null) return originalVec;
        final Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(originalVec.x, originalVec.y, originalVec.z), VS$OUT.get()
        );
        return new Vec3(worldPos.x, worldPos.y, worldPos.z);
    }
}
