package org.valkyrienskies.mod.common.assembly

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.ChunkPos
import org.valkyrienskies.core.api.ClientShip
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.ChunkClaim
import org.valkyrienskies.core.hooks.VSEvents.ShipLoadEventClient
import org.valkyrienskies.core.networking.simple.registerClientHandler
import org.valkyrienskies.core.util.logger
import org.valkyrienskies.core.util.pollUntilEmpty
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.networking.PacketRestartChunkUpdates
import org.valkyrienskies.mod.common.networking.PacketStopChunkUpdates
import org.valkyrienskies.mod.mixinducks.feature.seamless_copy.SeamlessCopyClientPacketListenerDuck
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
        fun get() = (Minecraft.getInstance().connection as? SeamlessCopyClientPacketListenerDuck)?.chunks
    }

    private val shipQueuedUpdates = ConcurrentHashMap<ChunkClaim, ConcurrentLinkedQueue<Packet<*>>>()
    private val queuedUpdates = ConcurrentHashMap<ChunkPos, ConcurrentLinkedQueue<Packet<*>>>()
    private val stalledChunks = LongOpenHashSet()

    init {
        PacketStopChunkUpdates::class.registerClientHandler { (chunks) ->
            chunks.forEach { stalledChunks.add(it.toLong()) }
        }
        PacketRestartChunkUpdates::class.registerClientHandler { packet ->
            Minecraft.getInstance().execute {
                onRestartUpdates(packet)
            }
        }

        ShipLoadEventClient.on { (ship) ->
            onShipLoad(ship)
        }
    }

    private fun onShipLoad(ship: ClientShip) {
        val packets = shipQueuedUpdates.remove(ship.chunkClaim)
        if (!packets.isNullOrEmpty()) {
            logger.info("Executing ${packets.size} deferred updates for ship ID=${ship.id} at ${ship.chunkClaim}")
            dispatchQueuedPackets(packets)
        }
    }

    private fun onRestartUpdates(packet: PacketRestartChunkUpdates) {
        val (chunks) = packet

        chunks.forEach { pos ->
            stalledChunks.remove(pos.toLong())
            val packets = queuedUpdates.remove(pos)
            if (!packets.isNullOrEmpty()) {
                logger.info("Executing ${packets.size} deferred updates at <${pos.x}, ${pos.z}>")
                dispatchQueuedPackets(packets)
            }
        }
    }

    private fun dispatchQueuedPackets(queue: Queue<Packet<*>>) {
        queue.pollUntilEmpty { packet ->
            when (packet) {
                is ClientboundLevelChunkPacket -> listener.handleLevelChunk(packet)
                is ClientboundBlockUpdatePacket -> listener.handleBlockUpdate(packet)
                is ClientboundSectionBlocksUpdatePacket -> listener.handleChunkBlocksUpdate(packet)
                is ClientboundLightUpdatePacket -> listener.handleLightUpdatePacked(packet)
            }
        }
    }

    fun cleanup() {
        stalledChunks.clear()
        queuedUpdates.clear()
        shipQueuedUpdates.clear()
    }

    /**
     * Attempt to defer a chunk update
     *
     * @return true if the chunk update was deferred, false if otherwise
     */
    fun queue(chunkX: Int, chunkZ: Int, packet: Packet<*>): Boolean {
        // note, this will get re-called when we're processing the shipQueuedUpdates queue,
        // so if any updates in there are actually still stalled by a [PacketStopChunkUpdates] it will
        // be added to the queuedUpdates queue here (and vice versa)

        // The chunk is in the shipyard, but we don't know what ship
        if (ChunkAllocator.isChunkInShipyard(chunkX, chunkZ) &&
            Minecraft.getInstance().level?.getShipManagingPos(chunkX, chunkZ) == null
        ) {
            logger.info("Deferring ship update at <$chunkX, $chunkZ> for ${packet::class}")
            shipQueuedUpdates
                .computeIfAbsent(ChunkClaim.getClaim(chunkX, chunkZ)) { ConcurrentLinkedQueue() }
                .add(packet)

            return true
        }

        // The chunk prevented from updating by a [PacketStopChunkUpdates]
        if (stalledChunks.contains(ChunkPos.asLong(chunkX, chunkZ))) {
            logger.info("Deferring update at <$chunkX, $chunkZ> for ${packet::class}")
            queuedUpdates
                .computeIfAbsent(ChunkPos(chunkX, chunkZ)) { ConcurrentLinkedQueue() }
                .add(packet)

            return true
        }

        logger.info("Received update at <$chunkX, $chunkZ> for ${packet::class}")

        return false
    }
}
