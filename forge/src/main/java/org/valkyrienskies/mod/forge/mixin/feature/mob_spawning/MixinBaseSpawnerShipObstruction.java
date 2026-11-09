package org.valkyrienskies.mod.forge.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.SpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

@Mixin(BaseSpawner.class)
public abstract class MixinBaseSpawnerShipObstruction {

    @WrapOperation(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;checkSpawnPositionSpawner(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/level/SpawnData;Lnet/minecraft/world/level/BaseSpawner;)Z"
        )
    )
    private boolean vs$wrapForgeCheckObstructionSpawner(
        final Mob mob, final ServerLevelAccessor accessor, final MobSpawnType type,
        final SpawnData data, final BaseSpawner spawner, final Operation<Boolean> original
    ) {
        if (!original.call(mob, accessor, type, data, spawner)) return false;
        if (!(accessor instanceof Level fullLevel)) return true;
        return ShipAwareCollisionUtil.noCollisionIncludingShips(fullLevel, mob, mob.getBoundingBox());
    }
}
