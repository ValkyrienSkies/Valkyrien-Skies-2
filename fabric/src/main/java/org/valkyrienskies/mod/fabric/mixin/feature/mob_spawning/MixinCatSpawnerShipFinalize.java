package org.valkyrienskies.mod.fabric.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.mob_spawning.ShipSpawnFinalizeWrap;

@Mixin(CatSpawner.class)
public abstract class MixinCatSpawnerShipFinalize {

    @WrapOperation(
        method = "spawnCat",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/Cat;finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;"
        )
    )
    private SpawnGroupData vs$shipFinalize(
        final Cat mob, final ServerLevelAccessor accessor, final DifficultyInstance difficulty,
        final MobSpawnType spawnType, final SpawnGroupData groupData, final CompoundTag tag,
        final Operation<SpawnGroupData> original
    ) {
        return ShipSpawnFinalizeWrap.wrap(mob, accessor, difficulty, spawnType, groupData, tag, original);
    }
}
