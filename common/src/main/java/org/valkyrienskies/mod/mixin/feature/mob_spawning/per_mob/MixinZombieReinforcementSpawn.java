package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// Zombie.hurt spawns reinforcement zombies on hard difficulty; each candidate is validated with level.noCollision which only checks world chunks, so reinforcements spawn inside ship geometry at the apparent ship location.
@Mixin(Zombie.class)
public abstract class MixinZombieReinforcementSpawn {

    @WrapOperation(
        method = "hurt",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private boolean vs$noCollisionIncludingShips(
        final Level level, final Entity entity, final Operation<Boolean> original
    ) {
        return ShipAwareCollisionUtil.noCollisionIncludingShips(level, entity, entity.getBoundingBox());
    }
}
