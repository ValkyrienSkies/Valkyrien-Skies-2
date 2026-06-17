package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// Warden.checkSpawnObstruction validates the candidate spawn AABB with level.noCollision, which only checks world chunks; without this wrap a warden could spawn inside ship geometry.
@Mixin(Warden.class)
public abstract class MixinWardenSpawn {

    @WrapOperation(
        method = "checkSpawnObstruction",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean vs$noCollisionIncludingShips(
        final LevelReader levelReader, final Entity entity, final AABB aabb,
        final Operation<Boolean> original
    ) {
        if (!(levelReader instanceof Level)) return original.call(levelReader, entity, aabb);
        return ShipAwareCollisionUtil.noCollisionIncludingShips((Level) levelReader, entity, aabb);
    }
}
