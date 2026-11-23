package org.valkyrienskies.mod.mixin.world.entity.projectile;

import java.util.function.Predicate;
import java.util.List;
import org.valkyrienskies.mod.common.world.SeatHoverBox;
import org.joml.Matrix4dc;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.valkyrienskies.mod.common.world.RayQuery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
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
    @WrapMethod(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;")
    private static EntityHitResult vs$entityQuery(Entity viewer, Vec3 from, Vec3 to, AABB bounds,
        Predicate<Entity> filter, double limit, Operation<EntityHitResult> original) {
        return RayQuery.entityQuery(() -> original.call(viewer, from, to, bounds, filter, limit));
    }

    // Keep vanilla's candidate/arbitration loop, including modded root-vehicle comparisons.
    // Supplement only its section lookup: carried actors are still indexed at tick positions.
    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> vs$worldPickCandidates(Level level, Entity except, AABB bounds, Predicate<Entity> filter,
        Operation<List<Entity>> query) {
        List<Entity> nativeHits = query.call(level, except, bounds, filter);
        if (!RayQuery.isFrameEntityPick()) return nativeHits;
        return SeatHoverBox.supplement(nativeHits, RayQuery.presentedActors(), except, actor -> {
            if (actor.level() != level) return false;
            Matrix4dc seat = RayQuery.seatBasis(actor);
            AABB box = actor.getBoundingBox();
            AABB envelope = seat == null ? box.inflate(actor.getPickRadius()) :
                SeatHoverBox.of(box.move(-actor.getX(), -actor.getY(), -actor.getZ()), seat, actor.getPickRadius());
            return envelope.intersects(bounds) && filter.test(actor);
        });
    }

    @WrapOperation(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB vs$presentedBox(Entity candidate, Operation<AABB> original) {
        AABB box = original.call(candidate);
        if (!RayQuery.isFrameEntityPick()) return box;
        Matrix4dc seat = RayQuery.seatBasis(candidate);
        return seat == null ? box : SeatHoverBox.of(box.move(-candidate.getX(), -candidate.getY(), -candidate.getZ()), seat, 0.0);
    }

    @Inject(
        at = @At("RETURN"),
        method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
        cancellable = true
    )
    private static void beforeGetEntityHitResult(
        final Entity entity, final Vec3 vec3, final Vec3 vec32, final AABB aABB, final Predicate<Entity> predicate,
        final double d, final CallbackInfoReturnable<@Nullable EntityHitResult> cir) {

        if (cir.getReturnValue() != null) {
            return;
        }

        cir.setReturnValue(RaycastUtilsKt.raytraceEntities(entity.level(), entity, vec3, vec32, aABB, predicate, d));
    }

    // Projectile-style overload (per-target inflate arg). Without this wrap arrows fly
    // straight through shipyard-frame entities — vanilla's scan only sees world entities.
    @Inject(
        at = @At("RETURN"),
        method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
        cancellable = true
    )
    private static void vs$beforeProjectileHitResult(
        final Level level, final Entity entity, final Vec3 vec3, final Vec3 vec32,
        final AABB aABB, final Predicate<Entity> predicate, final float f,
        final CallbackInfoReturnable<@Nullable EntityHitResult> cir) {

        if (cir.getReturnValue() != null || !VSGameUtilsKt.getShipsIntersecting(level, aABB).iterator().hasNext()) {
            return;
        }

        cir.setReturnValue(RaycastUtilsKt.raytraceEntitiesInflated(level, entity, vec3, vec32, aABB, predicate, f));
    }

}
