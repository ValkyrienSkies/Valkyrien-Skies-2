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
import org.valkyrienskies.mod.common.networking.PacketRestartChunkUpdates
import org.valkyrienskies.mod.common.networking.PacketStopChunkUpdates
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixinducks.feature.seamless_copy.SeamlessCopyClientPacketListenerDuck
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck
import org.valkyrienskies.mod.util.logger
import java.util.Queue
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
    // Adapts automatically to frame rate: at 60fps we get ~16ms/frame so 5ms is ~30%;
    // at 30fps we get ~33ms/frame so 5ms is ~15%. This replaces the old fixed count
    // of 5 chunks/frame which was too slow for worlds with 1000+ ships.
    private val CHUNK_BUDGET_MS = 5L

    // Queue of packets waiting to be dispatched, processed in batches each frame
    private val deferredDispatch = ConcurrentLinkedQueue<Packet<*>>()

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
        val deadline = System.currentTimeMillis() + CHUNK_BUDGET_MS
        try {
            while (deferredDispatch.isNotEmpty()) {
                val packet = deferredDispatch.peek() ?: break
                // Check time budget before processing expensive chunk packets
                if (packet is ClientboundLevelChunkWithLightPacket && System.currentTimeMillis() >= deadline) {
                    return
                }
                deferredDispatch.poll()
                when (packet) {
                    is ClientboundBlockUpdatePacket -> listener.handleBlockUpdate(packet)
                    is ClientboundSectionBlocksUpdatePacket -> listener.handleChunkBlocksUpdate(packet)
                    is ClientboundLevelChunkWithLightPacket -> listener.handleLevelChunkWithLight(packet)
                    else -> throw IllegalStateException("Didn't know how to dispatch packet: ${packet::class}")
                }
            }
        } finally {
            dispatching = false
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
