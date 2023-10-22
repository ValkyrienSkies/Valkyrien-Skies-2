package org.valkyrienskies.mod.common.world

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import org.apache.commons.lang3.mutable.MutableObject
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.apigame.world.chunks.ChunkUnwatchTask
import org.valkyrienskies.core.apigame.world.chunks.ChunkWatchTask
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor
import org.valkyrienskies.mod.util.logger

object ChunkManagement {
    @JvmStatic
    fun tickChunkLoading(shipWorld: ServerShipWorldCore, server: MinecraftServer) {
        val (chunkWatchTasks, chunkUnwatchTasks) = shipWorld.getChunkWatchTasks()

        // for now, just do all the watch tasks
        chunkWatchTasks.forEach { chunkWatchTask: ChunkWatchTask ->
            logger.debug(
                "Watch task for dimension " + chunkWatchTask.dimensionId + ": " +
                    chunkWatchTask.chunkX + " : " + chunkWatchTask.chunkZ
            )

            val chunkPos = ChunkPos(chunkWatchTask.chunkX, chunkWatchTask.chunkZ)

            val level = server.getLevelFromDimensionId(chunkWatchTask.dimensionId)!!
            level.chunkSource.updateChunkForced(chunkPos, true)

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
        }

        chunkUnwatchTasks.forEach { chunkUnwatchTask: ChunkUnwatchTask ->
            logger.debug(
                "Unwatch task for dimension " + chunkUnwatchTask.dimensionId + ": " +
                    chunkUnwatchTask.chunkX + " : " + chunkUnwatchTask.chunkZ
            )
            val chunkPos = ChunkPos(chunkUnwatchTask.chunkX, chunkUnwatchTask.chunkZ)

            if (chunkUnwatchTask.shouldUnload) {
                val level = server.getLevelFromDimensionId(chunkUnwatchTask.dimensionId)!!
                level.chunkSource.updateChunkForced(chunkPos, false)
            }

            for (player in chunkUnwatchTask.playersNeedUnwatching) {
                (player.mcPlayer as ServerPlayer).untrackChunk(chunkPos)
            }
        }

        shipWorld.setExecutedChunkWatchTasks(chunkWatchTasks, chunkUnwatchTasks)
    }

    private val logger by logger()
}
