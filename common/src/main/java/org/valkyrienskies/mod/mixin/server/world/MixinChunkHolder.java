package org.valkyrienskies.mod.mixin.server.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;
import org.valkyrienskies.mod.common.config.VSGameConfig;

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
    private LevelHeightAccessor levelHeightAccessor;

    @Inject(method = "getTickingChunk", at = @At("HEAD"), cancellable = true)
    private void vs$getTickingChunkForShipyard(CallbackInfoReturnable<LevelChunk> cir) {
        final ChunkPos pos = ((ChunkHolder) (Object) this).getPos();
        if (VSGameConfig.SERVER.getPerformance().getUseRadiusZeroShipChunkTickets()
            && VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) {
            if (levelHeightAccessor instanceof ServerLevel serverLevel) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk != null) {
                    cir.setReturnValue(chunk);
                }
            }
        }
    }

    /**
     * Promote shipyard chunks to BLOCK_TICKING inside updateFutures().
     *
     * In 1.21.1 vanilla, ChunkHolder.updateFutures() calls ChunkLevel.fullStatus(ticketLevel)
     * as a static method — our getFullStatus mixin is bypassed here. For level-33 (FULL)
     * shipyard chunks, this returns FullChunkStatus.FULL, so prepareTickingChunk() and
     * prepareEntityTickingChunk() are never scheduled. That leaves the ticking chunk future
     * empty and, more importantly, prevents startTickingChunk → LevelChunk.unpackTicks from
     * running for newly-loaded chunks via the normal pipeline. Scheduled block ticks
     * (repeaters, buttons, water flow, dispensers) never fire as a result.
     *
     * Upgrading the reported status to BLOCK_TICKING for shipyard chunks restores the normal
     * transitions; ENTITY_TICKING is intentionally NOT used because we want ship chunks to
     * stay at level 33 for neighbor-loading reasons.
     */
    @WrapOperation(
        method = "updateFutures",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkLevel;fullStatus(I)Lnet/minecraft/server/level/FullChunkStatus;"
        )
    )
    private FullChunkStatus vs$promoteShipyardStatus(final int ticketLevel, final Operation<FullChunkStatus> op) {
        final ChunkPos pos = ((ChunkHolder) (Object) this).getPos();
        if (VSGameConfig.SERVER.getPerformance().getUseRadiusZeroShipChunkTickets() && ticketLevel == 33
                && VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(pos.x, pos.z)) {
            return FullChunkStatus.BLOCK_TICKING;
        }
        return op.call(ticketLevel);
    }
}
