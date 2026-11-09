package org.valkyrienskies.mod.fabric.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

@Mixin(BaseSpawner.class)
public abstract class MixinBaseSpawnerShipObstruction {

    @WrapOperation(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;checkSpawnObstruction(Lnet/minecraft/world/level/LevelReader;)Z"
        )
    )
    private boolean vs$wrapMobCheckObstruction(
        final Mob mob, final LevelReader level, final Operation<Boolean> original
    ) {
        if (!original.call(mob, level)) return false;
        if (!(level instanceof Level fullLevel)) return true;
        return ShipAwareCollisionUtil.noCollisionIncludingShips(fullLevel, mob, mob.getBoundingBox());
    }
}
