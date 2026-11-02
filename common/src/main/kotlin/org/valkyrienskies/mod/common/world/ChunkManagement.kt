package org.valkyrienskies.mod.common.world

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import java.util.ArrayDeque
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import org.apache.commons.lang3.mutable.MutableObject
import org.joml.primitives.AABBic
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.chunks.VsiChunkUnwatchTask
import org.valkyrienskies.core.internal.world.chunks.VsiChunkWatchTask
import org.valkyrienskies.mod.common.VS2ChunkAllocator
import org.valkyrienskies.mod.common.getLevelFromDimensionId
import org.valkyrienskies.mod.common.mcPlayer
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.util.VSServerLevel
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor
import org.valkyrienskies.mod.util.logger

object ChunkManagement {

    private const val PENDING_SEND_TIMEOUT_TICKS = 600

    private const val NEIGHBOR_UPGRADE_SCAN_INTERVAL_TICKS = 20

    private class PendingChunkSend(
        val level: ServerLevel,
        val chunkPos: ChunkPos,
        val dimensionId: String,
        val players: List<VsiPlayer>,
        val requiresTickingChunk: Boolean,
        val queuedAtTick: Int,
    )

    private val pendingChunkSends = ArrayDeque<PendingChunkSend>()

    private val shipChunkOnlyColumns = HashMap<String, Long2LongOpenHashMap>()

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

            val shipAABB: AABBic? = chunkWatchTask.ship.shipAABB
            val canContainShipBlocks =
                shipAABB == null || columnOverlapsShipAABB(chunkPos.x, chunkPos.z, shipAABB)

            if (canContainShipBlocks) {
                level.chunkSource.updateChunkForced(chunkPos, true)
                level.chunkSource.removeRegionTicket(VSTicketType.SHIP_CHUNK, chunkPos, 0, chunkPos)
                shipChunkOnlyColumns[chunkWatchTask.dimensionId]?.remove(chunkPos.toLong())
            } else {
                level.chunkSource.addRegionTicket(VSTicketType.SHIP_CHUNK, chunkPos, 0, chunkPos)
                shipChunkOnlyColumns
                    .getOrPut(chunkWatchTask.dimensionId) { Long2LongOpenHashMap() }
                    .put(chunkPos.toLong(), chunkWatchTask.ship.id)
            }
            (level as? VSServerLevel)?.addPendingForcedChunk(chunkPos.x, chunkPos.z)

            pendingChunkSends.add(
                PendingChunkSend(
                    level, chunkPos, chunkWatchTask.dimensionId,
                    chunkWatchTask.playersNeedWatching.toList(),
                    requiresTickingChunk = canContainShipBlocks,
                    queuedAtTick = server.tickCount
                )
            )
            executedWatchTasks.add(chunkWatchTask)
        }

        var cappedUnwatchTasksExecuted = 0
        for (chunkUnwatchTask in chunkUnwatchTasks) {
            val currentShip = shipWorld.allShips.getById(chunkUnwatchTask.ship.id)
            val isShipDeleted = currentShip == null
            val isOneShotTask = isShipDeleted || currentShip!!.chunkClaimDimension != chunkUnwatchTask.dimensionId
            if (!isOneShotTask) {
                if (cappedUnwatchTasksExecuted >= maxUnwatchTasks) continue
                cappedUnwatchTasksExecuted++
            }

            logger.debug(
                "Unwatch task for dimension " + chunkUnwatchTask.dimensionId + ": " +
                    chunkUnwatchTask.chunkX + " : " + chunkUnwatchTask.chunkZ
            )
            val chunkPos = ChunkPos(chunkUnwatchTask.chunkX, chunkUnwatchTask.chunkZ)

            if (chunkUnwatchTask.shouldUnload) {
                val level = server.getLevelFromDimensionId(chunkUnwatchTask.dimensionId)!!
                val isLiveShipChunk =
                    VS2ChunkAllocator.isChunkInShipyardCompanion(chunkPos.x, chunkPos.z) &&
                        !isOneShotTask
                if (!isLiveShipChunk) {
                    level.chunkSource.updateChunkForced(chunkPos, false)
                    level.chunkSource.removeRegionTicket(VSTicketType.SHIP_CHUNK, chunkPos, 0, chunkPos)
                    shipChunkOnlyColumns[chunkUnwatchTask.dimensionId]?.remove(chunkPos.toLong())
                }
            }

            for (player in chunkUnwatchTask.playersNeedUnwatching) {
                (player.mcPlayer as ServerPlayer).untrackChunk(chunkPos)
            }
            executedUnwatchTasks.add(chunkUnwatchTask)
        }

        shipWorld.setExecutedChunkWatchTasks(executedWatchTasks, executedUnwatchTasks)

        upgradeGrownShipColumns(shipWorld, server)
        drainPendingChunkSends(server)
    }

    private fun drainPendingChunkSends(server: MinecraftServer) {
        if (pendingChunkSends.isEmpty()) return
        val maxSends = VSGameConfig.SERVER.Performance.shipChunkSendsPerTick.coerceIn(1, 4096)
        var sends = 0
        var toScan = pendingChunkSends.size
        while (toScan-- > 0 && sends < maxSends) {
            val pending = pendingChunkSends.poll() ?: break

            if (server.getLevelFromDimensionId(pending.dimensionId) !== pending.level) {
                continue
            }

            val chunkMap = pending.level.chunkSource.chunkMap as ChunkMapAccessor
            val timedOut = server.tickCount - pending.queuedAtTick > PENDING_SEND_TIMEOUT_TICKS

            if (pending.requiresTickingChunk) {
                val ready =
                    chunkMap.callGetVisibleChunkIfPresent(pending.chunkPos.toLong())?.tickingChunk != null ||
                        pending.level.chunkSource.getChunkNow(pending.chunkPos.x, pending.chunkPos.z) != null
                if (!ready && !timedOut) {
                    pendingChunkSends.add(pending)
                    continue
                }
                if (!ready) {
                    logger.warn(
                        "Ship chunk send for ${pending.chunkPos} in ${pending.dimensionId} timed out after " +
                            "$PENDING_SEND_TIMEOUT_TICKS ticks — forcing execution"
                    )
                }
                sends++
                val packetCache = MutableObject<ClientboundLevelChunkWithLightPacket>()
                forEachResolvedPlayer(pending) { serverPlayer ->
                    chunkMap.callUpdateChunkTracking(serverPlayer, pending.chunkPos, packetCache, false, true)
                }
            } else {
                val chunk = pending.level.chunkSource.getChunkNow(pending.chunkPos.x, pending.chunkPos.z)
                if (chunk == null) {
                    if (!timedOut) {
                        pendingChunkSends.add(pending)
                    } else {
                        logger.warn(
                            "Dropping ship chunk send for ${pending.chunkPos} in ${pending.dimensionId}: " +
                                "chunk never reached FULL status after $PENDING_SEND_TIMEOUT_TICKS ticks"
                        )
                    }
                    continue
                }
                sends++
                val packetCache = MutableObject<ClientboundLevelChunkWithLightPacket>()
                forEachResolvedPlayer(pending) { serverPlayer ->
                    chunkMap.callPlayerLoadedChunk(serverPlayer, packetCache, chunk)
                }
            }
        }
    }

    private inline fun forEachResolvedPlayer(pending: PendingChunkSend, send: (ServerPlayer) -> Unit) {
        for (player in pending.players) {
            val minecraftPlayer = player as MinecraftPlayer
            val serverPlayer = minecraftPlayer.playerEntityReference.get() as ServerPlayer? ?: continue
            if (serverPlayer.serverLevel() !== pending.level) {
                logger.warn("Skipping ship chunk send for player in a different dimension than the chunk!")
                continue
            }
            send(serverPlayer)
        }
    }

    private fun upgradeGrownShipColumns(shipWorld: VsiServerShipWorld, server: MinecraftServer) {
        if (shipChunkOnlyColumns.isEmpty()) return
        if (server.tickCount % NEIGHBOR_UPGRADE_SCAN_INTERVAL_TICKS != 0) return

        val dimensionIterator = shipChunkOnlyColumns.entries.iterator()
        while (dimensionIterator.hasNext()) {
            val (dimensionId, columns) = dimensionIterator.next()
            val level = server.getLevelFromDimensionId(dimensionId)
            val columnIterator = columns.long2LongEntrySet().fastIterator()
            while (columnIterator.hasNext()) {
                val entry = columnIterator.next()
                val ship = shipWorld.allShips.getById(entry.longValue)
                if (ship == null) {
                    columnIterator.remove()
                    continue
                }
                val shipAABB = ship.shipAABB ?: continue
                val chunkX = ChunkPos.getX(entry.longKey)
                val chunkZ = ChunkPos.getZ(entry.longKey)
                if (level == null || !columnOverlapsShipAABB(chunkX, chunkZ, shipAABB)) continue

                val chunkPos = ChunkPos(chunkX, chunkZ)
                level.chunkSource.updateChunkForced(chunkPos, true)
                level.chunkSource.removeRegionTicket(VSTicketType.SHIP_CHUNK, chunkPos, 0, chunkPos)
                columnIterator.remove()
            }
            if (columns.isEmpty()) {
                dimensionIterator.remove()
            }
        }
    }

    private fun columnOverlapsShipAABB(chunkX: Int, chunkZ: Int, shipAABB: AABBic): Boolean {
        val minBlockX = chunkX shl 4
        val minBlockZ = chunkZ shl 4
        return minBlockX < shipAABB.maxX() && minBlockX + 15 >= shipAABB.minX() &&
            minBlockZ < shipAABB.maxZ() && minBlockZ + 15 >= shipAABB.minZ()
    }

    /**
     * Returns the list of pending chunk-tracking sends (exposed for tests).
     */
    @JvmStatic
    fun getPendingTrackingUpdates(): List<Any> = pendingChunkSends.toList()

    /**
     * Clears any pending chunk management state (pending sends and neighbor-column tracking).
     */
    @JvmStatic
    fun clearPendingState() {
        pendingChunkSends.clear()
        shipChunkOnlyColumns.clear()
    }

    private val logger by logger()
}
