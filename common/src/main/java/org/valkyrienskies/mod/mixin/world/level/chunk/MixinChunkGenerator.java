package org.valkyrienskies.mod.mixin.world.level.chunk;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

@Mixin(ChunkGenerator.class)
public class MixinChunkGenerator {
    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void preFindNearestMapFeature(ServerLevel serverLevel, HolderSet<Structure> holderSet, BlockPos blockPos, int i, boolean bl, CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void preApplyBiomeDecoration(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess, StructureManager structureManager, CallbackInfo callbackInfo) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "hasStructureChunkInRange", at = @At("HEAD"), cancellable = true)
    private void preHasFeatureChunkInRange(Holder<StructureSet> holder, RandomState randomState, long l, int chunkX, int chunkZ, int k, CallbackInfoReturnable<Boolean> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void preCreateStructures(RegistryAccess registryAccess, RandomState randomState, StructureManager structureManager, ChunkAccess chunkAccess, StructureTemplateManager structureTemplateManager, long l, CallbackInfo callbackInfo) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "createReferences", at = @At("HEAD"), cancellable = true)
    private void preCreateReferences(WorldGenLevel worldGenLevel, StructureManager structureManager, ChunkAccess chunkAccess, CallbackInfo callbackInfo) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }
}
