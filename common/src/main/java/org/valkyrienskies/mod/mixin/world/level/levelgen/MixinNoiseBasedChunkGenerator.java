package org.valkyrienskies.mod.mixin.world.level.levelgen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGenerator {
    @Shadow
    @Final
    protected Holder<NoiseGeneratorSettings> settings;

    @Shadow
    @Final
    private static BlockState[] EMPTY_COLUMN;

    @Inject(method = "getBaseColumn", at = @At("HEAD"), cancellable = true)
    private void preGetBaseColumn(int i, int j, LevelHeightAccessor levelHeightAccessor, CallbackInfoReturnable<NoiseColumn> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(i, j)) {
            final NoiseSettings noiseSettings = this.settings.value().noiseSettings();
            final int k = Math.max(noiseSettings.minY(), levelHeightAccessor.getMinBuildHeight());
            cir.setReturnValue(new NoiseColumn(k, EMPTY_COLUMN));
        }
    }

    @Inject(method = "buildSurface", at = @At("HEAD"), cancellable = true)
    private void preBuildSurface(WorldGenRegion worldGenRegion, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, CallbackInfo ci) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void preApplyCarvers(WorldGenRegion worldGenRegion, long l, BiomeManager biomeManager, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, GenerationStep.Carving carving, CallbackInfo ci) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "fillFromNoise", at = @At("HEAD"), cancellable = true)
    private void preFillFromNoise(Executor executor, Blender blender, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunkAccess));
        }
    }

    @Inject(method = "spawnOriginalMobs", at = @At("HEAD"), cancellable = true)
    private void preSpawnOriginalMobs(WorldGenRegion worldGenRegion, CallbackInfo ci) {
        final ChunkPos chunkPos = worldGenRegion.getCenter();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }
}
