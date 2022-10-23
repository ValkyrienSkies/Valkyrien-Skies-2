package org.valkyrienskies.mod.common.assembly

import com.google.common.collect.HashBiMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.ChunkPos
import org.valkyrienskies.core.hooks.VSEvents.ShipLoadEventClient
import org.valkyrienskies.core.networking.RegisteredHandler
import org.valkyrienskies.core.networking.simple.registerClientHandler
import org.valkyrienskies.core.util.logger
import org.valkyrienskies.core.util.pollUntilEmpty
import org.valkyrienskies.mod.common.networking.PacketRestartChunkUpdates
import org.valkyrienskies.mod.common.networking.PacketStopChunkUpdates
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.mixinducks.feature.seamless_copy.ChunkRenderDispatcherDuck
import org.valkyrienskies.mod.mixinducks.feature.seamless_copy.SeamlessCopyClientPacketListenerDuck
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class SeamlessChunksManager(private val listener: ClientPacketListener) {

    companion object {
        private val logger by logger()

        @JvmStatic
        fun get() = (Minecraft.getInstance().connection as? SeamlessCopyClientPacketListenerDuck)?.chunks
    }

    private val linkedRenders = HashBiMap.create<ChunkPos, ChunkPos>()

    private val queuedUpdates = ConcurrentHashMap<ChunkPos, ConcurrentLinkedQueue<Packet<*>>>()
    private val stalledChunks = LongOpenHashSet()

    private val handlers: Iterable<RegisteredHandler>

    init {
        handlers = mutableListOf(
            PacketStopChunkUpdates::class.registerClientHandler { (chunks) ->
                chunks.forEach { stalledChunks.add(it.toLong()) }
            },

            PacketRestartChunkUpdates::class.registerClientHandler { packet ->
                if (Minecraft.getInstance().shipObjectWorld.queryableShipData.contains(packet.waitForShip)) {
                    Minecraft.getInstance().execute {
                        onRestartUpdates(packet)
                    }
                } else {
                    ShipLoadEventClient.once({ it.ship.id == packet.waitForShip }) {
                        Minecraft.getInstance().execute {
                            onRestartUpdates(packet)
                        }
                    }
                }

            }
        )
    }

    private fun onRestartUpdates(packet: PacketRestartChunkUpdates) {
        val (chunks, linkedChunks) = packet
        // linkedChunks.forEach { (c1, c2) ->
        //     linkedRenders[c1] = c2
        //     println("Linking chunks $c1 and $c2")
        // }

        chunks.forEach { pos ->
            stalledChunks.remove(pos.toLong())

            queuedUpdates[pos]?.pollUntilEmpty { packet ->
                logger.info("Executing deferred update at <${pos.x}, ${pos.z}> for ${packet::class}")
                when (packet) {
                    is ClientboundLevelChunkPacket -> listener.handleLevelChunk(packet)
                    is ClientboundBlockUpdatePacket -> listener.handleBlockUpdate(packet)
                    is ClientboundSectionBlocksUpdatePacket -> listener.handleChunkBlocksUpdate(packet)
                    is ClientboundLightUpdatePacket -> listener.handleLightUpdatePacked(packet)
                }
            }

        }
    }

    private val RenderChunk.pos get() = ChunkPos(origin.x shr 4, origin.z shr 4)

    private fun isLinked(c1: RenderChunk, c2: RenderChunk): Boolean =
        linkedRenders[c1.pos] == c2.pos || linkedRenders[c2.pos] == c1.pos

    private fun unlink(c: RenderChunk) {
        if (linkedRenders.remove(c.pos) == null) {
            linkedRenders.inverse().remove(c.pos)
        }
    }

    fun scheduleLinkedChunksCompile(
        finishTimeNano: Long, chunksToCompile: MutableSet<RenderChunk>, dispatcher: ChunkRenderDispatcher
    ) {
        val toRemove = mutableSetOf<RenderChunk>()
        val startTime = Util.getNanos()
        val iterator = chunksToCompile.iterator()
        var chunksCompiled = 0
        while (iterator.hasNext()) {
            val renderChunk = iterator.next()
            val linkedChunk = chunksToCompile.find { c2 -> isLinked(renderChunk, c2) }
            if (linkedChunk != null) {
                println("Rendering linked chunks together ${linkedChunk.pos} and ${renderChunk.pos}")
                // modified code
                val tasks = listOf(renderChunk.createCompileTask(), linkedChunk.createCompileTask())
                (dispatcher as ChunkRenderDispatcherDuck).vs_scheduleLinked(tasks)

                unlink(renderChunk)
                toRemove.add(renderChunk)
                toRemove.add(linkedChunk)

                chunksCompiled += 2
            } else {
                // vanilla code
                if (renderChunk.isDirtyFromPlayer) {
                    dispatcher.rebuildChunkSync(renderChunk)
                } else {
                    renderChunk.rebuildChunkAsync(dispatcher)
                }
                renderChunk.setNotDirty()
                iterator.remove()
                chunksCompiled++
            }

            val currentTime = Util.getNanos()
            val averageTimePerCompile: Long = (currentTime - startTime) / chunksCompiled.toLong()
            val timeRemaining = finishTimeNano - currentTime
            if (timeRemaining < averageTimePerCompile) {
                break
            }
        }
        chunksToCompile.removeAll(toRemove)
    }

    fun cleanup() {
        stalledChunks.clear()
        queuedUpdates.clear()
    }

    fun queue(chunkX: Int, chunkZ: Int, packet: Packet<*>): Boolean {
        if (stalledChunks.contains(ChunkPos.asLong(chunkX, chunkZ))) {
            logger.info("Deferring update at <$chunkX, $chunkZ> for ${packet::class}")
            queuedUpdates.computeIfAbsent(ChunkPos(chunkX, chunkZ)) { ConcurrentLinkedQueue() }.add(packet)
            return true
        }

        logger.info("Received update at <$chunkX, $chunkZ> for ${packet::class}")

        return false
    }
}
