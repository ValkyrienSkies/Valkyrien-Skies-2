package org.valkyrienskies.mod.mixin.world.entity.projectile;

import static net.minecraft.world.entity.projectile.ProjectileUtil.getHitResult;

import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {
    @Unique
    private static boolean isModifyingHitResult = false;

    @Inject(method = "getHitResult", at = @At("RETURN"), cancellable = true)
    private static void getHitResultMixin(final Entity projectile, final Predicate<Entity> filter,
        final CallbackInfoReturnable<HitResult> cir) {
        if (isModifyingHitResult) {
            return;
        }
        if (cir.getReturnValue().getType() == Type.MISS) {
            isModifyingHitResult = true;
            final Vec3 oldPos = projectile.position();
            final double origX = oldPos.x();
            final double origY = oldPos.y();
            final double origZ = oldPos.z();

            VSGameUtilsKt.transformToNearbyShipsAndWorld(projectile.level, origX, origY, origZ,
                projectile.getBoundingBox().getSize(), (x, y, z) -> {
                    projectile.setPos(x, y, z);
                    final HitResult newHitResult = getHitResult(projectile, filter);
                    projectile.setPos(origX, origY, origZ);
                    cir.setReturnValue((HitResult) newHitResult);
                });
            isModifyingHitResult = false;

        }

    }

}
