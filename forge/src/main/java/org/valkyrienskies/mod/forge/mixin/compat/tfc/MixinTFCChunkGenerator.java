package org.valkyrienskies.mod.forge.mixin.compat.tfc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

@Pseudo
@Mixin(targets = "net.dries007.tfc.world.TFCChunkGenerator")
public class MixinTFCChunkGenerator {

    @Inject(method = "m_183372_", at = @At("HEAD"), cancellable = true)
    private void preApplyBiomeDecoration(
        final WorldGenLevel worldGenLevel, final ChunkAccess chunkAccess,
        final StructureFeatureManager structureFeatureManager, final CallbackInfo callbackInfo
    ) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "m_62199_", at = @At("HEAD"), cancellable = true)
    private void preCreateStructures(
        final RegistryAccess registryAccess,
        final StructureFeatureManager structureFeatureManager, final ChunkAccess chunkAccess,
        final StructureManager structureManager, final long l, final CallbackInfo callbackInfo
    ) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "m_62177_", at = @At("HEAD"), cancellable = true)
    private void preCreateReferences(
        final WorldGenLevel worldGenLevel, final StructureFeatureManager structureFeatureManager,
        final ChunkAccess chunkAccess, final CallbackInfo callbackInfo
    ) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "m_141914_", at = @At("HEAD"), cancellable = true)
    private void preGetBaseColumn(int i, int j, LevelHeightAccessor levelHeightAccessor, CallbackInfoReturnable<NoiseColumn> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(i, j)) {
            cir.setReturnValue(new NoiseColumn(0, new BlockState[0]));
        }
    }

    @Inject(method = "m_183621_", at = @At("HEAD"), cancellable = true)
    private void preBuildSurface(WorldGenRegion worldGenRegion, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, CallbackInfo ci) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "m_183516_", at = @At("HEAD"), cancellable = true)
    private void preApplyCarvers(WorldGenRegion worldGenRegion, long l, BiomeManager biomeManager, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, GenerationStep.Carving carving, CallbackInfo ci) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "m_183489_", at = @At("HEAD"), cancellable = true)
    private void preFillFromNoise(Executor executor, Blender blender, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunkAccess));
        }
    }

    @Inject(method = "m_6929_", at = @At("HEAD"), cancellable = true)
    private void preSpawnOriginalMobs(WorldGenRegion worldGenRegion, CallbackInfo ci) {
        final ChunkPos chunkPos = worldGenRegion.getCenter();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            ci.cancel();
        }
    }
}
