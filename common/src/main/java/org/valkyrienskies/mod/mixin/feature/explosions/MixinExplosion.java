package org.valkyrienskies.mod.mixin.feature.explosions;

import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Explosion.class)
public abstract class MixinExplosion {

    @Shadow
    @Final
    private Level level;
    @Shadow
    @Final
    @Mutable
    private double x;

    @Shadow
    @Final
    @Mutable
    private double y;
    @Shadow
    @Final
    @Mutable
    private double z;
    @Shadow
    @Final
    @Mutable
    private float radius;
    @Unique
    private boolean isModifyingExplosion = false;

    @Shadow
    public abstract void explode();

    @Inject(at = @At("TAIL"), method = "explode")
    private void afterExplode(final CallbackInfo ci) {
        if (isModifyingExplosion) {
            return;
        }

        isModifyingExplosion = true;

        final double origX = this.x;
        final double origY = this.y;
        final double origZ = this.z;

        VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, this.x, this.y, this.z, this.radius, (x, y, z) -> {
            this.x = x;
            this.y = y;
            this.z = z;
            this.explode();
        });

        this.x = origX;
        this.y = origY;
        this.z = origZ;

        isModifyingExplosion = false;
    }

    // Don't raytrace the shipyard
    // getEntities already gives shipyard entities
    @Redirect(method = "explode",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        )
    )
    private List<Entity> noRayTrace(final Level instance, final Entity entity, final AABB aabb) {
        if (isModifyingExplosion) {
            return Collections.emptyList();
        } else {
            return instance.getEntities(entity, aabb);
        }
    }
}
