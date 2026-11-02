package org.valkyrienskies.mod.common.assembly

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.ChunkPos
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ChunkClaim
import org.valkyrienskies.core.util.pollUntilEmpty
import org.valkyrienskies.mod.api.vsApi
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.isChunkInShipyard
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.networking.PacketRestartChunkUpdates
import org.valkyrienskies.mod.common.networking.PacketStopChunkUpdates
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixinducks.feature.seamless_copy.SeamlessCopyClientPacketListenerDuck
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck
import org.valkyrienskies.mod.util.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * @see createNewShipWithBlocks
 * @see org.valkyrienskies.mod.mixin.feature.seamless_copy.MixinClientPacketListener
 */
class SeamlessChunksManager(private val listener: ClientPacketListener) {

    companion object {
        private val logger by logger()

        @JvmStatic
        fun get() = (Minecraft.getInstance().connection as? SeamlessCopyClientPacketListenerDuck)?.vs_getChunks()
    }

    private val shipQueuedUpdates = ConcurrentHashMap<ChunkClaim, ConcurrentLinkedQueue<Packet<*>>>()
    private val queuedUpdates = ConcurrentHashMap<ChunkPos, ConcurrentLinkedQueue<Packet<*>>>()
    private val stalledChunks = LongOpenHashSet()
    // Guard flag to prevent re-entrant throttling when drainDeferredBatch dispatches packets
    private var dispatching = false

    init {
        with(vsCore.simplePacketNetworking) {
            PacketStopChunkUpdates::class.registerClientHandler { (chunks) ->
                chunks.forEach { stalledChunks.add(it.toMinecraft().toLong()) }
            }
            PacketRestartChunkUpdates::class.registerClientHandler { packet ->
                Minecraft.getInstance().execute {
                    onRestartUpdates(packet)
                }
            }
        }

        vsApi.shipLoadEventClient.on { ev ->
            onShipLoad(ev.ship)
        }
        vsApi.shipUnloadEventClient.on { ev ->
            onShipUnload(ev.ship)
        }
    }

    private fun onShipLoad(ship: ClientShip) {
        val packets = shipQueuedUpdates.remove(ship.chunkClaim)
        if (!packets.isNullOrEmpty()) {
            logger.debug("Executing ${packets.size} deferred updates for ship ID=${ship.id} at ${ship.chunkClaim}")
            packets.pollUntilEmpty { deferredDispatch.add(it) }
        }
        val player = Minecraft.getInstance().player
        if (player is PlayerKnownShipsDuck) {
            player.vs_addKnownShip(ship.id)
        }
    }

    // Drop any packets queued for a ship's claim once the ship goes away.
    // Without this, packets that arrived between the final tick-drain and
    // the unload event sit in shipQueuedUpdates forever — each new ship
    // allocates a fresh claim so they'd never be triggered — which both
    // bloats memory and costs hash lookups on every subsequent queue() call.
    private fun onShipUnload(ship: ClientShip) {
        shipQueuedUpdates.remove(ship.chunkClaim)
    }

    private fun onRestartUpdates(packet: PacketRestartChunkUpdates) {
        val (chunks) = packet

        chunks.forEach { p ->
            val pos = p.toMinecraft()
            stalledChunks.remove(pos.toLong())
            val packets = queuedUpdates.remove(pos)
            if (!packets.isNullOrEmpty()) {
                logger.debug("Executing ${packets.size} deferred updates at <${pos.x}, ${pos.z}>")
                packets.pollUntilEmpty { deferredDispatch.add(it) }
            }
        }
    }

    // Time budget per frame for processing deferred chunk packets (in milliseconds).
    // Adapts automatically to frame rate: at 60fps we get ~16ms/frame so 5ms is ~30%.
    //
    // Scales up with backlog: when the queue has >= BACKLOG_FAST_PATH items (e.g.
    // a 1000-ship perf-test spawn just landed), we temporarily widen the budget to
    // BACKLOG_BUDGET_MS. That costs a frame hitch but settles the world in ~3s
    // instead of ~6s, which matters far more than one bad frame.
    // Threshold kept low: ship packets trickle in at ~5-10/frame during a bulk
    // spawn (the server sends them as each ship's chunk is finalised), so if we
    // wait for the queue to pile up to 100 the fast path never fires. 10 is
    // enough to detect "something beyond normal-gameplay noise is happening"
    // without tripping on occasional single-ship events.
    private val BACKLOG_FAST_PATH = 10

    // Queue of packets waiting to be dispatched, processed in batches each frame
    private val deferredDispatch = ConcurrentLinkedQueue<Packet<*>>()

    /**
     * Number of chunk packets still waiting to reach the vanilla client packet handler.
     * Useful to tests that want to wait for true settle — a count of 0 here, combined
     * with the ship count matching the server's, means the client is caught up.
     */
    fun pendingDeferredCount(): Int = deferredDispatch.size

    fun pendingShipChunkUpdateCount(): Int =
        shipQueuedUpdates.values.sumOf { it.size }

    fun pendingStalledChunkUpdateCount(): Int =
        queuedUpdates.values.sumOf { it.size }

    fun pendingShipClaimCount(): Int = shipQueuedUpdates.size

    fun stalledChunkCount(): Int = stalledChunks.size

    /**
     * True while {@link #drainDeferredBatch()} is running through its inner loop.
     * Per-chunk hot paths (notably MixinClientChunkCache.preLoadChunkFromPacket's
     * light-flush) can check this to defer expensive cleanup until the whole
     * batch has been applied, then run it once at the end of the drain.
     */
    @JvmField
    var inBulkDrain: Boolean = false

    /**
     * Called once per render frame from setupRender to process deferred chunk packets.
     * This is the ONLY place that drains the deferred queue, ensuring exactly one
     * batch per frame regardless of how many packets arrive.
     *
     * Uses a time budget instead of a fixed count so throughput scales with how
     * fast the client can actually process chunks (affected by render chunk pooling,
     * light engine optimizations, etc.).
     */
    fun drainDeferredBatch() {
        if (deferredDispatch.isEmpty()) return
        dispatching = true
        inBulkDrain = true
        // Tiered per-frame budget for processing the deferred chunk queue:
        //   - 5ms idle: smooth gameplay, chunks trickle in behind ships
        //   - 20ms small backlog: batch-assemble ramp
        //   - 50ms medium backlog: steady-state bulk spawn
        //   - 500ms huge backlog: drain EVERYTHING this frame
        //
        // The 500ms "drain-it-all" tier exists because the chunk pipeline
        // bypass + sparse-section compile mixin dropped per-chunk CPU from
        // ~900us warm down to closer to 100us. At that cost, 1000 chunks
        // drain in ~100ms — one frame hitch — and then EVERYTHING is
        // visible in the next render. Waiting 20-50ms/frame to stay smooth
        // makes the ships take 2+ seconds to appear, which is a worse
        // user experience than a single visible hitch.
        val size = deferredDispatch.size
        // Peak-burst tier tuned DOWN from 500 ms to 100 ms. A 500 ms budget
        // produced multi-second visible freezes on 1000-ship spawn (the
        // per-packet cost the old budget assumed — ~100 µs — is far off
        // from the real cost of ~2-3 ms once relight + setLightEnabled
        // cascade + updateSectionStatus + drain are counted). With 100 ms
        // we still finish the 5000-packet drain in ~50 frames (~1 s at
        // 60 fps), trading a single 500 ms freeze for 50 × 100 ms hitches
        // that each stay below the "noticeable freeze" threshold while
        // letting the render loop keep sampling input.
        // Peak-burst tier tuned from 500 ms to 100 ms. 1000-ship benchmark
        // data: 500 ms budget → 11.5 s worst-frame freeze; 100 ms → 5.3 s
        // (note: 50 ms showed 8.6 s, matching or worse than 100 ms — the
        // dominant hitch at this scale isn't the packet dispatch but the
        // light-queue drain we do before sync-compile in
        // MixinLevelRendererVanilla, which isn't itself bounded yet).
        // Peak-burst tier tuned from 100 ms → 40 ms. Drain phase logs
        // showed engineFlush was inflating the full drain to 500 ms on
        // 1000-ship spawn; with the engineFlush removed entirely, dispatch
        // cleanly fits in 40 ms per frame. Trade-off: same packet count
        // (~5000) now spreads over ~1.5× more frames, so END-TO-END grows
        // by ~1 s. In return p99 drops from ~100 ms (budget-dominated) to
        // ~50 ms, which is what the user felt as "choppy."
        val perf = VSGameConfig.CLIENT.Performance
        val baseBudget = perf.shipChunkPacketBaseBudgetMs.coerceIn(1, 200).toLong()
        val backlogBudget = perf.shipChunkPacketBacklogBudgetMs.coerceIn(1, 300).toLong()
        val largeBacklogBudget = perf.shipChunkPacketLargeBacklogBudgetMs.coerceIn(1, 500).toLong()
        val budget = when {
            size >= 200 -> largeBacklogBudget
            size >= 50 -> 30L
            size >= BACKLOG_FAST_PATH -> backlogBudget
            else -> baseBudget
        }
        val deadline = System.currentTimeMillis() + budget
        val frameStart = System.nanoTime()
        var chunksProcessed = 0
        try {
            while (deferredDispatch.isNotEmpty()) {
                val packet = deferredDispatch.peek() ?: break
                // Check time budget before processing expensive chunk packets
                if (packet is ClientboundLevelChunkWithLightPacket && System.currentTimeMillis() >= deadline) {
                    return
                }
                deferredDispatch.poll()
                if (packet is ClientboundLevelChunkWithLightPacket) chunksProcessed++
                when (packet) {
                    is ClientboundBlockUpdatePacket -> listener.handleBlockUpdate(packet)
                    is ClientboundSectionBlocksUpdatePacket -> listener.handleChunkBlocksUpdate(packet)
                    is ClientboundLevelChunkWithLightPacket -> listener.handleLevelChunkWithLight(packet)
                    else -> throw IllegalStateException("Didn't know how to dispatch packet: ${packet::class}")
                }
            }
        } finally {
            dispatching = false
            inBulkDrain = false
            // After processing all chunks this frame, flush pending light
            // updates — MixinClientChunkCache skips its per-chunk
            // runLightUpdates loop while inBulkDrain is true to avoid doing the
            // same O(pending) work once per chunk, so the engine queue here
            // can be huge.
            //
            // Previously unbounded, "one pass." On a 1000-ship spawn that
            // single call was doing 200-500 ms of propagation per frame and
            // was the dominant max-frame contributor (FrameTimeTracker saw
            // 650+ ms with GC=0). Budget-bounded at 15 ms so the work spills
            // across frames. Safe to partial-drain: engine state is
            // eventually consistent, and MixinLevelRendererVanilla's
            // render-frame drain plus vanilla pollLightUpdates both pick
            // up the remainder.
            // Previously: while (runLightUpdates() > 0) { ... deadline ... }
            // phase instrumentation showed a SINGLE runLightUpdates() call
            // running 400 ms inside its internal batch loop. The engine
            // drains in big internal chunks that don't honour our deadline
            // — the deadline check only fires BETWEEN calls, not inside one.
            // So the "bounded loop" was effectively unbounded on mass spawn.
            //
            // Fix: skip this eager flush entirely. Vanilla ClientLevel.tick
            // already polls the light-update queue every tick (we cap it at
            // 10 via MixinClientLevelPollCap) and
            // MixinLevelRendererVanilla.vs$addShipVisibleChunks runs another
            // bounded engine drain per render-frame. Those two together
            // propagate over a handful of frames at low cost, trading
            // "instant light correctness after mass spawn" for "no 400 ms
            // render-thread freeze." User sees ships appear 1-2 frames
            // earlier than light settles in edge cases — far better than
            // a visible hitch.
            val engineFlushMs = 0L
            val totalMs = (System.nanoTime() - frameStart) / 1_000_000
            // Slow-drain diagnostic — when this frame's drain cost >80 ms,
            // dump dispatch-vs-engine-flush split so we can tell which
            // subphase dominated.
            if (totalMs > 300L) {
                logger.info(
                    "[slow-drain] total={}ms dispatch={}ms engineFlush={}ms chunks={} budget={}ms left={}",
                    totalMs, totalMs - engineFlushMs, engineFlushMs,
                    chunksProcessed, budget, deferredDispatch.size)
            }
            // Only log when a meaningful burst is happening so normal gameplay
            // stays quiet. Useful for diagnosing slow-settle complaints.
            if (chunksProcessed >= 50) {
                val perChunkUs = (System.nanoTime() - frameStart) / chunksProcessed / 1000
                logger.debug("SeamlessChunksManager drain: {} chunks in {}ms ({}us/chunk, budget {}ms, queue left {})",
                    chunksProcessed, totalMs, perChunkUs, budget, deferredDispatch.size)
            }
        }
    }

    fun cleanup() {
        stalledChunks.clear()
        queuedUpdates.clear()
        shipQueuedUpdates.clear()
        deferredDispatch.clear()
    }

    /**
     * Attempt to defer a chunk update
     *
     * @return true if the chunk update was deferred, false if otherwise
     */
    fun queue(chunkX: Int, chunkZ: Int, packet: Packet<*>, level: ClientLevel): Boolean {
        // note, this will get re-called when we're processing the shipQueuedUpdates queue,
        // so if any updates in there are actually still stalled by a [PacketStopChunkUpdates] it will
        // be added to the queuedUpdates queue here (and vice versa)

        // The chunk is in the shipyard, but we don't know what ship
        if (level.isChunkInShipyard(chunkX, chunkZ) &&
            level.getShipManagingPos(chunkX, chunkZ) == null
        ) {
            logger.debug("Deferring ship update at <$chunkX, $chunkZ> for ${packet::class}")
            shipQueuedUpdates
                .computeIfAbsent(vsCore.newChunkClaimFromChunkPos(chunkX, chunkZ)) { ConcurrentLinkedQueue() }
                .add(packet)

            return true
        }

        // The chunk prevented from updating by a [PacketStopChunkUpdates]
        if (stalledChunks.contains(ChunkPos.asLong(chunkX, chunkZ))) {
            logger.debug("Deferring update at <$chunkX, $chunkZ> for ${packet::class}")
            queuedUpdates
                .computeIfAbsent(ChunkPos(chunkX, chunkZ)) { ConcurrentLinkedQueue() }
                .add(packet)

            return true
        }

        // Throttle shipyard chunk packets even when the ship is already loaded.
        // Without this, spawning 100 ships sends ~500 chunk packets that are all processed
        // in a single frame (each running toDenseVoxelUpdate with 4096 block lookups),
        // causing a massive client-side lag spike.
        // Skip this check when we're dispatching from the deferred queue (re-entrant call).
        if (!dispatching &&
            packet is ClientboundLevelChunkWithLightPacket &&
            level.isChunkInShipyard(chunkX, chunkZ)
        ) {
            deferredDispatch.add(packet)
            return true
        }

        logger.trace("Received update at <$chunkX, $chunkZ> for ${packet::class}")

        return false
    }
}
