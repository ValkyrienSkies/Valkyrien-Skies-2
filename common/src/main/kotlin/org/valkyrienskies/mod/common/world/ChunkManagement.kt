package org.valkyrienskies.mod.common.world

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
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
import org.valkyrienskies.mod.common.util.VSServerLevel
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

            // Use lightweight ticket for shipyard chunks to avoid loading excessive neighbor chunks.
            // Vanilla's updateChunkForced uses level 31 (entity ticking) which forces a 2-chunk
            // neighborhood (~25 chunks). Our ticket with radius 1 gives level 32 (ticking), which
            // only needs a 1-chunk neighborhood (~9 chunks) — still a big reduction.
            if (VS2ChunkAllocator.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z)) {
                level.chunkSource.addRegionTicket(
                    VSTicketType.SHIP_CHUNK, chunkPos, 1, chunkPos
                )
                (level as VSServerLevel).addPendingForcedChunk(chunkPos.x, chunkPos.z)
            } else {
                level.chunkSource.updateChunkForced(chunkPos, true)
                (level as VSServerLevel).addPendingForcedChunk(chunkPos.x, chunkPos.z)
            }

            level.server.executeIf({ level.isChunkLoadedForVS(chunkPos) }) {
                for (player in chunkWatchTask.playersNeedWatching) {
                    val minecraftPlayer = player as MinecraftPlayer
                    val serverPlayer = minecraftPlayer.playerEntityReference.get() as ServerPlayer?
                    if (serverPlayer != null) {
                        if (chunkWatchTask.dimensionId != player.dimension) {
                            logger.warn("Player received watch task for chunk in dimension that they are not also in!")
                        }
                        val map = level.chunkSource.chunkMap as ChunkMapAccessor
                        map.callUpdateChunkTracking(serverPlayer, chunkPos, MutableObject(), false, true)
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
                    level.chunkSource.removeRegionTicket(
                        VSTicketType.SHIP_CHUNK, chunkPos, 1, chunkPos
                    )
                } else {
                    level.chunkSource.updateChunkForced(chunkPos, false)
                }
            }

            for (player in chunkUnwatchTask.playersNeedUnwatching) {
                (player.mcPlayer as ServerPlayer).untrackChunk(chunkPos)
            }
        }

        shipWorld.setExecutedChunkWatchTasks(chunkWatchTasks, chunkUnwatchTasks)
    }

    private val logger by logger()
}
