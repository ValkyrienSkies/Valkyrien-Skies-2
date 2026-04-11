package org.valkyrienskies.mod.mixin.world.chunk;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

@Mixin(ServerChunkCache.class)
public class MixinServerChunkCache {

    /**
     * Allow shipyard chunks to be treated as "position ticking" even at ticket level 33 (FULL).
     *
     * VS2 uses radius-0 tickets (level 33) for ship chunks to minimize neighbor chunk loading.
     * However, vanilla's isPositionTicking requires level ≤ 32, which means:
     * - Scheduled ticks (buttons, repeaters) don't process
     * - Block entities (furnaces, hoppers) don't tick
     *
     * This mixin checks if the chunk is in the shipyard and loaded at FULL status,
     * and returns true so block ticking works on ships.
     */
    @Inject(method = "isPositionTicking", at = @At("HEAD"), cancellable = true)
    private void vs$allowShipyardTicking(long pos, CallbackInfoReturnable<Boolean> cir) {
        int chunkX = ChunkPos.getX(pos);
        int chunkZ = ChunkPos.getZ(pos);
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ)) {
            // Check if the chunk is actually loaded (FULL status)
            if (((ServerChunkCache) (Object) this).getChunkNow(chunkX, chunkZ) != null) {
                cir.setReturnValue(true);
            }
        }
    }
}
