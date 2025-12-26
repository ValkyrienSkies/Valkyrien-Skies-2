package org.valkyrienskies.mod.forge.mixin.compat.terrafirmacraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.dries007.tfc.world.TFCChunkGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

@Mixin(TFCChunkGenerator.class)
public class MixinTFCChunkGenerator {
    @Final
    @Shadow
    private Holder<NoiseGeneratorSettings> noiseSettings;

    @Inject(method = "getBaseColumn", at = @At("HEAD"), cancellable = true)
    private void preGetBaseColumn(int x, int y, LevelHeightAccessor level, RandomState random, CallbackInfoReturnable<NoiseColumn> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(x, y)) {
            final NoiseSettings ns = this.noiseSettings.value().noiseSettings();
            final int k = Math.max(ns.minY(), level.getMinBuildHeight());
            cir.setReturnValue(new NoiseColumn(k, new BlockState[0]));
        }
    }

    @Inject(method = "buildSurface", at = @At("HEAD"), cancellable = true)
    private void preBuildSurface(WorldGenRegion world, StructureManager manager, RandomState random, ChunkAccess chunk, CallbackInfo ci) {
        final ChunkPos chunkPos = chunk.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "fillFromNoise", at = @At("HEAD"), cancellable = true)
    private void preFillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structureManager, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        final ChunkPos chunkPos = chunk.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }

    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void preApplyCarvers(WorldGenRegion worldGenRegion, long l, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess, Carving carving, CallbackInfo ci) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void preCreateStructures(RegistryAccess access, ChunkGeneratorStructureState state, StructureManager manager, ChunkAccess chunk, StructureTemplateManager templateManager, CallbackInfo ci) {
        final ChunkPos chunkPos = chunk.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "spawnOriginalMobs", at = @At("HEAD"), cancellable = true)
    private void preSpawnOriginalMobs(WorldGenRegion worldGenRegion, CallbackInfo ci) {
        final ChunkPos chunkPos = worldGenRegion.getCenter();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void preApplyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk,
        StructureManager structureFeatureManager, CallbackInfo ci) {
        final ChunkPos chunkPos = chunk.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }
}
