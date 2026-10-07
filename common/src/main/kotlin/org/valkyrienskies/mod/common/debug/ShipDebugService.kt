package org.valkyrienskies.mod.common.debug

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.joml.Vector3d
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.internal.ships.VsiServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.internal.physics.VsiDebugVector
import org.valkyrienskies.core.internal.physics.VsiPhysicsDebugSnapshot
import org.valkyrienskies.core.internal.world.VsiPlayer
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.slf4j.LoggerFactory
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.networking.DebugShipInfo
import org.valkyrienskies.mod.common.networking.PacketPhysicsDebug
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore
import java.util.UUID
import java.util.concurrent.Executors

object ShipDebugService {
    const val RANGE = 128.0
    const val MAX_SHIPS = 64
    private val users = HashMap<UUID, DebugRendererMode>()
    private data class ShipAge(val ticks: Long, val known: Boolean, val parent: Long?)
    private data class Subscription(
        val player: VsiPlayer,
        val dimension: String,
        val ships: Map<Long, ShipAge>,
        val limited: Boolean,
        val mode: DebugRendererMode,
    )
    private data class Sample(val world: VsiServerShipWorld, val physics: VsiPhysicsDebugSnapshot)
    private val sendLock = Any()
    private val logger = LoggerFactory.getLogger(ShipDebugService::class.java)
    private val sender by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "VS debug packets").apply { isDaemon = true } }
    }
    private val samples = LatestValueTask<Sample>({ sender.execute(it) }, ::sendSample)
    @Volatile private var subscriptions: List<Subscription> = emptyList()
    @Volatile private var debugWorld: VsiServerShipWorld? = null

    fun setMode(player: ServerPlayer, value: DebugRendererMode? = null): DebugRendererMode {
        val mode = value ?: if (player.uuid in users) DebugRendererMode.NONE else DebugRendererMode.FULL
        if (mode == DebugRendererMode.NONE) {
            users.remove(player.uuid)
            disable(player)
        } else users[player.uuid] = mode
        tick(player.server)
        return mode
    }

    /** The time uses the overworld clock so dimension changes do not reset age. */
    fun markCreated(server: MinecraftServer, ship: ServerShip, parent: ServerShip?) {
        (ship as VsiServerShip).setAttachmentBeforeLoad(ShipDebugMetadata(server.overworld().gameTime, true, parent?.id))
    }

    @JvmStatic
    fun clear() {
        users.clear()
        stopCollection()
    }

    private fun stopCollection() {
        synchronized(sendLock) {
            subscriptions = emptyList()
            debugWorld?.physicsDebugSnapshotListener = null
            debugWorld?.physicsDebugShipIds = emptySet()
            debugWorld = null
            samples.clear()
        }
    }

    private fun disable(player: ServerPlayer) {
        synchronized(sendLock) {
            subscriptions = subscriptions.filter { it.player.uuid != player.uuid }
            send(player, PacketPhysicsDebug(DebugRendererMode.NONE))
        }
    }

    @JvmStatic
    fun tick(server: MinecraftServer) {
        val world = server.shipObjectWorld
        if (users.isEmpty()) {
            stopCollection()
            return
        }
        val players = server.playerList.players
        players.filter { it.uuid in users && !it.hasPermissions(2) }.forEach {
            disable(it)
        }
        users.keys.retainAll(players.filter { it.hasPermissions(2) }.map { it.uuid }.toSet())
        val requested = HashSet<Long>()
        val nextSubscriptions = ArrayList<Subscription>()
        val gameTime = server.overworld().gameTime
        for (player in players) {
            val mode = users[player.uuid] ?: continue
            val dimension = player.level().dimensionId
            val position = Vector3d(player.x, player.y, player.z)
            val nearby = world.loadedShips.filter { ship ->
                if (ship.chunkClaimDimension != dimension) return@filter false
                val box = ship.worldAABB
                val validBox = box.minX() <= box.maxX() && box.minY() <= box.maxY() && box.minZ() <= box.maxZ()
                val closest = if (validBox) Vector3d(
                    position.x.coerceIn(box.minX(), box.maxX()),
                    position.y.coerceIn(box.minY(), box.maxY()),
                    position.z.coerceIn(box.minZ(), box.maxZ()),
                ) else Vector3d(ship.transform.position)
                closest.distanceSquared(position) <= RANGE * RANGE
            }.sortedBy { it.transform.position.distanceSquared(position) }
            val selected = nearby.take(MAX_SHIPS).associateBy { it.id }
            requested.addAll(selected.keys)
            val ages = selected.mapValues { (_, ship) ->
                val metadata = ship.getAttachment<ShipDebugMetadata>() ?: ShipDebugMetadata(
                    gameTime,
                ).also { ship.setAttachment(it) }
                ShipAge((gameTime - metadata.createdGameTime).coerceAtLeast(0),
                    metadata.creationTimeKnown, metadata.parentShipId)
            }
            nextSubscriptions.add(Subscription(player.playerWrapper, dimension, ages, nearby.size > MAX_SHIPS, mode))
        }
        synchronized(sendLock) {
            if (debugWorld !== world) {
                debugWorld?.physicsDebugSnapshotListener = null
                debugWorld?.physicsDebugShipIds = emptySet()
                samples.clear()
                debugWorld = world
            }
            subscriptions = nextSubscriptions.toList()
            world.physicsDebugSnapshotListener = { samples.offer(Sample(world, it)) }
            world.physicsDebugShipIds = requested.toSet()
            subscriptions.filter { it.ships.isEmpty() }.forEach {
                vsCore.simplePacketNetworking.sendToClient(
                    PacketPhysicsDebug(it.mode, it.dimension, origins = listOf(VsiDebugVector.ZERO)), it.player,
                )
            }
        }
    }

    private fun sendSample(sample: Sample) {
        if (debugWorld !== sample.world) return
        val snapshot = sample.physics
        for (subscription in subscriptions) {
            if (subscription.ships.isEmpty()) continue
            val selected = subscription.ships
            val ships = snapshot.ships.mapNotNull { physics ->
                if (physics.dimensionId != subscription.dimension) return@mapNotNull null
                val age = selected[physics.id] ?: return@mapNotNull null
                DebugShipInfo(if (subscription.mode.showMarkers) physics else physics.copy(forces = emptyList()),
                    age.ticks, age.known, age.parent)
            }
            val contacts = if (subscription.mode.showMarkers) snapshot.contacts.filter {
                it.dimensionId == subscription.dimension && (it.bodyId0 in selected || it.bodyId1 in selected)
            } else emptyList()
            val joints = if (subscription.mode.showMarkers) snapshot.joints.filter {
                it.dimensionId == subscription.dimension && (it.bodyId0 in selected || it.bodyId1 in selected)
            } else emptyList()
            val packet = PacketPhysicsDebug(subscription.mode, subscription.dimension, ships, contacts, joints,
                listOf(VsiDebugVector.ZERO), subscription.limited || (subscription.mode.showMarkers &&
                    (snapshot.contacts.size >= 256 || snapshot.joints.size >= 256 ||
                        ships.any { it.physics.forceCount > it.physics.forces.size })),
                snapshot.physicsTick)
            synchronized(sendLock) {
                // Send the stop packet after the last sample, including a sample already in use.
                if (debugWorld === sample.world && subscriptions.any {
                    it.player === subscription.player && it.dimension == subscription.dimension &&
                        it.mode == subscription.mode && it.ships.isNotEmpty()
                }) {
                    try {
                        vsCore.simplePacketNetworking.sendToClient(packet, subscription.player)
                    } catch (exception: Exception) {
                        logger.error("Could not send physics debug data.", exception)
                    }
                }
            }
        }
    }

    private fun send(player: ServerPlayer, packet: PacketPhysicsDebug) {
        vsCore.simplePacketNetworking.sendToClient(packet, player.playerWrapper)
    }
}
