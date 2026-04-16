package org.valkyrienskies.mod.mixin.server.world;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

/**
 * Fix multiple issues with ship chunks at level 33 (FULL status):
 *
 * 1. getTickingChunk() returns null — blocks blockChanged() and broadcastChanges()
 *    from sending block update packets to clients. Fixed by getting the chunk
 *    directly from the ServerLevel's chunk cache.
 *
 * 2. getFullStatus() returns FULL instead of BLOCK_TICKING — this prevents:
 *    - registerTickContainerInLevel() from being called during chunk loading,
 *      so scheduled ticks (repeaters, observers, etc.) don't work after save/reload
 *    - Level.markAndNotifyBlock() from calling sendBlockUpdated()
 *    Fixed by returning BLOCK_TICKING for shipyard positions.
 */
@Mixin(ChunkHolder.class)
public abstract class MixinChunkHolder {

    @Shadow
    @Final
    ChunkPos pos;

    @Shadow
    @Final
    private LevelHeightAccessor levelHeightAccessor;

    @Inject(method = "getTickingChunk", at = @At("HEAD"), cancellable = true)
    private void vs$getTickingChunkForShipyard(CallbackInfoReturnable<LevelChunk> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) {
            if (levelHeightAccessor instanceof ServerLevel serverLevel) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk != null) {
                    cir.setReturnValue(chunk);
                }
            }
        }
    }

    /**
     * Report shipyard chunk holders as BLOCK_TICKING status.
     *
     * ChunkMap uses this status to decide when to call registerTickContainerInLevel().
     * Without this, ship chunks at level 33 (FULL) never get their tick containers
     * registered, so scheduled ticks (repeaters, observers, buttons) don't work
     * after a save/reload cycle.
     */
    @Inject(method = "getFullStatus", at = @At("HEAD"), cancellable = true)
    private void vs$upgradeShipyardHolderStatus(CallbackInfoReturnable<FullChunkStatus> cir) {
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) {
            cir.setReturnValue(FullChunkStatus.BLOCK_TICKING);
        }
    }
}
