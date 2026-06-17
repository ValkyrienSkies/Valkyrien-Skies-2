package org.valkyrienskies.mod.fabric.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.mob_spawning.ShipSpawnFinalizeWrap;

@Mixin(NaturalSpawner.class)
public abstract class MixinNaturalSpawnerShipFinalize {

    @WrapOperation(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;"
        )
    )
    private static SpawnGroupData vs$shipFinalize(
        final Mob mob, final ServerLevelAccessor accessor, final DifficultyInstance difficulty,
        final MobSpawnType spawnType, final SpawnGroupData groupData, final CompoundTag tag,
        final Operation<SpawnGroupData> original
    ) {
        return ShipSpawnFinalizeWrap.wrap(mob, accessor, difficulty, spawnType, groupData, tag, original);
    }
}
