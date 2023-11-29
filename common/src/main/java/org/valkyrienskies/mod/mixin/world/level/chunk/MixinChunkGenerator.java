package org.valkyrienskies.mod.mixin.world.level.chunk;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureFeatureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

@Mixin(ChunkGenerator.class)
public class MixinChunkGenerator {
    // TODO its pretty standard to extend this class, if they do super.whatever, these mixins will not work correctly
    // tfc in forge part of the mod has a bandaid solution, if this is fixed please remove that

    @Inject(method = "findNearestMapFeature", at = @At("HEAD"), cancellable = true)
    private void preFindNearestMapFeature(ServerLevel serverLevel, HolderSet<ConfiguredStructureFeature<?, ?>> holderSet, BlockPos blockPos, int i, boolean bl, final CallbackInfoReturnable<Pair<BlockPos, Holder<ConfiguredStructureFeature<?, ?>>>> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void preApplyBiomeDecoration(WorldGenLevel worldGenLevel, ChunkAccess chunkAccess, StructureFeatureManager structureFeatureManager, CallbackInfo callbackInfo) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "hasFeatureChunkInRange", at = @At("HEAD"), cancellable = true)
    private void preHasFeatureChunkInRange(ResourceKey<StructureSet> resourceKey, long l, int chunkX, int chunkZ, int k, final CallbackInfoReturnable<Boolean> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void preCreateStructures(RegistryAccess registryAccess, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, StructureManager structureManager, long l, CallbackInfo callbackInfo) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "createReferences", at = @At("HEAD"), cancellable = true)
    private void preCreateReferences(WorldGenLevel worldGenLevel, StructureFeatureManager structureFeatureManager, ChunkAccess chunkAccess, CallbackInfo callbackInfo) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
            callbackInfo.cancel();
        }
    }
}
