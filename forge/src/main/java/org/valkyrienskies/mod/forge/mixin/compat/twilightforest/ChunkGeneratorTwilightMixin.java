package org.valkyrienskies.mod.forge.mixin.compat.twilightforest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import twilightforest.world.components.chunkgenerators.ChunkGeneratorTwilight;

@Mixin(ChunkGeneratorTwilight.class)
public class ChunkGeneratorTwilightMixin {

    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void preFindNearestMapStructure(ServerLevel level, HolderSet<Structure> targetStructures, BlockPos pos, int searchRadius, boolean skipKnownStructures, CallbackInfoReturnable cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.getX() >> 4, pos.getZ() >> 4)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void vs$skipCreateStructuresInShipyard(
        RegistryAccess registryAccess,
        ChunkGeneratorStructureState chunkGeneratorStructureState,
        StructureManager structureManager,
        ChunkAccess chunkAccess,
        StructureTemplateManager structureTemplateManager,
        CallbackInfo ci
    ) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkAccess.getPos().x, chunkAccess.getPos().z)) {
            ci.cancel();
        }
    }
}
