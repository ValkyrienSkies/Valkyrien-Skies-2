package org.valkyrienskies.mod.common.fluid

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import java.util.UUID
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.impl.networking.simple.SimplePacket
import org.valkyrienskies.core.impl.networking.simple.SimplePacketNetworking
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.client.ClientBlockStateInfo
import org.valkyrienskies.mod.common.*
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.networking.PacketSyncBlockStateInfo
import org.valkyrienskies.mod.common.networking.VSGamePackets
import org.valkyrienskies.mod.common.util.MinecraftPlayer

class ClientFluidTypesTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val core = mockk<VsiCore>(relaxed = true)
    private val networking = mockk<SimplePacketNetworking>(relaxed = true)
    private val handler = slot<(PacketSyncBlockStateInfo) -> Unit>()
    private val player = mockk<MinecraftPlayer>()
    private val sent = mutableListOf<PacketSyncBlockStateInfo>()
    private val world = mockk<VsiClientShipWorld>(relaxed = true)
    private val level = mockk<ClientLevel>()
    private val ship = mockk<ClientShip>(relaxed = true)
    private val registryField = MassDatapackResolver::class.java.getDeclaredField("mcBlockStateToVs").apply { isAccessible = true }
    private val cacheField = MassDatapackResolver::class.java.getDeclaredField("liquidIdToFlowingFluid").apply { isAccessible = true }
    private val syncField = MassDatapackResolver::class.java.getDeclaredField("cachedFluidTypes").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    private val registry = registryField.get(MassDatapackResolver) as MutableMap<BlockState, VsiBlockState>
    @Suppress("UNCHECKED_CAST")
    private val cache = cacheField.get(MassDatapackResolver) as MutableMap<Int, Any?>
    private val savedRegistry = registry.toMap()
    private val savedCache = cache.toMap()
    private val savedSync = syncField.get(MassDatapackResolver)
    private val mapper = Class.forName("org.valkyrienskies.core.impl.util.serialization.VSJacksonUtil").let {
        it.getMethod("getPacketMapper").invoke(it.getField("INSTANCE").get(null)) as ObjectMapper
    }

    @BeforeEach fun setup() {
        registry.clear(); cache.clear(); syncField.set(MassDatapackResolver, null)
        ClientFluidTypes.clear()
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { vsCore } returns core
        every { core.simplePacketNetworking } returns networking
        every { player.uuid } returns UUID(0, 1)
        every { with(networking) { any<SimplePacket>().sendToClient(player) } } answers {
            sent += firstArg<SimplePacket>() as PacketSyncBlockStateInfo
        }
        every { with(networking) { PacketSyncBlockStateInfo::class.registerClientHandler(capture(handler)) } } returns mockk()
        VSGamePackets.registerHandlers()
        every { level.shipObjectWorld } returns world
        every { (level as Level).shipObjectWorld } returns world
        every { level.shipWorldNullable } returns world
        every { level.dimensionId } returns "test"
        every { level.getBlockState(any()) } returns Blocks.AIR.defaultBlockState()
        every { level.getFluidState(any()) } returns Fluids.EMPTY.defaultFluidState()
        every { level.getShipsIntersecting(any<org.joml.primitives.AABBdc>()) } returns listOf(ship)
        every { world.allShips.getIntersecting(any(), any()) } returns listOf(ship)
        every { world.loadedShips.iterator() } answers { listOf(ship).iterator() }
        every { ship.id } returns 7
        every { ship.worldAABB } returns AABBd(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        every { ship.renderAABB } returns AABBd(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        every { ship.worldToShip } returns Matrix4d()
        every { ship.shipToWorld } returns Matrix4d()
        every { ship.renderTransform.worldToShip } returns Matrix4d()
        every { world.getFluidGameplaySnapshot(7) } returns gameplaySnapshot(topologySnapshot(),
            floodingSnapshot(VsiFluidFloodedVoxel(0, 0, 0, 17, 255, 0)))
    }

    @AfterEach fun cleanup() {
        ShipFluidInteraction.clear(world)
        FloodedFluidClientCache.clear()
        FluidTopologyClientCache.clear()
        ClientFluidTypes.clear()
        ClientBlockStateInfo.disable()
        unmockkAll()
        registry.clear(); registry.putAll(savedRegistry)
        cache.clear(); cache.putAll(savedCache)
        syncField.set(MassDatapackResolver, savedSync)
    }

    private fun register(state: BlockState, id: Int) {
        val vsState = VsiBlockState(null, mockk())
        val type = mockk<VsiBlockType>()
        registry[state] = vsState
        every { core.blockTypes.getType(vsState) } returns type
        every { core.blockTypes.getLiquidStateId(type) } returns id
    }

    private fun receive(packet: PacketSyncBlockStateInfo) {
        handler.captured(mapper.readValue(mapper.writeValueAsBytes(packet), PacketSyncBlockStateInfo::class.java))
    }

    @Test fun `remote client gets body eye camera and block fluid from a serialized mapping without local physics registration`() {
        assertNull(MassDatapackResolver.getFlowingFluid(17))
        assertFalse(ShipFluidInteraction.samplePoint(level, Vec3(0.5, 0.5, 0.5)).flooded())
        receive(PacketSyncBlockStateInfo(emptyMap(), mapOf(17 to "minecraft:water")))
        assertSame(Fluids.WATER, ShipFluidInteraction.samplePoint(level, Vec3(0.5, 0.5, 0.5)).fluid())
        var retainedDepth = 0.0
        ShipFluidInteraction.prepareVolume(level, AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)).forEachRetained { fluid, depth ->
            assertTrue(fluid.type.isSame(Fluids.WATER))
            retainedDepth = depth
        }
        assertEquals(0.8, retainedDepth, 1e-8)
        assertTrue(CameraFluidState.sample(level, world, Vec3(0.5, 0.5, 0.5), Fluids.EMPTY.defaultFluidState(), true).submergedInShip())
        assertTrue(ShipFluidStateQueries.atWorld(level, 0.5, 0.5, 0.5)!!.fluid().type.isSame(Fluids.WATER))
        assertNull(MassDatapackResolver.getFlowingFluid(17), "Client mapping must not populate the server registry")
    }

    @Test fun `both mass info settings send the registered fluid IDs`() {
        register(Blocks.WATER.defaultBlockState(), 17)
        register(Blocks.LAVA.defaultBlockState(), 18)
        MassDatapackResolver.syncBlockStates(player)
        MassDatapackResolver.clearBlockStates(player)
        assertEquals(2, sent.size)
        assertFalse(sent[0].blockState2VS.isEmpty())
        assertTrue(sent[1].blockState2VS.isEmpty())
        for (packet in sent) {
            assertEquals(mapOf(17 to "minecraft:water", 18 to "minecraft:lava"), packet.liquidState2Fluid)
            receive(packet)
            assertSame(Fluids.WATER, ClientFluidTypes.get(17))
            assertSame(Fluids.LAVA, ClientFluidTypes.get(18))
        }
        assertFalse(ClientBlockStateInfo.clientHasMassInfo)
    }

    @Test fun `server changes replace IDs and disconnect removes them without changing local server lookup`() {
        register(Blocks.WATER.defaultBlockState(), 17)
        assertSame(Fluids.WATER, MassDatapackResolver.getFlowingFluid(17))
        receive(PacketSyncBlockStateInfo(emptyMap(), mapOf(17 to "minecraft:water", 18 to "minecraft:lava")))
        receive(PacketSyncBlockStateInfo(emptyMap(), mapOf(17 to "minecraft:lava")))
        assertSame(Fluids.LAVA, ShipFluidInteraction.samplePoint(level, Vec3(0.5, 0.5, 0.5)).fluid())
        assertNull(ClientFluidTypes.get(18))
        assertSame(Fluids.WATER, MassDatapackResolver.getFlowingFluid(17))
        ClientFluidTypes.clear()
        assertNull(MassDatapackResolver.getFlowingFluid(17, true))
        assertSame(Fluids.WATER, MassDatapackResolver.getFlowingFluid(17, false))
    }

    @Test fun `unknown or invalid fluid names do not resolve to water`() {
        receive(PacketSyncBlockStateInfo(emptyMap(), mapOf(17 to "missing:fluid", 18 to "bad name", 19 to "minecraft:empty")))
        for (id in 17..19) assertNull(ClientFluidTypes.get(id))
    }
}
