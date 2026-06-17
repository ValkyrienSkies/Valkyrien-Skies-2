package org.valkyrienskies.mod.mixin.feature.ai.ghast;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// Ghast canReach steps a probe AABB to its wanted-position; vanilla noCollision is world-only so it would fly through ship hulls.
@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastMoveControl")
public abstract class MixinGhastMoveControl {

    @WrapOperation(
        method = "canReach",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean vs$noCollisionIncludingShips(
        final Level level, final Entity entity, final AABB aabb,
        final Operation<Boolean> original
    ) {
        return ShipAwareCollisionUtil.noCollisionIncludingShips(level, entity, aabb);
    }
}
