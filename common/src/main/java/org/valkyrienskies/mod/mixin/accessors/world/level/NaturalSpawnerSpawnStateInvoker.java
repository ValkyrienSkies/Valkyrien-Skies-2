package org.valkyrienskies.mod.mixin.accessors.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NaturalSpawner.SpawnState.class)
public interface NaturalSpawnerSpawnStateInvoker {
    @Invoker("canSpawn")
    boolean vs$canSpawn(EntityType<?> type, BlockPos pos, ChunkAccess chunk);

    @Invoker("afterSpawn")
    void vs$afterSpawn(Mob mob, ChunkAccess chunk);

    @Invoker("canSpawnForCategory")
    boolean vs$canSpawnForCategory(MobCategory category, ChunkPos chunkPos);
}
