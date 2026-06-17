package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.mob_spawning.ShipSpawnFinalizeWrap;

@Mixin(BaseSpawner.class)
public abstract class MixinBaseSpawnerShipFinalize {

    @WrapOperation(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;"
        )
    )
    private SpawnGroupData vs$shipFinalize(
        final Mob mob, final ServerLevelAccessor accessor, final DifficultyInstance difficulty,
        final MobSpawnType spawnType, final SpawnGroupData groupData, final CompoundTag tag,
        final Operation<SpawnGroupData> original
    ) {
        return ShipSpawnFinalizeWrap.wrap(mob, accessor, difficulty, spawnType, groupData, tag, original);
    }
}
