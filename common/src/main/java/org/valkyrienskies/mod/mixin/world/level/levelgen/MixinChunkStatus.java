package org.valkyrienskies.mod.mixin.world.level.levelgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

@Mixin(ChunkStatus.class)
public class MixinChunkStatus {

    // BIOMES + NOISE — both have the same full generation task signature
    @Inject(method = {"method_38285", "method_38284"}, at = @At("HEAD"), cancellable = true)
    private static void skipFillFromNoise(ChunkStatus chunkStatus, Executor executor, ServerLevel serverLevel,
        ChunkGenerator chunkGenerator, StructureTemplateManager structureTemplateManager,
        ThreadedLevelLightEngine threadedLevelLightEngine, Function function, List list, ChunkAccess chunkAccess,
        CallbackInfoReturnable<CompletableFuture<Either>> cir) {
        ChunkPos cp = chunkAccess.getPos();
        // We deal with returning Either.left(...) to skip the method entirely instead of wrapping just fillFromNoise.
        // This will skip the check for bedrock retrogen (thanks Caves & Cliffs), probably a tiny performance increase.
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cp.x, cp.z)) cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunkAccess)));
    }

    // STRUCTURE_STARTS, createStructures
    // Here we use a WrapOp target instead of cancelling because the method also calls some ServerLevel things, presumably necessary.
    @WrapOperation(method = "method_39464", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;createStructures(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;)V"))
    private static void skipCreateStructures(ChunkGenerator instance, RegistryAccess registryAccess,
        ChunkGeneratorStructureState chunkGeneratorStructureState, StructureManager structureManager,
        ChunkAccess chunkAccess, StructureTemplateManager structureTemplateManager, Operation<Void> original) {
        ChunkPos cp = chunkAccess.getPos();
        if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cp.x, cp.z)) original.call(instance, registryAccess, chunkGeneratorStructureState, structureManager, chunkAccess, structureTemplateManager);
    }

    // Lambdas are anonymous and have no mojmap. Intermediary names are used. These are usually stable between versions.
    @Inject(
        method = {
            "method_16569", // SURFACE, buildSurface
            "method_38282", // CARVERS, applyCarvers
            "method_51375", // FEATURES, applyBiomeDecoration
            "method_17033", // SPAWN, spawnOriginalMobs
            "method_16565", // STRUCTURE_REFERENCES, createReferences
        },
        at = @At("HEAD"), cancellable = true
    )
    private static void skipStage(ChunkStatus chunkStatus, ServerLevel serverLevel,
        ChunkGenerator chunkGenerator, List list, ChunkAccess chunkAccess, CallbackInfo ci) {
        ChunkPos cp = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cp.x, cp.z)) ci.cancel();
    }

    // Let the vanilla light engine's lightChunk run for shipyard chunks.
    // MixinThreadedLevelLightEngine gives shipyard light tasks high priority so they
    // actually get processed. MixinChunkMapShipyard handles neighbor requirements.
    // lightChunk sets up internal light engine state (propagateLightSources, setLightEnabled)
    // that is required for lighting to work correctly after assembly.
}
