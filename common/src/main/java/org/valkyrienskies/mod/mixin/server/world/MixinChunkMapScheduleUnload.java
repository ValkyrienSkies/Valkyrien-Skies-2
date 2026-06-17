package org.valkyrienskies.mod.mixin.server.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

/**
 * Fast-paths shipyard chunk unloads. Two interventions; they work together.
 *
 * <p><b>1. Fast scheduleUnload:</b> for shipyard chunks, skip the async save +
 * whenComplete cleanup chain entirely. Just remove the holder from pendingUnloads
 * and return. The per-chunk cost goes from "wait on save sync future, call
 * saveChunkIfNeeded, fire ChunkEvent.Unload, tear down block entity tickers,
 * clean up light/POI state" to "one hashmap remove."
 *
 * <p><b>2. Bulk evict at processUnloads HEAD:</b> when processUnloads runs, walk
 * the toDrop set once and pull out every shipyard entry: remove it from toDrop
 * (so MC's per-entry loop doesn't see it), remove it from updatingChunkMap
 * (freeing it for GC and shrinking the map MC iterates every tick). MC's natural
 * drain is rate-limited by the distance manager committing ticket changes in
 * batches, which spreads 62k cascaded holders across hundreds of ticks. Bulk
 * eviction lets the chunk map shrink in a single pass so ServerChunkCache.tickChunks
 * stops iterating stale holders.
 *
 * <p><b>Risks:</b> we never run the async unload-complete callback, which normally
 * removes the holder from pendingUnloads and runs per-chunk cleanup. For shipyard
 * chunks that's acceptable in practice: they hold almost no state (no players, no
 * real block entities in our use case, no POIs on a voxel island), and the holder
 * object itself is released so the JVM GCs its transitive references.
 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMapScheduleUnload {

    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @Shadow
    @Final
    private LongSet toDrop;

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "scheduleUnload", at = @At("HEAD"), cancellable = true)
    private void vs$fastScheduleUnloadForShipyard(final long chunkPos, final ChunkHolder holder,
                                                  final CallbackInfo ci) {
        final int cx = ChunkPos.getX(chunkPos);
        final int cz = ChunkPos.getZ(chunkPos);
        if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) return;

        // Shipyard chunks on their way out: skip the whole async save + cleanup
        // chain. The only bookkeeping we have to do before returning is the
        // pendingUnloads entry that processUnloads just added — otherwise it leaks.
        pendingUnloads.remove(chunkPos);
        ci.cancel();
    }

    /**
     * Tracks the {@link #toDrop} size at the last bulk-eviction pass.
     * If the set hasn't grown, there's nothing new to evict — skip the
     * full walk. This is what prevents the shutdown-hang spin: on worlds
     * with accumulated shipyard chunks, {@code processUnloads} is called
     * every tick across many stop-phase ticks, and without this guard
     * each call re-iterates the full toDrop set even though our first
     * eviction already cleared every shipyard entry.
     */
    @org.spongepowered.asm.mixin.Unique
    private int vs$lastToDropSize = -1;

    /**
     * Bulk-evict shipyard entries before MC's per-entry processUnloads loop runs.
     * MC's loop iterates the toDrop set at a rate that's bounded by how quickly
     * the DistanceManager commits ticket changes — with 62k cascaded shipyard
     * holders queued up, that stretches into hundreds of ticks while
     * `ServerChunkCache.tickChunks` keeps iterating them every tick. Pulling them
     * out of both toDrop and updatingChunkMap up front cuts the per-tick iteration
     * cost to the non-shipyard portion immediately.
     *
     * <p>On shutdown of a world with 1000+ ships: processUnloads fires on every
     * tick of stopServer()'s drain loop, and without the size-change guard
     * below we re-iterate the full toDrop each tick. 62k entries × 100s of
     * ticks = ~1000 CPU-seconds spinning on {@code LongOpenHashSet$SetIterator
     * .nextLong} — the user-visible "world-close hang". The size check makes
     * repeat calls an O(1) no-op once we've emptied the shipyard portion.
     */
    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void vs$bulkEvictShipyardAtHead(final java.util.function.BooleanSupplier shouldContinue,
                                            final CallbackInfo ci) {
        final int currentSize = toDrop.size();
        if (currentSize == 0) {
            vs$lastToDropSize = 0;
            return;
        }
        // Fast path: toDrop hasn't changed since last walk → we already
        // evicted every shipyard entry that was there. Skip the O(n)
        // iteration. If MC's own loop later removes entries from toDrop,
        // the size will shrink and we'll re-walk on that call — but the
        // walk will find no shipyard entries (we've been maintaining the
        // invariant) so it's still O(n) without evictions, then the size
        // stabilizes and subsequent calls are no-ops.
        if (currentSize == vs$lastToDropSize) return;

        final LongIterator it = toDrop.iterator();
        int evicted = 0;
        while (it.hasNext()) {
            final long chunkPos = it.nextLong();
            final int cx = ChunkPos.getX(chunkPos);
            final int cz = ChunkPos.getZ(chunkPos);
            if (!VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(cx, cz)) continue;
            updatingChunkMap.remove(chunkPos);
            pendingUnloads.remove(chunkPos);
            it.remove();
            evicted++;
        }
        // Trim the backing array any time we evict a non-trivial chunk
        // of entries. A LongOpenHashSet that peaked at 60k entries stays
        // at 60k capacity even after removal — every
        // iterator.nextLong() call walks empty slots until the next
        // occupied one, so vanilla's own per-tick processUnloads loop
        // walks 60k slots per call. Over hundreds of stopServer() ticks
        // that's minutes of hang (stack shows server RUNNABLE in
        // LongOpenHashSet$SetIterator.nextLong during world-save).
        //
        // Prior condition gated on `size() <= 500` which failed in
        // practice — 1000-ship worlds often have a few thousand
        // non-shipyard entries in toDrop alongside the shipyard ones,
        // so post-evict size stayed above 500 and trim never fired.
        // Now trims on any evict ≥ 100 regardless of remaining size.
        // trim() is O(size) so calling it on a now-small set is cheap;
        // the amortized cost over repeated processUnloads calls is
        // fine.
        if (evicted >= 100 && toDrop instanceof LongOpenHashSet los) {
            los.trim();
        }
        vs$lastToDropSize = toDrop.size();
    }
}
