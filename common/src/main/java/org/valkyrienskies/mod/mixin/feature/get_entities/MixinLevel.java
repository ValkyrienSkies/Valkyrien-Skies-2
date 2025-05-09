package org.valkyrienskies.mod.mixin.feature.get_entities;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.util.RateLimiter;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.util.BugFixUtil;

@Mixin(Level.class)
public class MixinLevel {
    @Unique
    private static final Logger LOGGER = LogManager.getLogger("VS2 Get Entities Mixin");

    @Unique
    private static final RateLimiter LIMITER = new RateLimiter(Duration.ofSeconds(5));

    @ModifyVariable(
        method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private AABB moveAABB1(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @ModifyVariable(
        method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private AABB moveAABB2(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <T extends Entity> void check1(final EntityTypeTest<Entity, T> entityTypeTest, final AABB area,
        final Predicate<? super T> predicate, final CallbackInfoReturnable<List<T>> cir) {

        if (BugFixUtil.INSTANCE.isCollisionBoxTooBig(area)) {
            LIMITER.maybeRun(() ->
                LOGGER.error(new Exception(
                    "Collision box is too big! " + area + " returning empty list! this might break things")));
            cir.setReturnValue(Collections.emptyList());
            cir.cancel();
        }
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <T extends Entity> void check2(@Nullable final Entity entity, final AABB area,
        final Predicate<? super Entity> predicate, final CallbackInfoReturnable<List<Entity>> cir) {

        if (BugFixUtil.INSTANCE.isCollisionBoxTooBig(area)) {
            LIMITER.maybeRun(() ->
                LOGGER.error(new Exception(
                    "Collision box is too big! " + area + " returning empty list! this might break things")));
            cir.setReturnValue(Collections.emptyList());
            cir.cancel();
        }
    }
}
