package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;

@Mixin(value = Entity.class, priority = 1100)
abstract class MixinEntityPose {
    /**
     * Allows Entities to use collision with ship to get into pose, e.g. crawling of players.
     */
    @WrapOperation(
        method = "canEnterPose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z")
    )
    private boolean noCollisionWithShip(Level level, Entity entity, AABB aabb, Operation<Boolean> original){
        if(!original.call(level, entity, aabb)) return false;
        return EntityShipCollisionUtils.INSTANCE.getShipPolygonsCollidingWithEntity(null, Vec3.ZERO, aabb.deflate(0.2), level).isEmpty();
    }
}
