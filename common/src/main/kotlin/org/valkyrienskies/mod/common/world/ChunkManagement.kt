package org.valkyrienskies.mod.common.world

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import org.apache.commons.lang3.mutable.MutableObject
import org.valkyrienskies.core.chunk_tracking.ChunkUnwatchTask
import org.valkyrienskies.core.chunk_tracking.ChunkWatchTask
import org.valkyrienskies.core.game.ships.ShipObjectServerWorld
import org.valkyrienskies.core.util.logger
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.server.world.ChunkMapAccessor

object ChunkManagement {
    @JvmStatic
    fun tickChunkLoading(shipWorld: ShipObjectServerWorld, server: MinecraftServer) {
        val (chunkWatchTasks, chunkUnwatchTasks) = shipWorld.getChunkWatchTasks()

        // for now, just do all the watch tasks

        chunkWatchTasks.forEach { chunkWatchTask: ChunkWatchTask ->
            logger.debug(
                "Watch task for dimension " + chunkWatchTask.dimensionId + ": " +
                    chunkWatchTask.getChunkX() + " : " + chunkWatchTask.getChunkZ()
            )
            val chunkPacketBuffer: MutableObject<ClientboundLevelChunkWithLightPacket> =
                MutableObject<ClientboundLevelChunkWithLightPacket>()
            val chunkPos = ChunkPos(chunkWatchTask.getChunkX(), chunkWatchTask.getChunkZ())

            val level = server.getLevelFromDimensionId(chunkWatchTask.dimensionId)!!
            level.chunkSource.updateChunkForced(chunkPos, true)

            level.server.executeIf({ level.isTickingChunk(chunkPos) }) {
                for (player in chunkWatchTask.playersNeedWatching) {
                    val minecraftPlayer = player as MinecraftPlayer
                    if (chunkWatchTask.dimensionId != player.dimension) {
                        logger.warn("Player received watch task for chunk in dimension that they are not also in!")
                    }
                    val serverPlayerEntity =
                        minecraftPlayer.playerEntityReference.get() as ServerPlayer?
                    if (serverPlayerEntity != null) {
                        (level.chunkSource.chunkMap as ChunkMapAccessor)
                            .callUpdateChunkTracking(serverPlayerEntity, chunkPos, chunkPacketBuffer, false, true)
                    }
                }
            }
        }

        chunkUnwatchTasks.forEach { chunkUnwatchTask: ChunkUnwatchTask ->
            logger.debug(
                "Unwatch task for dimension " + chunkUnwatchTask.dimensionId + ": " +
                    chunkUnwatchTask.getChunkX() + " : " + chunkUnwatchTask.getChunkZ()
            )
            val chunkPos = ChunkPos(chunkUnwatchTask.getChunkX(), chunkUnwatchTask.getChunkZ())

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
