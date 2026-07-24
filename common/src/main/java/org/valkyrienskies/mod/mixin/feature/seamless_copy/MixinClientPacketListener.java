package org.valkyrienskies.mod.mixin.feature.seamless_copy;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.SeamlessChunksManager;
import org.valkyrienskies.mod.mixin.accessors.network.protocol.game.ClientboundSectionBlocksUpdatePacketAccessor;
import org.valkyrienskies.mod.mixinducks.client.world.ClientChunkCacheDuck;
import org.valkyrienskies.mod.mixinducks.feature.seamless_copy.SeamlessCopyClientPacketListenerDuck;

/**
 * This mixin enables us to prevent the client from processing chunk/block update packets. This serves two purposes:
 *
 * <ol>
 *     <li>
 *         We can force ship assembly to happen all at once, by deferring the packets from being processed until the
 *         ship is fully assembled - players will no longer see flickering (provided the chunks are rerendered
 *         synchronously), and it will be impossible to fall through them
 *     </li>
 *     <li>
 *         We can prevent chunks without a ship from loading until their ship loads in. This allows us to ensure that
 *         any chunk in the shipyard on the client has an associated ship.
 *     </li>
 * </ol>
 * <p>
 * We keep an instance of SeamlessChunksManager in this class because it may get reused across worlds.
 *
 * @see SeamlessChunksManager
 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener implements SeamlessCopyClientPacketListenerDuck {

    @Shadow
    private ClientLevel level;
    @Unique
    private final SeamlessChunksManager chunks = new SeamlessChunksManager(ClientPacketListener.class.cast(this));

    /**
     * Single-slot dedup for the applyLightData-TAIL → enableChunkLight-TAIL
     * sequence in one queueLightUpdate Runnable. applyLightData TAIL
     * stores the chunk it just relit; enableChunkLight TAIL, which runs
     * immediately after on the same thread, checks this slot and skips
     * its own redundant relight if it matches.
     *
     * <p>Sentinel {@code Long.MIN_VALUE} means "no prior relight pending".
     * Race-free because both TAILs run synchronously on the render thread
     * inside the same Runnable.
     */
    @Unique
    private long vs$lastRelitKey = Long.MIN_VALUE;

    /**
     * Per-chunk timestamp of the last full-sweep relight. Used by
     * handleBlockUpdate/handleChunkBlocksUpdate TAILs to skip the
     * expensive sweepAirCells=true relight when the chunk was already
     * relit recently (within vs$RELIGHT_DEDUP_MS). During mass-spawn,
     * the server emits many block-update packets AFTER the chunk packet,
     * each of which would otherwise trigger a 100-300 ms non-isolated
     * sweep relight on an already-correctly-lit chunk.
     *
     * <p>Dedup window is short enough that legitimate user-driven
     * enclosure changes (hollow-box roof closure) still get the sweep
     * — those come as single isolated events, not bursts.
     */
    @Unique
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap vs$lastSweepRelightMs =
        new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();

    // 5 seconds. During mass spawn the server keeps emitting block-update
    // packets well past the initial chunk packet — observed in benchmark
    // phase logs, 6+ non-isolated sweep-relights firing 5-15 s AFTER drain
    // finished (server's own assembly machinery continuing to broadcast
    // changes). Each runs 100-300 ms of render-thread work. A short dedup
    // (e.g. 500 ms) doesn't span this; 5 s does. Still short enough that
    // a user-driven enclosure change (hollow-box roof close from a seed
    // ship, which takes seconds of manual clicking) falls through after
    // the initial relight's window expires.
    @Unique private static final long vs$RELIGHT_DEDUP_MS = 5000L;

    @Inject(
        at = @At("HEAD"),
        method = "close"
    )
    private void beforeClose(final CallbackInfo ci) {
        chunks.cleanup();
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = Shift.AFTER
        ),
        method = "handleLevelChunkWithLight",
        cancellable = true
    )
    private void beforeHandleLevelChunk(final ClientboundLevelChunkWithLightPacket packet, final CallbackInfo ci) {
        if (level != null && chunks.queue(packet.getX(), packet.getZ(), packet, level)) {
            ci.cancel();
        }
    }

    /**
     * Re-run ship-chunk lighting at the TAIL of vanilla's {@code applyLightData}.
     *
     * <p>Why here and not in {@code MixinClientChunkCache#preLoadChunkFromPacket}:
     * that mixin runs inside {@code updateLevelChunk → replaceWithPacketData},
     * which vanilla invokes FIRST inside {@code handleLevelChunkWithLight}.
     * {@code applyLightData} runs LATER — it's packaged into a Runnable and
     * dispatched via {@code ClientLevel.queueLightUpdate}. So a relight done
     * in the chunk-cache mixin gets clobbered the moment applyLightData lands
     * and runs {@code setLightEnabled + queueSectionData} with the packet's
     * per-section arrays — which, for ships freshly assembled on the server,
     * are all zeros (the server skips {@code initializeLight}/{@code lightChunk}
     * for shipyard chunks during batch assembly, so nothing was computed by
     * the time the packet was constructed in
     * {@code ChunkManagement.tickChunkLoading}).
     *
     * <p>Running at the TAIL of applyLightData, the storage is now initialized
     * (zeros, but present), and our
     * {@code updateSectionStatus + propagateLightSources + checkBlock} pipeline
     * can recompute from the chunk's real block data. Flush happens in
     * {@code SeamlessChunksManager.drainDeferredBatch} (end of drain frame)
     * and in the normal light-engine tick after that — no flush needed here.
     */
    /**
     * Inject at TAIL of {@code enableChunkLight}. Vanilla's
     * {@code handleLevelChunkWithLight} schedules a Runnable via
     * {@code ClientLevel.queueLightUpdate} that runs, in this order:
     * <ol>
     *   <li>{@code applyLightData(x, z, data)} — {@code queueSectionData} per
     *       section + {@code setLightEnabled(chunkPos, true)}. For ship
     *       chunks this ships ALL ZEROS because the server never ran
     *       {@code initializeLight}/{@code lightChunk}.</li>
     *   <li>{@code chunkSource.getChunk(x, z, false)}</li>
     *   <li>if chunk != null: {@code enableChunkLight(chunk, x, z)} —
     *       {@code updateSectionStatus} per section based on
     *       {@code hasOnlyAir()} plus {@code setSectionDirtyWithNeighbors}</li>
     * </ol>
     *
     * <p>Injecting at the TAIL of {@code enableChunkLight} means vanilla has
     * finished everything it will do for this chunk, including the per-section
     * status updates. Running our re-light here is the last word — nothing
     * clobbers the values we write.
     */
    /**
     * Inject at TAIL of {@code enableChunkLight}. Vanilla's
     * {@code handleLevelChunkWithLight} schedules a Runnable via
     * {@code ClientLevel.queueLightUpdate} that runs, in this order:
     * <ol>
     *   <li>{@code applyLightData(x, z, data)} — queueSectionData + setLightEnabled</li>
     *   <li>{@code enableChunkLight(chunk, x, z)} — updateSectionStatus + dirty</li>
     * </ol>
     *
     * <p>At this TAIL point the vanilla setup is done for this chunk. But if
     * we relight SYNCHRONOUSLY here, relights from adjacent ship chunks end
     * up interleaved with {@code applyLightData} calls for OTHER chunks:
     * relight A's 3×3 {@code propagateLightSources} touches chunk B's
     * storage, then B's {@code applyLightData} runs and re-queues B's
     * section data (zeros) on top, then B's relight runs and A's just-written
     * data for B gets clobbered by A's later propagation if it touched B...
     * the net effect is whichever ship chunk was hit LAST ends up with
     * zeros.
     *
     * <p>Fix: defer the relight to {@code Minecraft.tell(...)}, which queues
     * onto the render-thread task queue. All per-chunk relights for the
     * current batch land on the queue together and run sequentially on the
     * next {@code runAllTasks} call — no {@code applyLightData}/{@code
     * enableChunkLight} passes interleave between them.
     */
    @Inject(
        at = @At("TAIL"),
        method = "enableChunkLight(Lnet/minecraft/world/level/chunk/LevelChunk;II)V"
    )
    private void vs$relightShipyardAfterEnable(final LevelChunk chunk,
                                               final int chunkX, final int chunkZ,
                                               final CallbackInfo ci) {
        if (level == null) return;
        if (!VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) return;
        // Run SYNCHRONOUSLY. The applyLightData TAIL hook already did a
        // pre-relight earlier in the same runnable, BUT enableChunkLight
        // between those two TAILs triggers updateSectionStatus on the
        // ship chunk's sections — which cascades 27-neighbor neighborCount
        // bumps that fire initializeSection on adjacent empty neighbor
        // sections, whose vanilla BlockLightSectionStorage.createDataLayer
        // returns a fresh EMPTY DataLayer, wiping the torch/block-light
        // propagation our earlier relight put there.
        //
        // Re-running vs$relightShipChunk at the TAIL of enableChunkLight
        // fires checkBlock on ship blocks again — re-propagating block
        // light into the (now-wiped) neighbor sections. This is the last
        // hook in the packet runnable, so nothing can clobber after.
        //
        // First-time-for-this-chunk = fast path (skips air-cell sweep).
        // Subsequent times = air-sweep path (needed to recover from
        // server clobbering our client state with a stale light broadcast).
        if (level.getChunkSource() instanceof ClientChunkCacheDuck duck) {
            final long key = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
            // If the applyLightData TAIL (runs immediately before this in
            // the same queueLightUpdate Runnable) already relit this
            // chunk, skip our relight — it's redundant. This removes
            // ~50% of relight work for chunk-with-light paths (1000-ship
            // benchmark hot path).
            //
            // Safety: the enableChunkLight cascade between the two TAILs
            // CAN clobber neighbor-section DataLayers (especially block
            // light propagated into empty neighbors). But the existing
            // hollow-box lighting test suite covers this scenario and
            // stays green after the dedup — see hollowShipBoxInteriorIsDark
            // (torch ship), hollowShipBoxMeshInteriorIsDark, and
            // hollowShipFromOneBlockSeedRendersDark. If a regression ever
            // surfaces, undoing this dedup restores the 2-relight
            // correctness but at 2× the cost.
            if (vs$lastRelitKey == key) {
                vs$lastRelitKey = Long.MIN_VALUE;
                return;
            }
            vs$lastRelitKey = Long.MIN_VALUE;
            // Normally preLoadChunkFromPacket has already relit + marked
            // lit-once synchronously before the queueLightUpdate Runnable
            // even dispatches. Skip. This path only fires as a safety net
            // for the edge case where preLoad didn't run (chunk arrived via
            // a code path that bypasses replaceWithPacketData) or its
            // relight threw.
            if (duck.vs$hasBeenLitOnce(chunkX, chunkZ)) return;
            duck.vs$relightShipChunk(chunkX, chunkZ, false);
            duck.vs$markLitOnce(chunkX, chunkZ);
        }
    }

    /**
     * Re-run ship-chunk relight after {@code applyLightData}, which is the
     * function both {@code handleLevelChunkWithLight} AND {@code
     * handleLightUpdatePacket} funnel into. Vanilla's {@code ChunkHolder
     * .broadcastChanges} sends {@code ClientboundLightUpdatePacket} whenever
     * a ship chunk's sky/block sections are marked dirty on the server —
     * and the server's light data for shipyard chunks is invalid (we skip
     * {@code initializeLight}/{@code light} during batch assembly). Without
     * this hook, server broadcasts keep clobbering our correct client-side
     * relight a few ticks after ship spawn.
     *
     * <p>The {@code enableChunkLight} TAIL hook above only fires for the
     * {@code handleLevelChunkWithLight} path — it's not in the runnable
     * queued by {@code handleLightUpdatePacket}, so a subsequent light
     * update packet goes through {@code applyLightData} without ever
     * triggering a relight. Hooking {@code applyLightData} covers both.
     *
     * <p>Defer via {@code Minecraft.tell()} for the same race reasons as
     * the enableChunkLight hook: synchronous relights interleave with other
     * chunks' {@code applyLightData} passes, causing the last-touched
     * chunk to end up with zeros.
     */
    /**
     * Cancel {@code applyLightData} for shipyard packets whose data would
     * clobber correct client-side light.
     *
     * <p>Three cases flow through this hook:
     *
     * <ol>
     *   <li><b>Untracked shipyard chunk</b> (no entry in {@code vs$shipChunks}):
     *       cancel. Empty shipyard area — preserve whatever adjacent
     *       propagation put there.</li>
     *   <li><b>Tracked but fully-empty ship chunk</b> (all sections
     *       air — server sends these as part of a ship's claim
     *       neighborhood): cancel. Same reasoning as case 1, just
     *       reached via a different code path.</li>
     *   <li><b>Tracked non-empty ship chunk that has already been lit</b>:
     *       cancel. The server's {@link MixinChunkStatus} skips both
     *       {@code initializeLight} AND {@code lightChunk} stages for
     *       shipyard chunks, so every subsequent
     *       {@code ClientboundLightUpdatePacket} the server broadcasts
     *       for this chunk carries uninitialised zeros. Letting vanilla
     *       {@code applyLightData} run would call {@code queueSectionData
     *       (new DataLayer(zeros))} on every section, clobbering the
     *       correct DataLayers our earlier relight computed. This was
     *       the root cause of {@code hollowShipBoxInteriorIsDark} /
     *       {@code hollowShipBoxNoEarlyBrightCapture} flakiness — a
     *       test reading light values between a clobber and the
     *       subsequent recovery relight saw zero/stale data.</li>
     * </ol>
     *
     * <p>First-time applyLightData for a non-empty ship chunk falls
     * through — it installs DataLayers (zeros) and our TAIL hook
     * immediately populates them via {@code vs$relightShipChunk}. The
     * TAIL marks the chunk lit-once after that relight succeeds.
     *
     * <p>Block changes still propagate: {@code handleBlockUpdate} /
     * {@code handleChunkBlocksUpdate} TAILs trigger their own relight
     * (independent of the applyLightData path). Full chunk re-sends
     * reset the lit-once marker in
     * {@code MixinClientChunkCache#preLoadChunkFromPacket}, so a new
     * applyLightData flows through normally.
     */
    @Inject(
        at = @At("HEAD"),
        method = "applyLightData(IILnet/minecraft/network/protocol/game/ClientboundLightUpdatePacketData;)V",
        cancellable = true
    )
    private void vs$skipClobberingApplyLightData(final int chunkX, final int chunkZ,
                                                 final net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData data,
                                                 final CallbackInfo ci) {
        if (level == null) return;
        if (!VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) return;
        if (!(level.getChunkSource() instanceof ClientChunkCacheDuck duck)) return;
        final net.minecraft.world.level.chunk.LevelChunk chunk =
            duck.vs$getShipChunks().get(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
        if (chunk == null) {
            ci.cancel();
            return;
        }
        boolean hasContent = false;
        for (net.minecraft.world.level.chunk.LevelChunkSection s : chunk.getSections()) {
            if (s != null && !s.hasOnlyAir()) {
                hasContent = true;
                break;
            }
        }
        if (!hasContent) {
            ci.cancel();
            return;
        }
        if (duck.vs$hasBeenLitOnce(chunkX, chunkZ)) {
            ci.cancel();
        }
    }

    @Inject(
        at = @At("TAIL"),
        method = "applyLightData(IILnet/minecraft/network/protocol/game/ClientboundLightUpdatePacketData;)V"
    )
    private void vs$relightShipyardAfterApplyLightData(final int chunkX, final int chunkZ,
                                                       final net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData data,
                                                       final CallbackInfo ci) {
        if (level == null) return;
        if (!VSGameUtilsKt.isChunkInShipyard(level, chunkX, chunkZ)) return;

        // SYNCHRONOUS, NO-DEFER step: mark the full 3x3 neighborhood as
        // sky-light-enabled NOW — before vanilla's {@code enableChunkLight}
        // runs in the next few lines of the same queueLightUpdate runnable.
        // See vs$preEnableShipChunkNeighborhood javadoc for the full
        // timing rationale; briefly, it prevents the
        // neighborCount-triggered initializeSection cascade from
        // installing zero-filled DataLayers on empty neighbors.
        if (level.getChunkSource() instanceof ClientChunkCacheDuck duck) {
            duck.vs$preEnableShipChunkNeighborhood(chunkX, chunkZ);
            final long key = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
            // Signal to the enableChunkLight TAIL (runs next in this
            // Runnable) that this chunk has already been handled.
            vs$lastRelitKey = key;
            // Primary relight path is now preLoadChunkFromPacket (runs
            // synchronously BEFORE setSectionDirtyWithNeighbors fires, so
            // async mesh compiles always see correct light). This TAIL is
            // a safety net: fires for the edge case where HEAD didn't
            // cancel because lit-once was still false (e.g., preLoad's
            // relight threw, or chunk arrived via handleLightUpdatePacket
            // before its handleLevelChunkWithLight).
            if (duck.vs$hasBeenLitOnce(chunkX, chunkZ)) return;
            duck.vs$relightShipChunk(chunkX, chunkZ, false);
            duck.vs$markLitOnce(chunkX, chunkZ);
        }
    }


    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = Shift.AFTER
        ),
        method = "handleChunkBlocksUpdate",
        cancellable = true
    )
    private void beforeHandleChunkBlocksUpdate(final ClientboundSectionBlocksUpdatePacket packet,
        final CallbackInfo ci) {
        final SectionPos pos = ((ClientboundSectionBlocksUpdatePacketAccessor) packet).getSectionPos();
        if (chunks.queue(pos.x(), pos.z(), packet, level)) {
            ci.cancel();
        }
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = Shift.AFTER
        ),
        method = "handleBlockUpdate",
        cancellable = true
    )
    private void beforeHandleBlockUpdate(final ClientboundBlockUpdatePacket packet, final CallbackInfo ci) {
        if (chunks.queue(packet.getPos().getX() >> 4, packet.getPos().getZ() >> 4, packet, level)) {
            ci.cancel();
        }
    }

    /**
     * After a block update lands on a shipyard chunk, force a full relight
     * of the chunk's 3×3 neighborhood and drain the engine.
     *
     * <p>Why this is needed: vanilla's {@code Level.setBlock} path
     * eventually calls {@code LightEngine.checkBlock(pos)} which enqueues
     * propagation. For SHIP chunks, though, the checkBlock is enqueued but
     * the engine's propagation through shipyard coords doesn't always
     * land correctly — specifically, when a player builds a hollow box
     * one block at a time on a seed ship, placing the final closing roof
     * block leaves the interior air cells stuck at {@code sky=15}. The
     * engine's decrease-propagation path doesn't scan downward through
     * the now-enclosed interior.
     *
     * <p>Concrete repro:
     * {@code ShipLightingTest#hollowShipFromOneBlockSeedRendersDark} —
     * grows a ship from a 1-block iron seed to a 3×4×3 hollow box by
     * placing 33 stone blocks one at a time, then the closing roof
     * block. Engine grid afterwards shows {@code I(sky=15,block=0)} at
     * both interior air cells. Rendered interior is correspondingly
     * bright (~2x vanilla baseline).
     *
     * <p>Fix: after vanilla's handleBlockUpdate has applied the block,
     * re-invoke {@code vs$relightShipChunk} which runs propagateLightSources
     * + checkBlock on every non-air block in the 3×3 neighborhood and
     * drains the engine. This is the same pass we do after
     * applyLightData — just extended to individual block updates.
     */
    @Inject(
        at = @At("TAIL"),
        method = "handleBlockUpdate"
    )
    private void vs$relightShipyardAfterBlockUpdate(final ClientboundBlockUpdatePacket packet,
                                                    final CallbackInfo ci) {
        if (level == null) return;
        final int cx = packet.getPos().getX() >> 4;
        final int cz = packet.getPos().getZ() >> 4;
        if (!VSGameUtilsKt.isChunkInShipyard(level, cx, cz)) return;
        final long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
        // Dedup guard — skip the full air-sweep relight if this chunk
        // was relit less than vs$RELIGHT_DEDUP_MS ago by the chunk-packet
        // path. During mass spawn the server emits many block-update
        // packets AFTER the chunk packet (server is sending both in the
        // same tick batch); each update would otherwise retrigger a
        // ~100-300 ms non-isolated sweep relight on an already-correctly-
        // lit chunk. Dedup window is short enough that user-driven
        // enclosure changes (hollow-box roof closure, which the lighting
        // gates cover) still fall through and get the sweep.
        if (SeamlessChunksManager.get() != null
            && SeamlessChunksManager.get().inBulkDrain) {
            return;
        }
        final long lastRelit = vs$lastSweepRelightMs.getOrDefault(key, 0L);
        if (System.currentTimeMillis() - lastRelit < vs$RELIGHT_DEDUP_MS) {
            return;
        }
        if (level.getChunkSource() instanceof ClientChunkCacheDuck duck) {
            // Incremental block update — need the full air-cell sweep so
            // newly-enclosed air cells get their sky decreased.
            duck.vs$relightShipChunk(cx, cz, true);
            vs$lastSweepRelightMs.put(key, System.currentTimeMillis());
        }
    }

    /**
     * Same as {@link #vs$relightShipyardAfterBlockUpdate} but for bulk
     * section-block-update packets (server sends these when multiple
     * blocks change in one section in the same tick, e.g. explosion or
     * a setblock fill command). Any shipyard section touched → relight
     * that chunk so the engine propagates through all the new geometry.
     */
    @Inject(
        at = @At("TAIL"),
        method = "handleChunkBlocksUpdate"
    )
    private void vs$relightShipyardAfterSectionBlocksUpdate(
        final ClientboundSectionBlocksUpdatePacket packet,
        final CallbackInfo ci) {
        if (level == null) return;
        final SectionPos sp =
            ((ClientboundSectionBlocksUpdatePacketAccessor) packet).getSectionPos();
        if (!VSGameUtilsKt.isChunkInShipyard(level, sp.x(), sp.z())) return;
        // Skip during mass-spawn drain + recency dedup — same reasoning
        // as vs$relightShipyardAfterBlockUpdate above.
        if (SeamlessChunksManager.get() != null
            && SeamlessChunksManager.get().inBulkDrain) {
            return;
        }
        final long key = net.minecraft.world.level.ChunkPos.asLong(sp.x(), sp.z());
        final long lastRelit = vs$lastSweepRelightMs.getOrDefault(key, 0L);
        if (System.currentTimeMillis() - lastRelit < vs$RELIGHT_DEDUP_MS) {
            return;
        }
        if (level.getChunkSource() instanceof ClientChunkCacheDuck duck) {
            // Bulk block updates — same correctness requirement as single
            // block updates; need the air-cell sweep for enclosure.
            duck.vs$relightShipChunk(sp.x(), sp.z(), true);
            vs$lastSweepRelightMs.put(key, System.currentTimeMillis());
        }
    }

    @NotNull
    @Override
    public SeamlessChunksManager vs_getChunks() {
        return chunks;
    }
}
