package org.valkyrienskies.mod.mixin.world.level.chunk;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

@Mixin(ChunkGenerator.class)
public class MixinChunkGenerator {
    // TODO its pretty standard to extend this class, if they do super.whatever, these mixins will not work correctly
    // tfc in forge part of the mod has a bandaid solution, if this is fixed please remove that
    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void preFindNearestMapFeature(ServerLevel serverLevel, HolderSet<Structure> holderSet, BlockPos blockPos, int i, boolean bl, CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
            cir.setReturnValue(null);
        }
    }
}
