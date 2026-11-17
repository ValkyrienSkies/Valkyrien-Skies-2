package org.valkyrienskies.mod.common.distant

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.chunk.LevelChunk
import org.apache.logging.log4j.LogManager
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import org.valkyrienskies.mod.common.VS2ChunkAllocator
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.networking.DistantShipTransforms
import org.valkyrienskies.mod.common.networking.PacketDistantShip
import org.valkyrienskies.mod.common.networking.PacketDistantShipTransforms
import org.valkyrienskies.mod.common.networking.PacketRemoveDistantShips
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object DistantShipServer {
    private val logger = LogManager.getLogger("Valkyrien Skies Distant Ships")

    private const val REEVALUATE_INTERVAL_TICKS = 10
    private const val DISTANCE_HYSTERESIS = 64.0
    private const val PRELOAD_HYSTERESIS = 16.0
    private const val SNAPSHOT_IDLE_TICKS = 20L * 60
    private const val MAX_CONCURRENT_READS = 8
    private const val TRANSFORM_EPSILON = 1.0e-4

    private class Snapshot(val revision: Int, val shipAABB: IntArray, val blocks: ByteArray?, var lastUsedTick: Long)

    private class SentShip(val revision: Int) {
        val transform = DoubleArray(DistantShipTransforms.SIZE)
    }

    private class PlayerState {
        var requestedBlocks = 0
        var clientDimension: String? = null
        var sentDimension: String? = null
        val sent = Long2ObjectOpenHashMap<SentShip>()
        val pending = LongLinkedOpenHashSet()
    }

    private val players = HashMap<UUID, PlayerState>()
    private val snapshots = Long2ObjectOpenHashMap<Snapshot>()
    private val revisions = Long2IntOpenHashMap()
    private val changedShips = Long2LongOpenHashMap()
    private val reading = LongOpenHashSet()
    private var tick = 0L
    private var server: MinecraftServer? = null

    fun setRenderDistance(player: ServerPlayer, blocks: Int, dimension: String) {
        val state = players.getOrPut(player.uuid) { PlayerState() }
        state.requestedBlocks = blocks.coerceIn(0, 65536)
        state.clientDimension = dimension
    }

    @JvmStatic
    fun onBlockChanged(level: Level, pos: BlockPos) {
        if (level !is ServerLevel || players.isEmpty()) return
        val chunkX = pos.x shr 4
        val chunkZ = pos.z shr 4
        if (!VS2ChunkAllocator.isChunkInShipyardCompanion(chunkX, chunkZ)) return
        val ship = level.shipObjectWorld.allShips.getByChunkPos(chunkX, chunkZ, level.dimensionId) ?: return
        if (!changedShips.containsKey(ship.id)) changedShips.put(ship.id, tick)
    }

    @JvmStatic
    fun onShipDeleted(shipId: Long) {
        val server = server ?: return
        if (!server.isSameThread) {
            server.execute { onShipDeleted(shipId) }
            return
        }
        snapshots.remove(shipId)
        revisions.remove(shipId)
        changedShips.remove(shipId)
        for ((uuid, state) in players) {
            state.pending.remove(shipId)
            if (state.sent.remove(shipId) != null) {
                server.playerList.getPlayer(uuid)?.let { send(it, PacketRemoveDistantShips(longArrayOf(shipId))) }
            }
        }
    }

    @JvmStatic
    fun tick(server: MinecraftServer) {
        tick++
        this.server = server
        if (players.isEmpty()) {
            if (snapshots.isNotEmpty() && tick % 200 == 0L) evictSnapshots()
            return
        }
        players.keys.removeIf { server.playerList.getPlayer(it) == null }

        val config = VSGameConfig.SERVER.DistantShips
        if (!config.enabled) {
            for ((uuid, state) in players) {
                if (state.sent.isNotEmpty()) {
                    server.playerList.getPlayer(uuid)?.let { send(it, PacketRemoveDistantShips(state.sent.keys.toLongArray())) }
                }
                state.sent.clear()
                state.pending.clear()
            }
            snapshots.clear()
            return
        }

        applyBlockChanges(config.blockUpdateDelayTicks)
        for (player in server.playerList.players) {
            val state = players[player.uuid] ?: continue
            tickPlayer(player, state, config)
        }
        if (tick % 200 == 0L) evictSnapshots()
    }

    private fun tickPlayer(player: ServerPlayer, state: PlayerState, config: VSGameConfig.Server.DISTANT_SHIPS) {
        val level = player.serverLevel()
        val dimension = level.dimensionId
        if (state.sentDimension != dimension) {
            state.sent.clear()
            state.pending.clear()
            state.sentDimension = dimension
        }
        val distance = min(state.requestedBlocks, config.maxRenderDistance).toDouble()
        if (state.clientDimension != dimension || distance <= 0.0) {
            if (state.sent.isNotEmpty()) {
                send(player, PacketRemoveDistantShips(state.sent.keys.toLongArray()))
                state.sent.clear()
            }
            state.pending.clear()
            return
        }

        val phase = player.id.toLong()
        if ((tick + phase) % REEVALUATE_INTERVAL_TICKS == 0L) reevaluate(player, level, state, distance)
        sendPending(player, level, state, config)
        if ((tick + phase) % transformIntervalTicks() == 0L) sendTransforms(player, level, state)
    }

    private fun transformIntervalTicks(): Int = max(1, VSGameConfig.SERVER.DistantShips.transformUpdateIntervalTicks)

    /** Find the ships this player should have as distant ships. */
    private fun reevaluate(player: ServerPlayer, level: ServerLevel, state: PlayerState, distance: Double) {
        val dimension = level.dimensionId
        val known = (player as PlayerKnownShipsDuck).vs_getKnownShips()
        val preload = max(16.0, VSCoreConfig.SERVER.shipLoadDistance * 0.75)
        val x = player.x
        val y = player.y
        val z = player.z
        val reach = distance + DISTANCE_HYSTERESIS
        val query = AABBd(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach)

        val wanted = LongOpenHashSet()
        val added = ArrayList<Pair<Double, Long>>()
        for (ship in level.shipObjectWorld.allShips.getIntersecting(query, dimension)) {
            if (ship.shipAABB == null) continue
            val id = ship.id
            val shipDistance = distanceTo(ship.worldAABB, x, y, z)
            val isSent = state.sent.containsKey(id)
            val inRange = shipDistance <= if (isSent) reach else distance
            val needed = !known.contains(id) || shipDistance >= if (isSent) preload - PRELOAD_HYSTERESIS else preload
            if (inRange && needed) {
                wanted.add(id)
                if (!isSent && !state.pending.contains(id)) added.add(shipDistance to id)
            }
        }

        val removed = LongArrayList()
        val sentIterator = state.sent.keys.iterator()
        while (sentIterator.hasNext()) {
            val id = sentIterator.nextLong()
            if (!wanted.contains(id)) {
                removed.add(id)
                sentIterator.remove()
            }
        }
        if (!removed.isEmpty) send(player, PacketRemoveDistantShips(removed.toLongArray()))

        val pendingIterator = state.pending.iterator()
        while (pendingIterator.hasNext()) {
            if (!wanted.contains(pendingIterator.nextLong())) pendingIterator.remove()
        }
        added.sortBy { it.first }
        for ((_, id) in added) state.pending.add(id)
    }

    private fun sendPending(
        player: ServerPlayer, level: ServerLevel, state: PlayerState, config: VSGameConfig.Server.DISTANT_SHIPS
    ) {
        var budget = config.snapshotsPerPlayerPerTick
        val iterator = state.pending.iterator()
        while (budget > 0 && iterator.hasNext()) {
            val id = iterator.nextLong()
            val ship = level.shipObjectWorld.allShips.getById(id)
            if (ship == null) {
                iterator.remove()
                continue
            }
            val snapshot = snapshotFor(level, ship, config) ?: continue // still reading from disk
            iterator.remove()
            val sentShip = SentShip(snapshot.revision)
            writeTransform(ship.transform, sentShip.transform)
            state.sent.put(id, sentShip)
            val blocks = snapshot.blocks ?: continue
            send(
                player,
                PacketDistantShip(
                    id, level.dimensionId, snapshot.revision, snapshot.shipAABB, sentShip.transform.copyOf(),
                    transformIntervalTicks(), blocks
                )
            )
            budget--
        }
    }

    private fun sendTransforms(player: ServerPlayer, level: ServerLevel, state: PlayerState) {
        if (state.sent.isEmpty()) return
        val ids = LongArrayList()
        val values = DoubleArrayList()
        val scratch = DoubleArray(DistantShipTransforms.SIZE)
        for (entry in state.sent.long2ObjectEntrySet()) {
            val ship = level.shipObjectWorld.allShips.getById(entry.longKey) ?: continue
            writeTransform(ship.transform, scratch)
            val last = entry.value.transform
            var changed = false
            for (i in scratch.indices) {
                if (Math.abs(scratch[i] - last[i]) > TRANSFORM_EPSILON) {
                    changed = true
                    break
                }
            }
            if (!changed) continue
            System.arraycopy(scratch, 0, last, 0, scratch.size)
            ids.add(entry.longKey)
            values.addElements(values.size, scratch)
        }
        if (!ids.isEmpty) {
            send(
                player,
                PacketDistantShipTransforms(
                    level.dimensionId, transformIntervalTicks(), ids.toLongArray(), values.toDoubleArray()
                )
            )
        }
    }

    /** Queue changed ships for every player that has them, once their changes have settled. */
    private fun applyBlockChanges(delayTicks: Int) {
        if (changedShips.isEmpty()) return
        val iterator = changedShips.long2LongEntrySet().iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (tick - entry.longValue < delayTicks) continue
            val id = entry.longKey
            iterator.remove()
            revisions.addTo(id, 1)
            snapshots.remove(id)
            for (state in players.values) {
                if (state.sent.containsKey(id)) state.pending.addAndMoveToFirst(id)
            }
        }
    }

    /**
     * The current snapshot of [ship], or null while its saved chunks are read from disk. Loaded chunks are read
     * directly; the rest are read from the region files without loading them.
     */
    private fun snapshotFor(level: ServerLevel, ship: ServerShip, config: VSGameConfig.Server.DISTANT_SHIPS): Snapshot? {
        val id = ship.id
        val revision = revisions.get(id)
        val cached = snapshots.get(id)
        if (cached != null && cached.revision == revision) {
            cached.lastUsedTick = tick
            return cached
        }
        if (reading.contains(id)) return null

        val chunks = LongArrayList()
        ship.activeChunksSet.forEach { chunkX, chunkZ -> chunks.add(ChunkPos.asLong(chunkX, chunkZ)) }
        val shipAABB = ship.shipAABB
        val minY = max(level.minSection, ((shipAABB?.minY() ?: level.minBuildHeight) shr 4) - 1)
        val maxY = min(level.maxSection - 1, ((shipAABB?.maxY() ?: (level.maxBuildHeight - 1)) shr 4) + 1)
        val sections = ArrayList<DistantShipSnapshots.Section>()
        val missing = LongArrayList()
        for (i in 0 until chunks.size) {
            val key = chunks.getLong(i)
            val chunk = level.chunkSource.getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key))
            if (chunk != null) addSections(level, chunk, minY, maxY, sections) else missing.add(key)
        }
        if (missing.isEmpty) return store(id, ship, revision, sections, config)

        if (reading.size >= MAX_CONCURRENT_READS) return null
        reading.add(id)
        val reads = HashMap<Long, CompletableFuture<Optional<CompoundTag>>>()
        for (i in 0 until missing.size) {
            val key = missing.getLong(i)
            reads[key] = level.chunkSource.chunkMap.read(ChunkPos(key))
        }
        CompletableFuture.allOf(*reads.values.toTypedArray()).whenComplete { _, error ->
            level.server.execute {
                reading.remove(id)
                val latest = level.shipObjectWorld.allShips.getById(id) ?: return@execute
                if (error != null) {
                    logger.warn("Could not read the chunks of ship {} for distant rendering", id, error)
                    snapshots.put(id, Snapshot(revision, aabbOf(latest), null, tick))
                    return@execute
                }
                val biomes = level.registryAccess().registryOrThrow(Registries.BIOME)
                val all = ArrayList<DistantShipSnapshots.Section>()
                for (i in 0 until chunks.size) {
                    val key = chunks.getLong(i)
                    val chunkX = ChunkPos.getX(key)
                    val chunkZ = ChunkPos.getZ(key)
                    val chunk = level.chunkSource.getChunkNow(chunkX, chunkZ)
                    if (chunk != null) {
                        addSections(level, chunk, minY, maxY, all)
                        continue
                    }
                    val tag = reads[key]?.getNow(Optional.empty())?.orElse(null) ?: continue
                    try {
                        DistantShipSnapshots.sectionsFromSavedChunk(tag, chunkX, chunkZ, minY, maxY, biomes, all)
                    } catch (e: Exception) {
                        logger.warn("Could not decode saved chunk [{}, {}] of ship {}", chunkX, chunkZ, id, e)
                    }
                }
                store(id, latest, revision, all, config)
            }
        }
        return null
    }

    /** Add the blocks and light of sections [minY]..[maxY] of a loaded chunk. */
    private fun addSections(
        level: ServerLevel, chunk: LevelChunk, minY: Int, maxY: Int, out: MutableList<DistantShipSnapshots.Section>
    ) {
        val blockLight = level.lightEngine.getLayerListener(LightLayer.BLOCK)
        val skyLight = level.lightEngine.getLayerListener(LightLayer.SKY)
        for (y in minY..maxY) {
            val blocks = chunk.getSection(level.getSectionIndexFromSectionY(y)).takeIf { !it.hasOnlyAir() }
            val pos = SectionPos.of(chunk.pos.x, y, chunk.pos.z)
            val block = blockLight.getDataLayerData(pos)?.copy()?.data
            val sky = skyLight.getDataLayerData(pos)?.copy()?.data
            if (blocks != null || block != null || sky != null) {
                out.add(DistantShipSnapshots.Section(chunk.pos.x, y, chunk.pos.z, blocks, block, sky))
            }
        }
    }

    private fun store(
        id: Long, ship: ServerShip, revision: Int, sections: List<DistantShipSnapshots.Section>,
        config: VSGameConfig.Server.DISTANT_SHIPS
    ): Snapshot {
        var blocks: ByteArray? = null
        if (sections.any { it.blocks != null }) {
            val encoded = DistantShipSnapshots.encode(sections)
            if (encoded.size <= config.maxSnapshotKilobytes.toLong() * 1024) {
                blocks = encoded
            } else {
                logger.debug("Ship {} is too big to send as a distant ship ({} KB)", id, encoded.size / 1024)
            }
        }
        val snapshot = Snapshot(revision, aabbOf(ship), blocks, tick)
        snapshots.put(id, snapshot)
        return snapshot
    }

    private fun evictSnapshots() {
        val iterator = snapshots.long2ObjectEntrySet().iterator()
        while (iterator.hasNext()) {
            if (tick - iterator.next().value.lastUsedTick > SNAPSHOT_IDLE_TICKS) iterator.remove()
        }
        if (players.isEmpty()) {
            revisions.clear()
            changedShips.clear()
        }
    }

    private fun aabbOf(ship: ServerShip): IntArray {
        val aabb = ship.shipAABB ?: return IntArray(6)
        return intArrayOf(aabb.minX(), aabb.minY(), aabb.minZ(), aabb.maxX(), aabb.maxY(), aabb.maxZ())
    }

    private fun writeTransform(transform: ShipTransform, out: DoubleArray) {
        val position = transform.position
        val rotation = transform.rotation
        val scaling = transform.scaling
        val positionInModel = transform.positionInModel
        out[DistantShipTransforms.POSITION] = position.x()
        out[DistantShipTransforms.POSITION + 1] = position.y()
        out[DistantShipTransforms.POSITION + 2] = position.z()
        out[DistantShipTransforms.ROTATION] = rotation.x()
        out[DistantShipTransforms.ROTATION + 1] = rotation.y()
        out[DistantShipTransforms.ROTATION + 2] = rotation.z()
        out[DistantShipTransforms.ROTATION + 3] = rotation.w()
        out[DistantShipTransforms.SCALING] = scaling.x()
        out[DistantShipTransforms.SCALING + 1] = scaling.y()
        out[DistantShipTransforms.SCALING + 2] = scaling.z()
        out[DistantShipTransforms.POSITION_IN_MODEL] = positionInModel.x()
        out[DistantShipTransforms.POSITION_IN_MODEL + 1] = positionInModel.y()
        out[DistantShipTransforms.POSITION_IN_MODEL + 2] = positionInModel.z()
    }

    private fun distanceTo(aabb: AABBdc, x: Double, y: Double, z: Double): Double {
        val dx = max(max(aabb.minX() - x, 0.0), x - aabb.maxX())
        val dy = max(max(aabb.minY() - y, 0.0), y - aabb.maxY())
        val dz = max(max(aabb.minZ() - z, 0.0), z - aabb.maxZ())
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun send(player: ServerPlayer, packet: SimplePacket) {
        vsCore.simplePacketNetworking.sendToClient(packet, player.playerWrapper)
    }
}
