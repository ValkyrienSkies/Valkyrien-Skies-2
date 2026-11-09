package org.valkyrienskies.mod.common.fluid

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.util.WindData
import org.valkyrienskies.core.api.util.WindProvider
import org.valkyrienskies.core.api.util.WindType
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Bridges vanilla fluid flow into vscore's fluid wind sampler.
 *
 * This object never calls Minecraft getters from WindProvider callbacks. The game thread builds and publishes a
 * snapshot once per server tick; physics-thread drag reads only that completed snapshot.
 */
object VanillaFluidFlowWindProvider : WindProvider {
    private const val FLOW_SPEED_SCALE = 1.0
    private const val MAX_CHUNK_REFRESHES_PER_TICK = 8

    @Volatile
    private var registered = false

    @Volatile
    private var snapshotsByDimension: Map<DimensionId, DimensionFlowSnapshot> = emptyMap()

    private val refreshQueue = ArrayDeque<ChunkScanRequest>()
    private val queuedRefreshes = HashSet<ChunkRefreshKey>()
    private val dirtyChunks = HashSet<ChunkRefreshKey>()

    fun markDirty(level: Level, pos: BlockPos, previousState: BlockState?, newState: BlockState?) {
        if (level.isClientSide || level !is ServerLevel) return
        if (previousState != null && newState != null && previousState == newState) return

        val dimensionId = level.dimensionId
        val chunkX = SectionPos.blockToSectionCoord(pos.x)
        val chunkZ = SectionPos.blockToSectionCoord(pos.z)

        synchronized(this) {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    dirtyChunks.add(ChunkRefreshKey(dimensionId, ChunkPos.asLong(chunkX + dx, chunkZ + dz)))
                }
            }
        }
    }

    @OptIn(GameTickOnly::class)
    fun tick(level: ServerLevel) {
        val shipWorld = level.shipObjectWorld
        ensureRegistered(shipWorld)

        val dimensionId = level.dimensionId
        val activeChunkRanges = HashMap<Long, IntRange>()

        for (ship in shipWorld.loadedShips) {
            if (ship.chunkClaimDimension != dimensionId) continue
            val worldAabb = AABBd(ship.worldAABB).expand(1.0)

            val minX = floor(worldAabb.minX()).toInt()
            val minY = floor(worldAabb.minY()).toInt().coerceAtLeast(level.minBuildHeight)
            val minZ = floor(worldAabb.minZ()).toInt()
            val maxX = ceil(worldAabb.maxX()).toInt()
            val maxY = ceil(worldAabb.maxY()).toInt().coerceAtMost(level.maxBuildHeight - 1)
            val maxZ = ceil(worldAabb.maxZ()).toInt()
            if (minY > maxY) continue

            val minChunkX = SectionPos.blockToSectionCoord(minX)
            val maxChunkX = SectionPos.blockToSectionCoord(maxX)
            val minChunkZ = SectionPos.blockToSectionCoord(minZ)
            val maxChunkZ = SectionPos.blockToSectionCoord(maxZ)

            for (chunkX in minChunkX..maxChunkX) {
                for (chunkZ in minChunkZ..maxChunkZ) {
                    val chunkKey = ChunkPos.asLong(chunkX, chunkZ)
                    activeChunkRanges[chunkKey] = merge(activeChunkRanges[chunkKey], minY..maxY)
                }
            }
        }

        if (activeChunkRanges.isEmpty()) {
            clearDimension(dimensionId)
            return
        }

        queueChunksNeedingRefresh(dimensionId, activeChunkRanges)
        val refreshedChunks = refreshQueuedChunks(level, dimensionId)
        publishSnapshot(dimensionId, activeChunkRanges.keys, refreshedChunks)
    }

    override fun getWindFor(type: WindType, x: Double, y: Double, z: Double, dim: DimensionId): WindData {
        if (type != WindType.FLUID) return WindData.none(type)
        val snapshot = snapshotsByDimension[dim] ?: return WindData.none(type)
        val flow = sampleSmoothedFlow(snapshot, x, y, z)
        val speed = flow.length() * VSGameConfig.SERVER.fluidWindSpeedScale
        return if (speed > 1.0e-6) WindData(Vector3d(flow), speed, type) else WindData.none(type)
    }

    private fun sampleSmoothedFlow(snapshot: DimensionFlowSnapshot, x: Double, y: Double, z: Double): Vector3d {
        // Interpret stored block flows as cell-centered samples. This avoids a hard force step when the drag sampler
        // crosses a block boundary at the edge of a flowing-fluid cell.
        val sampleX = x - 0.5
        val sampleY = y - 0.5
        val sampleZ = z - 0.5
        val baseX = floor(sampleX).toInt()
        val baseY = floor(sampleY).toInt()
        val baseZ = floor(sampleZ).toInt()
        val fracX = sampleX - baseX
        val fracY = sampleY - baseY
        val fracZ = sampleZ - baseZ
        val result = Vector3d()

        for (dx in 0..1) {
            val wx = if (dx == 0) 1.0 - fracX else fracX
            for (dy in 0..1) {
                val wy = if (dy == 0) 1.0 - fracY else fracY
                for (dz in 0..1) {
                    val wz = if (dz == 0) 1.0 - fracZ else fracZ
                    val weight = wx * wy * wz
                    if (weight <= 0.0) continue

                    val blockX = baseX + dx
                    val blockY = baseY + dy
                    val blockZ = baseZ + dz
                    val flow = sampleBlockFlow(snapshot, blockX, blockY, blockZ) ?: continue
                    result.fma(weight, flow)
                }
            }
        }

        return result
    }

    private fun sampleBlockFlow(snapshot: DimensionFlowSnapshot, blockX: Int, blockY: Int, blockZ: Int): Vector3d? {
        val chunkKey = ChunkPos.asLong(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ))
        val chunk = snapshot.chunkFlows[chunkKey] ?: return null
        return chunk.flows[BlockPos.asLong(blockX, blockY, blockZ)]
    }

    private fun queueChunksNeedingRefresh(dimensionId: DimensionId, activeChunkRanges: Map<Long, IntRange>) {
        val previousSnapshot = snapshotsByDimension[dimensionId]
        for ((chunkKey, yRange) in activeChunkRanges) {
            val refreshKey = ChunkRefreshKey(dimensionId, chunkKey)
            val needsInitialScan = previousSnapshot?.chunkFlows?.containsKey(chunkKey) != true
            val isDirty = synchronized(this) { dirtyChunks.remove(refreshKey) }

            if ((needsInitialScan || isDirty) && queuedRefreshes.add(refreshKey)) {
                refreshQueue.addLast(ChunkScanRequest(dimensionId, chunkKey, yRange.first, yRange.last))
            }
        }
    }

    private fun refreshQueuedChunks(level: ServerLevel, dimensionId: DimensionId): Map<Long, ChunkFlowSnapshot> {
        val refreshed = HashMap<Long, ChunkFlowSnapshot>()
        var refreshedThisTick = 0

        while (refreshQueue.isNotEmpty() && refreshedThisTick < MAX_CHUNK_REFRESHES_PER_TICK) {
            val request = refreshQueue.removeFirst()
            queuedRefreshes.remove(ChunkRefreshKey(request.dimensionId, request.chunkKey))

            if (request.dimensionId != dimensionId) {
                refreshQueue.addLast(request)
                break
            }

            val chunkX = ChunkPos.getX(request.chunkKey)
            val chunkZ = ChunkPos.getZ(request.chunkKey)
            val chunk = level.chunkSource.getChunkNow(chunkX, chunkZ)
            if (chunk == null) {
                refreshed[request.chunkKey] = ChunkFlowSnapshot(Long2ObjectOpenHashMap())
                refreshedThisTick++
                continue
            }

            refreshed[request.chunkKey] = scanChunk(level, chunk, request.minY, request.maxY)
            refreshedThisTick++
        }

        return refreshed
    }

    private fun publishSnapshot(
        dimensionId: DimensionId,
        activeChunkKeys: Set<Long>,
        refreshedChunks: Map<Long, ChunkFlowSnapshot>
    ) {
        val previousDimension = snapshotsByDimension[dimensionId]
        val chunkFlows = HashMap<Long, ChunkFlowSnapshot>()

        if (previousDimension != null) {
            for ((chunkKey, snapshot) in previousDimension.chunkFlows) {
                if (chunkKey in activeChunkKeys) {
                    chunkFlows[chunkKey] = snapshot
                }
            }
        }

        for ((chunkKey, snapshot) in refreshedChunks) {
            if (chunkKey in activeChunkKeys) {
                chunkFlows[chunkKey] = snapshot
            }
        }

        val next = HashMap(snapshotsByDimension)
        if (chunkFlows.isEmpty()) {
            next.remove(dimensionId)
        } else {
            next[dimensionId] = DimensionFlowSnapshot(chunkFlows)
        }
        snapshotsByDimension = next
    }

    private fun scanChunk(level: ServerLevel, chunk: LevelChunk, minY: Int, maxY: Int): ChunkFlowSnapshot {
        val flows = Long2ObjectOpenHashMap<Vector3d>()
        val mutablePos = BlockPos.MutableBlockPos()
        val minBlockX = chunk.pos.minBlockX
        val minBlockZ = chunk.pos.minBlockZ

        for (xOffset in 0..15) {
            val x = minBlockX + xOffset
            for (zOffset in 0..15) {
                val z = minBlockZ + zOffset
                for (y in minY..maxY) {
                    mutablePos.set(x, y, z)
                    val fluidState = chunk.getFluidState(mutablePos)
                    if (fluidState.isEmpty) continue

                    val flow = fluidState.getFlow(level, mutablePos)
                    if (flow.lengthSqr() <= 1.0e-12) continue

                    flows[mutablePos.asLong()] = Vector3d(
                        flow.x * FLOW_SPEED_SCALE,
                        flow.y * FLOW_SPEED_SCALE,
                        flow.z * FLOW_SPEED_SCALE
                    )
                }
            }
        }

        return ChunkFlowSnapshot(flows)
    }

    @OptIn(GameTickOnly::class)
    private fun ensureRegistered(shipWorld: ServerShipWorld) {
        if (registered) return
        synchronized(this) {
            if (!registered) {
                shipWorld.aerodynamicUtils.registerWindProvider(this)
                registered = true
            }
        }
    }

    private fun clearDimension(dimensionId: DimensionId) {
        val next = HashMap(snapshotsByDimension)
        next.remove(dimensionId)
        snapshotsByDimension = next
        refreshQueue.removeAll { request ->
            if (request.dimensionId == dimensionId) {
                queuedRefreshes.remove(ChunkRefreshKey(request.dimensionId, request.chunkKey))
                true
            } else {
                false
            }
        }
        synchronized(this) {
            dirtyChunks.removeIf { it.dimensionId == dimensionId }
        }
    }

    private fun merge(previous: IntRange?, next: IntRange): IntRange =
        if (previous == null) {
            next
        } else {
            minOf(previous.first, next.first)..maxOf(previous.last, next.last)
        }

    private data class ChunkScanRequest(
        val dimensionId: DimensionId,
        val chunkKey: Long,
        val minY: Int,
        val maxY: Int
    )

    private data class ChunkRefreshKey(val dimensionId: DimensionId, val chunkKey: Long)

    private data class DimensionFlowSnapshot(val chunkFlows: Map<Long, ChunkFlowSnapshot>)

    private data class ChunkFlowSnapshot(val flows: Long2ObjectOpenHashMap<Vector3d>)
}
