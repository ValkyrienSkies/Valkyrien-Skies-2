package org.valkyrienskies.mod.common.networking

import io.mockk.*
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.SharedConstants
import net.minecraft.network.protocol.game.VecDeltaCodec
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerPlayerConnection
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sun.misc.Unsafe
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.common.getShipMountedToData
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.common.util.EntityDragger
import org.valkyrienskies.mod.common.util.EntityDraggingInformation
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.mixin.accessors.server.level.ChunkMapAccessor
import org.valkyrienskies.mod.mixin.accessors.server.level.TrackedEntityAccessor
import org.valkyrienskies.mod.mixin.feature.entity_movement_packets.MixinServerEntity
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck

class ServerShipTrackingTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    @AfterEach fun cleanup() = unmockkAll()

    private fun emptyLevel(): ServerLevel {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        return unsafe.allocateInstance(ServerLevel::class.java) as ServerLevel
    }

    @Test fun `stationary attachments keep contact age current and detach with an absolute baseline`() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        mockkStatic(EntityDragger::class)
        val core = mockk<VsiCore>(relaxed = true)
        every { vsCore } returns core
        val level = emptyLevel()
        val world = mockk<VsiServerShipWorld>(relaxed = true)
        every { level.shipObjectWorld } returns world
        val info = EntityDraggingInformation()
        info.applyNetworkShip(2L, 12)
        val entity = mockk<Entity>(relaxed = true, moreInterfaces = arrayOf(IEntityDraggingInformationProvider::class))
        every { entity.id } returns 5
        every { entity.x } returns 100.0
        every { entity.trackingPosition() } returns Vec3(100.0, 0.0, 0.0)
        every { entity.deltaMovement } returns Vec3.ZERO
        every { getShipMountedToData(entity, any()) } returns null
        every { EntityDragger.isDraggable(entity) } returns true
        every { (entity as IEntityDraggingInformationProvider).draggingInformation } returns info
        val ship = mockk<ServerShip>(relaxed = true)
        every { ship.id } returns 2L
        every { ship.worldToShip } returns Matrix4d()
        every { ship.transform.shipToWorldRotation } returns Quaterniond()
        every { world.allShips.getById(2L) } returns ship
        val chunkMap = mockk<ChunkMap>(moreInterfaces = arrayOf(ChunkMapAccessor::class))
        val tracked = mockk<ChunkMap.TrackedEntity>(moreInterfaces = arrayOf(TrackedEntityAccessor::class))
        val player = mockk<ServerPlayer>(relaxed = true, moreInterfaces = arrayOf(PlayerDuck::class))
        val wrapper = mockk<MinecraftPlayer>()
        val connection = mockk<ServerPlayerConnection> { every { getPlayer() } returns player }
        val chunkSource = mockk<ServerChunkCache>()
        ServerChunkCache::class.java.getDeclaredField("chunkMap").apply { isAccessible = true }.set(chunkSource, chunkMap)
        ServerLevel::class.java.getDeclaredField("chunkSource").apply { isAccessible = true }.set(level, chunkSource)
        every { (chunkMap as ChunkMapAccessor).entityMap } returns Int2ObjectOpenHashMap<ChunkMap.TrackedEntity>().apply { put(5, tracked) }
        every { (tracked as TrackedEntityAccessor).seenBy } returns setOf(connection)
        every { (player as PlayerDuck).vs_getPlayer() } returns wrapper
        val sent = mutableListOf<PacketEntityShipMotion>()
        every { core.simplePacketNetworking.sendToClients(capture(sent), wrapper) } just Runs
        val tracker = MixinServerEntity()
        val codec = VecDeltaCodec()
        fun set(name: String, value: Any) {
            MixinServerEntity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(tracker, value)
        }
        set("entity", entity)
        set("level", level)
        set("positionCodec", codec)
        set("updateInterval", 3)
        fun send(tick: Int) {
            set("tickCount", tick)
            MixinServerEntity::class.java.getDeclaredMethod("vs\$sendAttachmentChange", CallbackInfo::class.java)
                .apply { isAccessible = true }.invoke(tracker, CallbackInfo("sendChanges", false))
        }
        send(0)
        send(1)
        info.ticksSinceStoodOnShip = 15
        send(3)
        info.lastShipStoodOn = null
        send(4)
        assertEquals(listOf(2L, 2L, -1L), sent.map { it.shipID })
        assertEquals(15, sent[1].ticksSinceContact)
        assertEquals(100.0, sent.last().worldX)
        assertEquals(Vec3(100.0, 0.0, 0.0), codec.decode(0, 0, 0))
    }

    @Test fun `custom movement reaches distant tracked players and excludes untracked players`() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        val core = mockk<VsiCore>(relaxed = true)
        every { vsCore } returns core
        val level = emptyLevel()
        val entity = mockk<Entity> { every { id } returns 5 }
        val chunkMap = mockk<ChunkMap>(moreInterfaces = arrayOf(ChunkMapAccessor::class))
        val tracked = mockk<ChunkMap.TrackedEntity>(moreInterfaces = arrayOf(TrackedEntityAccessor::class))
        val player = mockk<ServerPlayer>(relaxed = true, moreInterfaces = arrayOf(PlayerDuck::class))
        val wrapper = mockk<MinecraftPlayer>()
        val connection = mockk<ServerPlayerConnection> { every { getPlayer() } returns player }
        val chunkSource = mockk<ServerChunkCache>()
        ServerChunkCache::class.java.getDeclaredField("chunkMap").apply { isAccessible = true }.set(chunkSource, chunkMap)
        ServerLevel::class.java.getDeclaredField("chunkSource").apply { isAccessible = true }.set(level, chunkSource)
        every { (chunkMap as ChunkMapAccessor).entityMap } returns Int2ObjectOpenHashMap<ChunkMap.TrackedEntity>().apply { put(5, tracked) }
        every { (tracked as TrackedEntityAccessor).seenBy } returns setOf(connection)
        every { (player as PlayerDuck).vs_getPlayer() } returns wrapper
        every { player.shouldRender(any<Double>(), any<Double>(), any<Double>()) } returns false
        val tracker = MixinServerEntity()
        for ((name, value) in mapOf("entity" to entity, "level" to level, "positionCodec" to VecDeltaCodec())) {
            MixinServerEntity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(tracker, value)
        }
        val packet = PacketEntityShipMotion(5, 2L, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, false, 0.0, 0.0, 0.0, 0.0, 0)
        MixinServerEntity::class.java.getDeclaredMethod("vs\$broadcast", PacketEntityShipMotion::class.java)
            .apply { isAccessible = true }.invoke(tracker, packet)
        verify(exactly = 1) { core.simplePacketNetworking.sendToClients(packet, wrapper) }
        verify(exactly = 0) { player.shouldRender(any<Double>(), any<Double>(), any<Double>()) }
    }
}
