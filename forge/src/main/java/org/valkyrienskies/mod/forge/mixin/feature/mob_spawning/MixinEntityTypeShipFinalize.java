package org.valkyrienskies.mod.forge.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.mob_spawning.ShipSpawnFinalizeWrap;

// Forge binpatches EntityType.create's Mob.finalizeSpawn into ForgeEventFactory.onFinalizeSpawn, so
// the Fabric variant's target is absent here. ForgeEventFactory can't be a @Mixin target (loaded
// before the mixin subsystem); wrapping the INVOKE at this call site does not load it.
@Mixin(EntityType.class)
public abstract class MixinEntityTypeShipFinalize {

    @WrapOperation(
        method = "create(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;Ljava/util/function/Consumer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onFinalizeSpawn(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;"
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
