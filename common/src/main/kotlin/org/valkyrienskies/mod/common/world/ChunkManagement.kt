package org.valkyrienskies.mod.common.world

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.apigame.world.chunks.ChunkUnwatchTask
import org.valkyrienskies.core.apigame.world.chunks.ChunkWatchTask
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor
import org.valkyrienskies.mod.util.DelegateLogger.provideDelegate
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
            level.chunkSource.addRegionTicket(VS_FORCED, chunkPos, TICKET_LEVEL_BELOW_FORCED, chunkPos)

            level.server.executeIf({ level.isTickingChunk(chunkPos) }) {
                val map = level.chunkSource.chunkMap as ChunkMapAccessor
                for (player in chunkWatchTask.playersNeedWatching) {
                    val minecraftPlayer = player as MinecraftPlayer
                    val serverPlayer = minecraftPlayer.playerEntityReference.get() as ServerPlayer?
                    if (serverPlayer != null) {
                        if (chunkWatchTask.dimensionId != player.dimension) {
                            logger.warn("Player received watch task for chunk in dimension that they are not also in!")
                        }
                        map.callMarkChunkPendingToSend(serverPlayer, chunkPos)
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
                level.chunkSource.removeRegionTicket(VS_FORCED, chunkPos, TICKET_LEVEL_BELOW_FORCED, chunkPos)
            }

            val level = server.getLevelFromDimensionId(chunkUnwatchTask.dimensionId)!!
            val map = level.chunkSource.chunkMap as ChunkMapAccessor
            for (player in chunkUnwatchTask.playersNeedUnwatching) {
                map.callDropChunk(player.mcPlayer as ServerPlayer, chunkPos)
            }
        }

        shipWorld.setExecutedChunkWatchTasks(chunkWatchTasks, chunkUnwatchTasks)
    }

    private val logger by logger()

    private val VS_FORCED: TicketType<ChunkPos> = TicketType.create("vs_forced", Comparator.comparingLong(ChunkPos::toLong))
    // Chunk ticket level gets set to FullChunkStatus.FULL - 3 = 30, it needs to be below 31 for entities to tick
    private const val TICKET_LEVEL_BELOW_FORCED = 3
}
