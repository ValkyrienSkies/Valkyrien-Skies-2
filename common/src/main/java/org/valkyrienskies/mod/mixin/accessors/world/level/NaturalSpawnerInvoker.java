package org.valkyrienskies.mod.mixin.accessors.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NaturalSpawner.class)
public interface NaturalSpawnerInvoker {

    @Invoker("getRandomSpawnMobAt")
    static Optional<MobSpawnSettings.SpawnerData> vs$getRandomSpawnMobAt(
        ServerLevel level, StructureManager structureManager, ChunkGenerator generator,
        MobCategory category, RandomSource random, BlockPos pos
    ) {
        throw new AssertionError();
    }

    @Invoker("isValidSpawnPostitionForType")
    static boolean vs$isValidSpawnPositionForType(
        ServerLevel level, MobCategory category, StructureManager structureManager,
        ChunkGenerator generator, MobSpawnSettings.SpawnerData spawnerData,
        BlockPos.MutableBlockPos pos, double playerDistSqr
    ) {
        throw new AssertionError();
    }

    @Invoker("isValidPositionForMob")
    static boolean vs$isValidPositionForMob(ServerLevel level, Mob mob, double playerDistSqr) {
        throw new AssertionError();
    }

    @Invoker("getMobForSpawn")
    static Mob vs$getMobForSpawn(ServerLevel level, EntityType<?> type) {
        throw new AssertionError();
    }
}
