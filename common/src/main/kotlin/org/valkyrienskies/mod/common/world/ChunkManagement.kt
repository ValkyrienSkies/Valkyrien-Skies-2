package org.valkyrienskies.mod.common.world

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import org.apache.commons.lang3.mutable.MutableObject
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.chunks.VsiChunkUnwatchTask
import org.valkyrienskies.core.internal.world.chunks.VsiChunkWatchTask
import org.valkyrienskies.mod.common.VS2ChunkAllocator
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.isChunkLoadedForVS
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor
import org.valkyrienskies.mod.util.logger

object ChunkManagement {
    @JvmStatic
    fun tickChunkLoading(shipWorld: VsiServerShipWorld, server: MinecraftServer) {
        val (chunkWatchTasks, chunkUnwatchTasks) = shipWorld.getChunkWatchTasks()

        // for now, just do all the watch tasks

        chunkWatchTasks.forEach { chunkWatchTask: VsiChunkWatchTask ->
            logger.debug(
                "Watch task for dimension " + chunkWatchTask.dimensionId + ": " +
                    chunkWatchTask.chunkX + " : " + chunkWatchTask.chunkZ
            )

            val chunkPos = ChunkPos(chunkWatchTask.chunkX, chunkWatchTask.chunkZ)

            val level = server.getLevelFromDimensionId(chunkWatchTask.dimensionId)!!
            if (VS2ChunkAllocator.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
                // Shipyard chunks use radius-0 tickets (level 33 = FULL status) to avoid
                // loading ~25 neighbor chunks per ship chunk. The chunk pipeline's neighbor
                // requirements are bypassed by MixinChunkMapShipyard.
                level.chunkSource.addRegionTicket(VSTicketType.SHIP_CHUNK, chunkPos, 0, chunkPos)
            } else {
                level.chunkSource.updateChunkForced(chunkPos, true)
            }

            val isShipyard = VS2ChunkAllocator.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)
            // Shipyard chunks use FULL status (level 33), so isTickingChunk never returns true.
            // Use isChunkLoadedForVS which accepts FULL status for shipyard chunks.
            val condition = if (isShipyard) {
                { level.isChunkLoadedForVS(chunkPos) }
            } else {
                { level.isTickingChunk(chunkPos) }
            }
            level.server.executeIf(condition) {
                for (player in chunkWatchTask.playersNeedWatching) {
                    if (player !is MinecraftPlayer) continue
                    val serverPlayer = player.playerEntityReference.get() as ServerPlayer?
                    if (serverPlayer != null) {
                        if (chunkWatchTask.dimensionId != player.dimension) {
                            logger.warn("Player received watch task for chunk in dimension that they are not also in!")
                        }
                        val map = level.chunkSource.chunkMap as ChunkMapAccessor
                        if (isShipyard) {
                            // callUpdateChunkTracking uses getTickingChunk() internally, which
                            // returns null for FULL-only chunks (level 33). Send directly instead.
                            val chunk = level.chunkSource.getChunkNow(chunkPos.x, chunkPos.z)
                            if (chunk != null) {
                                val packets = MutableObject<ClientboundLevelChunkWithLightPacket>()
                                map.callPlayerLoadedChunk(serverPlayer, packets, chunk)
                            }
                        } else {
                            map.callUpdateChunkTracking(serverPlayer, chunkPos, MutableObject(), false, true)
                        }
                    }
                }
            }
        }

        chunkUnwatchTasks.forEach { chunkUnwatchTask: VsiChunkUnwatchTask ->
            logger.debug(
                "Unwatch task for dimension " + chunkUnwatchTask.dimensionId + ": " +
                    chunkUnwatchTask.chunkX + " : " + chunkUnwatchTask.chunkZ
            )
            val chunkPos = ChunkPos(chunkUnwatchTask.chunkX, chunkUnwatchTask.chunkZ)

            if (chunkUnwatchTask.shouldUnload) {
                val level = server.getLevelFromDimensionId(chunkUnwatchTask.dimensionId)!!
                if (VS2ChunkAllocator.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
                    level.chunkSource.removeRegionTicket(VSTicketType.SHIP_CHUNK, chunkPos, 0, chunkPos)
                } else {
                    level.chunkSource.updateChunkForced(chunkPos, false)
                }
            }

            for (player in chunkUnwatchTask.playersNeedUnwatching) {
                if (player !is MinecraftPlayer) continue
                (player.mcPlayer as ServerPlayer).untrackChunk(chunkPos)
            }
        }

        shipWorld.setExecutedChunkWatchTasks(chunkWatchTasks, chunkUnwatchTasks)
    }

    /**
     * Returns the list of pending tracking updates (currently empty — stub for tests).
     */
    @JvmStatic
    fun getPendingTrackingUpdates(): List<Any> = emptyList()

    /**
     * Clears any pending chunk management state (stub for tests).
     */
    @JvmStatic
    fun clearPendingState() {
        // No-op in current implementation
    }

    private val logger by logger()
}
