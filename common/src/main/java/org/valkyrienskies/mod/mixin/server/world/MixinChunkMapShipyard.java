package org.valkyrienskies.mod.mixin.server.world;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

/**
 * Bypasses chunk pipeline neighbor requirements for shipyard chunks.
 *
 * When a shipyard chunk is being promoted through chunk statuses (EMPTY → ... → FULL),
 * each status normally requires neighbors at certain levels (e.g., FEATURES needs 8-radius
 * neighbors). With radius-0 tickets (level 33), no neighbor ChunkHolders exist, so the
 * pipeline would stall forever.
 *
 * This mixin intercepts getChunkRangeFuture to return only the center chunk when the
 * target is a shipyard chunk. The generation work at each status is already skipped by
 * MixinChunkStatus, so no actual neighbor data is needed.
 */
@Mixin(ChunkMap.class)
public class MixinChunkMapShipyard {

    @Inject(
        method = "getChunkRangeFuture",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vs$skipNeighborsForShipyard(
        ChunkHolder centerHolder, int range, IntFunction<ChunkStatus> statusByRange,
        CallbackInfoReturnable<CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.ChunkLoadingFailure>>> cir
    ) {
        if (range <= 0) return; // No neighbors needed anyway

        ChunkPos center = centerHolder.getPos();
        if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(center.x, center.z)) return;

        // For shipyard chunks, skip neighbor gathering entirely.
        // Request only the center chunk at the status required for distance 0.
        ChunkStatus requiredStatus = statusByRange.apply(0);
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> centerFuture =
            centerHolder.getOrScheduleFuture(requiredStatus, (ChunkMap) (Object) this);

        cir.setReturnValue(
            centerFuture.thenApply(result -> result.mapLeft(List::of))
        );
    }

    /**
     * Prevent vanilla from creating ChunkHolders for shipyard positions that only have
     * propagated ticket levels (> 33).
     *
     * When a ship chunk gets a level-33 (FULL) ticket, vanilla's DistanceManager propagates
     * the level outward: neighbors get 34, 35, ... up to MAX_LEVEL (~45). Each of these
     * creates a ChunkHolder, resulting in ~700 holders per single-block ship. With 100 ships,
     * that's 70,000+ unnecessary ChunkHolders that waste memory and slow down tick processing.
     *
     * VS2 only needs ChunkHolders for chunks with direct tickets (level 33). Any shipyard
     * position with level > 33 is purely from propagation and doesn't need a holder.
     */
    @Inject(
        method = "updateChunkScheduling",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vs$skipPropagatedShipyardHolders(
        long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel,
        CallbackInfoReturnable<ChunkHolder> cir
    ) {
        // Only intercept when no existing holder and level is propagated (> FULL = 33)
        if (holder != null) return;
        if (newLevel <= ChunkLevel.byStatus(FullChunkStatus.FULL)) return;

        int x = ChunkPos.getX(chunkPos);
        int z = ChunkPos.getZ(chunkPos);
        if (VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(x, z)) {
            cir.setReturnValue(null);
        }
    }
}
