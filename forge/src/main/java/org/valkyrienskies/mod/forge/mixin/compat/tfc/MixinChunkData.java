package org.valkyrienskies.mod.forge.mixin.compat.tfc;

import net.dries007.tfc.world.chunkdata.ChunkData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    // ChunkData.update is called from ChunkDataProvider#promotePartial with whatever partial ChunkData was tracked
    // for the ProtoChunk being promoted to a LevelChunk, looked up by removing it from a weak map keyed on the
    // ProtoChunk instance. Since shipyard chunks skip the worldgen stages that populate that map (see
    // MixinChunkStatus), the lookup misses and returns null. ChunkDataCapability#setData stores that null with no
    // check, so the next chunk save NPEs in ChunkDataCapability.serializeNBT. Cancelling here leaves the capability
    // holding the non-null ChunkData it was attached with instead of being nulled out.
    @Inject(
        method = "update(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/dries007/tfc/world/chunkdata/ChunkData;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private static void vs$noNullChunkDataUpdate(final LevelChunk chunk, final ChunkData data, final CallbackInfo ci) {
        if (data == null) {
            ci.cancel();
        }
    }
}
