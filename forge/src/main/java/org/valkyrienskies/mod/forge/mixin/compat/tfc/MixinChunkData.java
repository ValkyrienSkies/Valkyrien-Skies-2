package org.valkyrienskies.mod.forge.mixin.compat.tfc;

import net.dries007.tfc.world.chunkdata.ChunkData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

// Shipyard chunks skip worldgen (see MixinChunkStatus), so TFC never generates chunk data for them and its capability
// holds null. Every ChunkData.get overload funnels through this one, so anything that asks TFC about a shipyard chunk
// - onChunkWatch when a client starts tracking a ship, TFC block ticks on ship blocks - NPEs without this.
// The priority is above the default 1000 so we can still inject when another mod @Overwrites this method, as
// TerraFirmaGreg-Core does.
@Mixin(value = ChunkData.class, remap = false, priority = 1500)
public class MixinChunkData {

    @Inject(
        method = "get(Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/dries007/tfc/world/chunkdata/ChunkData;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void vs$noChunkDataInShipyard(final LevelChunk chunk, final CallbackInfoReturnable<ChunkData> cir) {
        if (chunk == null) {
            return;
        }
        final ChunkPos pos = chunk.getPos();
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) {
            cir.setReturnValue(ChunkData.EMPTY);
        }
    }
}
