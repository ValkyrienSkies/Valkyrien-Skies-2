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
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.util.VSServerLevel
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor
import org.valkyrienskies.mod.util.logger

object ChunkManagement {
    @JvmStatic
    fun tickChunkLoading(shipWorld: VsiServerShipWorld, server: MinecraftServer) {
        val (chunkWatchTasks, chunkUnwatchTasks) = shipWorld.getChunkWatchTasks()
        val maxWatchTasks = VSGameConfig.SERVER.Performance.shipChunkWatchTasksPerTick.coerceIn(1, 4096)
        val maxUnwatchTasks = VSGameConfig.SERVER.Performance.shipChunkUnwatchTasksPerTick.coerceIn(1, 4096)
        val executedWatchTasks = ArrayList<VsiChunkWatchTask>(minOf(chunkWatchTasks.size, maxWatchTasks))
        val executedUnwatchTasks =
            ArrayList<VsiChunkUnwatchTask>(minOf(chunkUnwatchTasks.size, maxUnwatchTasks))

        for (chunkWatchTask in chunkWatchTasks.asSequence().take(maxWatchTasks)) {
            logger.debug(
                "Watch task for dimension " + chunkWatchTask.dimensionId + ": " +
                    chunkWatchTask.chunkX + " : " + chunkWatchTask.chunkZ
            )

            val chunkPos = ChunkPos(chunkWatchTask.chunkX, chunkWatchTask.chunkZ)

            val level = server.getLevelFromDimensionId(chunkWatchTask.dimensionId)!!
            // Active ship chunks must stay on vanilla forced tickets so gameplay keeps
            // random ticks, block ticks, and entity ticks.
            level.chunkSource.updateChunkForced(chunkPos, true)
            (level as? VSServerLevel)?.addPendingForcedChunk(chunkPos.x, chunkPos.z)

            level.server.executeIf({ level.isTickingChunk(chunkPos) }) {
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
            executedWatchTasks.add(chunkWatchTask)
        }

        for (chunkUnwatchTask in chunkUnwatchTasks.asSequence().take(maxUnwatchTasks)) {
            logger.debug(
                "Unwatch task for dimension " + chunkUnwatchTask.dimensionId + ": " +
                    chunkUnwatchTask.chunkX + " : " + chunkUnwatchTask.chunkZ
            )
            val chunkPos = ChunkPos(chunkUnwatchTask.chunkX, chunkUnwatchTask.chunkZ)

            if (chunkUnwatchTask.shouldUnload) {
                val level = server.getLevelFromDimensionId(chunkUnwatchTask.dimensionId)!!
                val isLiveShipChunk =
                    VS2ChunkAllocator.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z) &&
                        shipWorld.allShips.getById(chunkUnwatchTask.ship.id) != null
                if (!isLiveShipChunk) {
                    level.chunkSource.updateChunkForced(chunkPos, false)
                }
            }

            for (player in chunkUnwatchTask.playersNeedUnwatching) {
                (player.mcPlayer as ServerPlayer).untrackChunk(chunkPos)
            }
            executedUnwatchTasks.add(chunkUnwatchTask)
        }

        shipWorld.setExecutedChunkWatchTasks(executedWatchTasks, executedUnwatchTasks)
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
