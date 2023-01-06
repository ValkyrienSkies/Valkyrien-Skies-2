package org.valkyrienskies.mod.mixin.world.entity.projectile;

import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

    @Inject(
        at = @At("HEAD"),
        method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
        cancellable = true
    )
    private static void beforeGetEntityHitResult(
        final Entity entity, final Vec3 vec3, final Vec3 vec32, final AABB aABB, final Predicate<Entity> predicate,
        final double d, final CallbackInfoReturnable<@Nullable EntityHitResult> cir) {

        if (!VSGameUtilsKt.getShipsIntersecting(entity.level, aABB).iterator().hasNext()) {
            return;
        }

        cir.setReturnValue(RaycastUtilsKt.raytraceEntities(entity.level, entity, vec3, vec32, aABB, predicate, d));
    }

}
