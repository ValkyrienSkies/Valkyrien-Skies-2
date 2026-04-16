package org.valkyrienskies.mod.mixin.feature.particle_collision;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;

@Mixin(Entity.class)
public class MixinEntity {

    /**
     * Route particle-style collision checks (entity == null) through VS ship collision first.
     * This keeps behavior centralized at the vanilla collision primitive used by physics particles.
     */
    @ModifyVariable(
        method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1
    )
    private static Vec3 includeShipsForParticleCollision(
        final Vec3 movement,
        @Local(argsOnly = true) final Entity entity,
        @Local(argsOnly = true) final AABB entityBoundingBox,
        @Local(argsOnly = true) final Level level
    ) {
        if (entity != null || !level.isClientSide) {
            return movement;
        }
        return EntityShipCollisionUtils.INSTANCE.adjustEntityMovementForShipCollisions(null, movement, entityBoundingBox, level);
    }
}
