package org.valkyrienskies.mod.common.world

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import org.apache.commons.lang3.mutable.MutableObject
import org.valkyrienskies.core.api.world.ServerShipWorldCore
import org.valkyrienskies.core.api.world.chunks.ChunkUnwatchTask
import org.valkyrienskies.core.api.world.chunks.ChunkWatchTask
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.server.world.ChunkMapAccessor
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
            val chunkPacketBuffer: MutableObject<ClientboundLevelChunkWithLightPacket> =
                MutableObject<ClientboundLevelChunkWithLightPacket>()
            val chunkPos = ChunkPos(chunkWatchTask.chunkX, chunkWatchTask.chunkZ)

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
